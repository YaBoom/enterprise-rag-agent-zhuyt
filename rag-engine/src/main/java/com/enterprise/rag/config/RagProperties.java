package com.enterprise.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 业务配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private Collection collection = new Collection();
    private Document document = new Document();
    private Retrieval retrieval = new Retrieval();
    private Embedding embedding = new Embedding();

    @Data
    public static class Collection {
        private String name = "enterprise_knowledge";
    }

    @Data
    public static class Document {
        private int chunkSize = 1000;
        private int chunkOverlap = 200;
    }

    @Data
    public static class Retrieval {
        private int topK = 5;
        private double scoreThreshold = 0.5;
    }

    @Data
    public static class Embedding {
        private int dimension = 1536;
    }
}
