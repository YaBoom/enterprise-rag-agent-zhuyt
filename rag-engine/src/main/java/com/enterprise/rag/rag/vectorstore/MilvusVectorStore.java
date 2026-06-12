package com.enterprise.rag.rag.vectorstore;

import com.enterprise.rag.config.RagProperties;
import com.enterprise.rag.model.Document;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.QueryResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Milvus 向量存储操作
 */
@Slf4j
@Service
public class MilvusVectorStore {

    private static final Gson GSON = new Gson();

    @Autowired(required = false)
    private MilvusClientV2 milvusClient;

    @Autowired
    private RagProperties ragProperties;

    public void insertDocuments(List<Document> documents) {
        requireClient();

        List<JsonObject> rows = new ArrayList<>();
        for (Document doc : documents) {
            JsonObject row = new JsonObject();
            row.addProperty("id", doc.getId());
            row.addProperty("content", doc.getContent());
            row.addProperty("title", nullSafe(doc.getTitle()));
            row.addProperty("source", nullSafe(doc.getSource()));
            row.addProperty("type", nullSafe(doc.getType()));
            row.addProperty("document_id", nullSafe(doc.getDocumentId()));
            row.addProperty("chunk_index", doc.getChunkIndex() != null ? doc.getChunkIndex() : 0);
            row.addProperty("metadata", doc.getMetadata() != null ? GSON.toJson(doc.getMetadata()) : "{}");

            JsonArray embeddingArray = new JsonArray();
            for (float value : doc.getEmbedding()) {
                embeddingArray.add(value);
            }
            row.add("embedding", embeddingArray);
            rows.add(row);
        }

        InsertReq insertReq = InsertReq.builder()
            .collectionName(collectionName())
            .data(rows)
            .build();

        InsertResp resp = milvusClient.insert(insertReq);
        log.info("[Milvus] 入库完成, insertCount={}", resp.getInsertCnt());
    }

    public void deleteByDocumentId(String documentId) {
        requireClient();

        DeleteReq deleteReq = DeleteReq.builder()
            .collectionName(collectionName())
            .filter("document_id == \"" + documentId + "\"")
            .build();

        DeleteResp resp = milvusClient.delete(deleteReq);
        log.info("[Milvus] 删除文档, documentId={}, deleteCount={}", documentId, resp.getDeleteCnt());
    }

    public List<String> listDocumentTitles() {
        requireClient();

        QueryReq queryReq = QueryReq.builder()
            .collectionName(collectionName())
            .filter("chunk_index == 0")
            .outputFields(List.of("document_id", "title"))
            .limit(100)
            .build();

        QueryResp queryResp = milvusClient.query(queryReq);
        Map<String, String> uniqueDocs = new LinkedHashMap<>();

        for (QueryResp.QueryResult result : queryResp.getQueryResults()) {
            Map<String, Object> entity = result.getEntity();
            String docId = String.valueOf(entity.get("document_id"));
            String title = String.valueOf(entity.get("title"));
            uniqueDocs.putIfAbsent(docId, title);
        }

        return new ArrayList<>(uniqueDocs.values());
    }

    private String collectionName() {
        return ragProperties.getCollection().getName();
    }

    private void requireClient() {
        if (milvusClient == null) {
            throw new IllegalStateException("MilvusClient 未配置，请启动 Milvus 并检查 spring.ai.milvus 配置");
        }
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
