package com.enterprise.rag.rag.retrieval;

import com.enterprise.rag.model.Document;
import com.enterprise.rag.model.RetrievalResult;
import com.enterprise.rag.rag.embedding.EmbeddingService;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 检索服务
 * 
 * @author jack.zhu
 */
@Service
public class RetrievalService {

    @Autowired(required = false)
    private MilvusClientV2 milvusClient;

    @Autowired
    private EmbeddingService embeddingService;

    @Value("${rag.collection.name:enterprise_knowledge}")
    private String collectionName;

    /**
     * 执行检索
     */
    public RetrievalResult retrieve(String query, Integer topK, String strategy) {
        long startTime = System.currentTimeMillis();

        List<Document> documents = vectorSearch(query, topK);
        List<Double> scores = documents.stream()
            .map(doc -> embeddingService.cosineSimilarity(
                embeddingService.embedQuery(query),
                doc.getEmbedding()
            ))
            .collect(Collectors.toList());

        long retrievalTime = System.currentTimeMillis() - startTime;

        return RetrievalResult.builder()
            .documents(documents)
            .scores(scores)
            .retrievalTime(retrievalTime)
            .retrievalStrategy(strategy)
            .build();
    }

    /**
     * 向量检索
     */
    private List<Document> vectorSearch(String query, Integer topK) {
        if (milvusClient == null) {
            throw new IllegalStateException("MilvusClient 未配置");
        }

        float[] queryEmbedding = embeddingService.embedQuery(query);

        SearchReq searchReq = SearchReq.builder()
            .collectionName(collectionName)
            .data(Collections.singletonList(new FloatVec(queryEmbedding)))
            .topK(topK)
            .outputFields(Arrays.asList("id", "content", "title", "source", "type"))
            .build();

        SearchResp searchResp = milvusClient.search(searchReq);

        List<Document> documents = new ArrayList<>();
        List<SearchResp.SearchResult> results = searchResp.getResult();
        
        for (SearchResp.SearchResult result : results) {
            Map<String, Object> fields = result.getEntity();
            
            Document doc = Document.builder()
                .id((String) fields.get("id"))
                .content((String) fields.get("content"))
                .title((String) fields.get("title"))
                .source((String) fields.get("source"))
                .type((String) fields.get("type"))
                .embedding(result.getEmbedding())
                .build();
            
            documents.add(doc);
        }

        return documents;
    }
}