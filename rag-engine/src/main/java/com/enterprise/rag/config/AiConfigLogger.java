package com.enterprise.rag.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时打印 AI 配置，便于排查 embedding 404 等问题。
 */
@Slf4j
@Component
public class AiConfigLogger implements CommandLineRunner {

    @Value("${spring.ai.openai.base-url:}")
    private String chatBaseUrl;

    @Value("${spring.ai.openai.chat.options.model:}")
    private String chatModel;

    @Value("${spring.ai.openai.embedding.base-url:}")
    private String embeddingBaseUrl;

    @Value("${spring.ai.openai.embedding.options.model:}")
    private String embeddingModel;

    @Value("${spring.ai.openai.embedding.options.dimensions:0}")
    private int embeddingDimensions;

    @Value("${spring.ai.openai.embedding.embeddings-path:}")
    private String embeddingsPath;

    @Override
    public void run(String... args) {
        log.info("[AI Config] chat baseUrl={}, model={}", chatBaseUrl, chatModel);
        log.info("[AI Config] embedding baseUrl={}, path={}, model={}, dimensions={}",
            embeddingBaseUrl, embeddingsPath, embeddingModel, embeddingDimensions);
    }
}
