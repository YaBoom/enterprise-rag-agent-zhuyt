package com.enterprise.rag.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * 在 Spring 解析 application.yml 之前注入 .env，解决 IDE 调试时占位符未生效的问题。
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String SOURCE_NAME = "dotenvFile";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> props = new HashMap<>(DotenvLoader.loadEnvMap());
        if (props.isEmpty()) {
            return;
        }

        bridge(props, "OPENAI_API_KEY", "spring.ai.openai.api-key");
        bridge(props, "OPENAI_BASE_URL", "spring.ai.openai.base-url");
        bridge(props, "OPENAI_CHAT_MODEL", "spring.ai.openai.chat.options.model");
        bridge(props, "OPENAI_EMBEDDING_API_KEY", "spring.ai.openai.embedding.api-key");
        bridge(props, "OPENAI_EMBEDDING_BASE_URL", "spring.ai.openai.embedding.base-url");
        bridge(props, "OPENAI_EMBEDDING_MODEL", "spring.ai.openai.embedding.options.model");
        bridge(props, "RAG_EMBEDDING_DIMENSION", "spring.ai.openai.embedding.options.dimensions");

        props.putIfAbsent("spring.ai.openai.embedding.embeddings-path", "/embeddings");
        normalizeDashScopeEmbedding(props);

        environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, props));
        System.out.println("[Config] EnvironmentPostProcessor 已注入 .env，keys=" + props.size());
    }

    private static void bridge(Map<String, Object> props, String envKey, String springKey) {
        Object value = props.get(envKey);
        if (value != null && !String.valueOf(value).isBlank()) {
            props.put(springKey, value);
        }
    }

    /**
     * DashScope 兼容模式：Spring AI 不会自动补 /v1，path=/embeddings 时 base 必须含 /v1。
     * 见 INC-010。
     */
    static void normalizeDashScopeEmbedding(Map<String, Object> props) {
        String embedBase = firstNonBlank(
            props.get("spring.ai.openai.embedding.base-url"),
            props.get("OPENAI_EMBEDDING_BASE_URL")
        );
        if (embedBase == null || !embedBase.contains("dashscope")) {
            return;
        }

        String path = String.valueOf(props.getOrDefault(
            "spring.ai.openai.embedding.embeddings-path", "/embeddings"));
        String normalized = trimTrailingSlash(embedBase);

        if ("/embeddings".equals(path) && !normalized.endsWith("/v1")) {
            normalized = normalized + "/v1";
            System.out.println("[Config] DashScope embedding base-url 自动补 /v1: " + normalized);
        } else if ("/v1/embeddings".equals(path) && normalized.endsWith("/v1")) {
            props.put("spring.ai.openai.embedding.embeddings-path", "/embeddings");
            System.out.println("[Config] DashScope base-url 已含 /v1，embedding path 改为 /embeddings");
        }

        props.put("OPENAI_EMBEDDING_BASE_URL", normalized);
        props.put("spring.ai.openai.embedding.base-url", normalized);
    }

    private static String firstNonBlank(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
