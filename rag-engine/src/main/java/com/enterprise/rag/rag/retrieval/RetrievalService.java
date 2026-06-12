package com.enterprise.rag.rag.retrieval;

import com.enterprise.rag.config.RagProperties;
import com.enterprise.rag.model.Document;
import com.enterprise.rag.model.RetrievalResult;
import com.enterprise.rag.rag.embedding.EmbeddingService;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
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

    @Autowired(required = false)
    private MilvusClientV2 milvusClient;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private RagProperties ragProperties;

    public RetrievalResult retrieve(String query, Integer topK, String strategy) {
        long startTime = System.currentTimeMillis();
        int effectiveTopK = topK != null ? topK : ragProperties.getRetrieval().getTopK();
        double scoreThreshold = ragProperties.getRetrieval().getScoreThreshold();

        List<Document> documents = vectorSearch(query, effectiveTopK);
        List<Double> scores = new ArrayList<>();

        List<Document> filtered = new ArrayList<>();
        for (Document doc : documents) {
            double score = doc.getMetadata() != null && doc.getMetadata().get("score") instanceof Number
                ? ((Number) doc.getMetadata().get("score")).doubleValue()
                : 0.0;
            if (score >= scoreThreshold) {
                filtered.add(doc);
                scores.add(score);
            }
        }

        long retrievalTime = System.currentTimeMillis() - startTime;
        log.debug("[Retrieval] strategy={}, hits={}, filtered={}", strategy, documents.size(), filtered.size());

        return RetrievalResult.builder()
            .documents(filtered)
            .scores(scores)
            .retrievalTime(retrievalTime)
            .retrievalStrategy(strategy != null ? strategy : "VECTOR")
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
            .outputFields(Arrays.asList("id", "content", "title", "source", "type", "document_id", "chunk_index"))
            .build();

        SearchResp searchResp = milvusClient.search(searchReq);
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
