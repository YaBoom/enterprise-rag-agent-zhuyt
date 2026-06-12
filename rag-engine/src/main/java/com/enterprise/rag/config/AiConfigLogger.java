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

        String resolvedUrl = resolveEmbeddingUrl(embeddingBaseUrl, embeddingsPath);
        log.info("[AI Config] embedding 实际请求 URL={}", resolvedUrl);

        if (resolvedUrl.contains("dashscope") && resolvedUrl.contains("/compatible-mode/embeddings")) {
            log.error("[AI Config] DashScope URL 缺少 /v1，上传必 404！base-url 应为 .../compatible-mode/v1（见 INC-010）");
        }
        if (resolvedUrl.contains("/v1/v1/")) {
            log.error("[AI Config] 检测到双 /v1 路径，上传必 404！（见 INC-010）");
        }
        if (DotenvLoader.findEnvFile().isEmpty()) {
            log.warn("[AI Config] 未找到 .env，IDE 调试请使用 .vscode/launch.json 或确认 cwd 为项目根目录");
        }
    }

    /** Spring AI RestClient 实际请求 = baseUrl + embeddingsPath（不会自动补 /v1） */
    public static String resolveEmbeddingUrl(String baseUrl, String path) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "(empty)";
        }
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String suffix = (path == null || path.isBlank()) ? "/v1/embeddings" : path;
        if (!suffix.startsWith("/")) {
            suffix = "/" + suffix;
        }
        return normalized + suffix;
    }
}
