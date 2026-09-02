package com.enterprise.rag.rag.retrieval;

import com.enterprise.rag.config.RagProperties;
import com.enterprise.rag.model.Document;
import com.enterprise.rag.model.RetrievalResult;
import com.enterprise.rag.rag.embedding.Bm25SparseVectorizer;
import com.enterprise.rag.rag.embedding.EmbeddingService;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.data.SparseFloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 检索服务
 */
@Slf4j
@Service
public class RetrievalService {

    /** 混合检索：各通道召回倍数，融合后截断到 topK */
    private static final int HYBRID_RECALL_MULTIPLIER = 2;

    /** RRF（Reciprocal Rank Fusion）融合常数 k */
    private static final double RRF_K = 60.0;

    @Autowired(required = false)
    private MilvusClientV2 milvusClient;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private Bm25SparseVectorizer bm25SparseVectorizer;

    @Autowired
    private RagProperties ragProperties;

    public RetrievalResult retrieve(String query, Integer topK, String strategy, Double scoreThreshold) {
        long startTime = System.currentTimeMillis();
        int effectiveTopK = topK != null ? topK : ragProperties.getRetrieval().getTopK();
        String effectiveStrategy = strategy != null ? strategy.toUpperCase() : "HYBRID";

        List<Document> documents;
        List<Double> scores;
        if ("HYBRID".equals(effectiveStrategy)) {
            // 混合检索：RRF 融合分与余弦相似度不可直接比较，跳过阈值过滤，避免关键词命中被向量阈值误杀
            RetrievalResult hybrid = hybridSearch(query, effectiveTopK);
            documents = hybrid.getDocuments();
            scores = hybrid.getScores();
        } else {
            // 请求级阈值优先（评估实验控制变量），未提供时使用全局配置
            double effectiveThreshold = scoreThreshold != null
                ? scoreThreshold
                : ragProperties.getRetrieval().getScoreThreshold();

            documents = vectorSearch(query, effectiveTopK);
            scores = new ArrayList<>();
            List<Document> filtered = new ArrayList<>();
            for (Document doc : documents) {
                double score = scoreOf(doc);
                if (score >= effectiveThreshold) {
                    filtered.add(doc);
                    scores.add(score);
                }
            }
            documents = filtered;
        }

        long retrievalTime = System.currentTimeMillis() - startTime;
        log.debug("[Retrieval] strategy={}, hits={}, filtered={}", effectiveStrategy, documents.size(), scores.size());

        return RetrievalResult.builder()
            .documents(documents)
            .scores(scores)
            .retrievalTime(retrievalTime)
            .retrievalStrategy(effectiveStrategy)
            .build();
    }

    /**
     * 混合检索：稠密向量通道 + BM25 稀疏向量通道各自召回，RRF 融合后取 topK。
     * 展示与置信度沿用稠密通道余弦相似度（RRF 分仅用于排序）。
     */
    private RetrievalResult hybridSearch(String query, int topK) {
        int recall = Math.max(topK, topK * HYBRID_RECALL_MULTIPLIER);
        List<Document> denseHits = vectorSearch(query, recall);
        List<Document> sparseHits = sparseSearch(query, recall);

        Map<String, Double> rrfScores = new HashMap<>();
        for (int i = 0; i < denseHits.size(); i++) {
            rrfScores.merge(denseHits.get(i).getId(), 1.0 / (RRF_K + i + 1), Double::sum);
        }
        for (int i = 0; i < sparseHits.size(); i++) {
            rrfScores.merge(sparseHits.get(i).getId(), 1.0 / (RRF_K + i + 1), Double::sum);
        }

        Map<String, Document> denseById = new HashMap<>();
        for (Document doc : denseHits) {
            denseById.put(doc.getId(), doc);
        }
        Map<String, Document> sparseById = new HashMap<>();
        for (Document doc : sparseHits) {
            sparseById.put(doc.getId(), doc);
        }

        List<Document> documents = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        rrfScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .forEach(e -> {
                Document doc = denseById.getOrDefault(e.getKey(), sparseById.get(e.getKey()));
                documents.add(doc);
                scores.add(scoreOf(doc));
            });

        return RetrievalResult.builder()
            .documents(documents)
            .scores(scores)
            .retrievalStrategy("HYBRID")
            .build();
    }

    private List<Document> vectorSearch(String query, Integer topK) {
        if (milvusClient == null) {
            throw new IllegalStateException("MilvusClient 未配置");
        }

        float[] queryEmbedding = embeddingService.embedQuery(query);
        String collectionName = ragProperties.getCollection().getName();

        SearchReq searchReq = SearchReq.builder()
            .collectionName(collectionName)
            .data(Collections.singletonList(new FloatVec(queryEmbedding)))
            .topK(topK)
            .annsField("embedding")
            .outputFields(Arrays.asList("id", "content", "title", "source", "type", "document_id", "chunk_index"))
            .build();

        return parseSearchResults(milvusClient.search(searchReq));
    }

    /**
     * BM25 稀疏向量检索（关键词通道），查询侧使用与入库一致的向量化逻辑
     */
    private List<Document> sparseSearch(String query, int topK) {
        SortedMap<Long, Float> sparseQuery = bm25SparseVectorizer.vectorize(query);
        if (sparseQuery.isEmpty()) {
            return Collections.emptyList();
        }

        SearchReq searchReq = SearchReq.builder()
            .collectionName(ragProperties.getCollection().getName())
            .data(Collections.singletonList(new SparseFloatVec(sparseQuery)))
            .topK(topK)
            .annsField("sparse")
            .outputFields(Arrays.asList("id", "content", "title", "source", "type", "document_id", "chunk_index"))
            .build();

        return parseSearchResults(milvusClient.search(searchReq));
    }

    private List<Document> parseSearchResults(SearchResp searchResp) {
        List<Document> documents = new ArrayList<>();

        List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();
        List<SearchResp.SearchResult> results = searchResults.isEmpty()
            ? Collections.emptyList()
            : searchResults.get(0);

        for (SearchResp.SearchResult result : results) {
            Map<String, Object> fields = result.getEntity();
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("score", result.getScore());

            Document doc = Document.builder()
                .id(stringValue(fields.get("id")))
                .documentId(stringValue(fields.get("document_id")))
                .content(stringValue(fields.get("content")))
                .title(stringValue(fields.get("title")))
                .source(stringValue(fields.get("source")))
                .type(stringValue(fields.get("type")))
                .chunkIndex(intValue(fields.get("chunk_index")))
                .metadata(metadata)
                .build();
            documents.add(doc);
        }

        return documents;
    }

    private double scoreOf(Document doc) {
        return doc.getMetadata() != null && doc.getMetadata().get("score") instanceof Number
            ? ((Number) doc.getMetadata().get("score")).doubleValue()
            : 0.0;
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
