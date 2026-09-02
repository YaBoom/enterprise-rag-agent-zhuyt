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
    private Chat chat = new Chat();

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
        /** 与 application.yml 保持一致：text-embedding-v4 相似度偏低，0.5 会误滤有效召回 */
        private double scoreThreshold = 0.3;
    }

    @Data
    public static class Embedding {
        private int dimension = 1536;
        /** DashScope text-embedding-v4 单次最多 10 条 */
        private int batchSize = 10;
    }

    @Data
    public static class Chat {
        /** Spring AI 会话记忆窗口大小（消息条数），超出后丢弃最旧消息 */
        private int maxMessages = 20;
    }
}
