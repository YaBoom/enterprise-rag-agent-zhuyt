package com.enterprise.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 核心配置
 * 
 * @author jack.zhu
 */
@Configuration
public class SpringAiConfig {

    @Autowired(required = false)
    private EmbeddingModel embeddingModel;

    @Autowired(required = false)
    private VectorStore vectorStore;

    @Bean
    public ChatClient.Builder chatClientBuilder() {
        return ChatClient.builder();
    }

    @Bean
    public EmbeddingModel embeddingModelBean() {
        return embeddingModel;
    }

    @Bean
    public VectorStore vectorStoreBean() {
        return vectorStore;
    }
}