package com.enterprise.rag;

import com.enterprise.rag.config.DotenvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.HashMap;
import java.util.Map;

/**
 * Enterprise RAG Engine - 企业级智能问答系统
 *
 * 单一技术栈：Java 21 + SpringAI + LangChain4j
 */
@SpringBootApplication
public class RagEngineApplication {

    private static final String OPENAI_AUTO_CONFIG = String.join(",",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration",
        "org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration",
        "org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreAutoConfiguration"
    );

    public static void main(String[] args) {
        DotenvLoader.load();
        DotenvLoader.warnEmbeddingMisconfiguration();

        SpringApplication app = new SpringApplication(RagEngineApplication.class);
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("spring.ai.openai.embedding.embeddings-path", "/embeddings");
        applyDashScopeDefaults(defaults);

        if (!DotenvLoader.hasOpenAiApiKey()) {
            System.err.println("[Config] OPENAI_API_KEY 未配置，跳过 OpenAI 自动配置（嵌入/问答不可用，健康检查仍可用）");
            System.err.println("[Config] 请复制 .env.example 为 .env 并填入有效 API Key");
            defaults.put("spring.autoconfigure.exclude", OPENAI_AUTO_CONFIG);
        } else {
            logResolvedAiConfig();
        }

        app.setDefaultProperties(defaults);
        app.run(args);
        System.out.println("\n✅ Enterprise RAG Engine 启动成功！");
        System.out.println("📚 单一Java技术栈，精准定位，持续发力！");
        System.out.println("🎯 目标：成为市场上最稀缺的Java+AI复合型人才\n");
    }

    private static void logResolvedAiConfig() {
        System.out.println("[Config] chat baseUrl=" + DotenvLoader.resolve("OPENAI_BASE_URL")
            + ", model=" + DotenvLoader.resolve("OPENAI_CHAT_MODEL"));
        System.out.println("[Config] embedding baseUrl=" + DotenvLoader.resolve("OPENAI_EMBEDDING_BASE_URL")
            + ", model=" + DotenvLoader.resolve("OPENAI_EMBEDDING_MODEL")
            + ", embeddings-path=/embeddings");
    }

    private static void applyDashScopeDefaults(Map<String, Object> defaults) {
        String embedBase = DotenvLoader.resolve("OPENAI_EMBEDDING_BASE_URL");
        if (embedBase != null && embedBase.contains("dashscope")) {
            defaults.put("spring.ai.openai.embedding.base-url", embedBase);
        }
    }
}
