package com.enterprise.rag.rag.embedding;

import com.enterprise.rag.model.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量嵌入服务
 * 
 * @author jack.zhu
 */
@Service
public class EmbeddingService {

    @Autowired(required = false)
    private EmbeddingModel embeddingModel;

    /**
     * 为文档生成向量嵌入
     */
    public Document embedDocument(Document document) {
        if (embeddingModel == null) {
            throw new IllegalStateException("EmbeddingModel 未配置");
        }

        float[] embedding = embeddingModel.embed(document.getContent());
        document.setEmbedding(embedding);

        return document;
    }

    /**
     * 批量生成向量嵌入
     */
    public List<Document> embedDocuments(List<Document> documents) {
        if (embeddingModel == null) {
            throw new IllegalStateException("EmbeddingModel 未配置");
        }

        List<String> contents = new ArrayList<>();
        for (Document doc : documents) {
            contents.add(doc.getContent());
        }

        List<float[]> embeddings = embeddingModel.embed(contents);

        for (int i = 0; i < documents.size(); i++) {
            documents.get(i).setEmbedding(embeddings.get(i));
        }

        return documents;
    }

    /**
     * 为查询文本生成向量嵌入
     */
    public float[] embedQuery(String query) {
        if (embeddingModel == null) {
            throw new IllegalStateException("EmbeddingModel 未配置");
        }

        return embeddingModel.embed(query);
    }

    /**
     * 计算余弦相似度
     */
    public double cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length) {
            throw new IllegalArgumentException("向量维度不匹配");
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}