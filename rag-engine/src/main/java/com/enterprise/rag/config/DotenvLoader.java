package com.enterprise.rag.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 加载项目根目录 .env，供 IDE 调试与 mvn spring-boot:run 使用。
 * 已存在于 OS 环境变量中的非空键不会被覆盖。
 */
public final class DotenvLoader {

    private static final String PLACEHOLDER_API_KEY = "your-api-key-here";

    private DotenvLoader() {
    }

    public static void load() {
        Map<String, Object> envMap = loadEnvMap();
        if (envMap.isEmpty()) {
            System.err.println("[Config] 未找到 .env 文件（已查找 user.dir 与 ../.env）");
            return;
        }
        envMap.putIfAbsent("spring.ai.openai.embedding.embeddings-path", "/embeddings");
        DotenvEnvironmentPostProcessor.normalizeDashScopeEmbedding(envMap);
        envMap.forEach((key, value) -> applyIfAbsent(key, String.valueOf(value)));
    }

    public static Map<String, Object> loadEnvMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        findEnvFile().ifPresent(path -> readEnvFile(path, result));
        return result;
    }

    public static boolean hasOpenAiApiKey() {
        String key = resolve("OPENAI_API_KEY");
        return key != null && !key.isBlank() && !PLACEHOLDER_API_KEY.equals(key);
    }

    public static void warnEmbeddingMisconfiguration() {
        String embedBase = resolve("OPENAI_EMBEDDING_BASE_URL");
        if (embedBase == null) {
            embedBase = "https://api.openai.com/v1";
        }
        String embedModel = resolve("OPENAI_EMBEDDING_MODEL");
        if (embedModel == null) {
            embedModel = "text-embedding-3-small";
        }
        if (embedBase.contains("deepseek") || embedModel.contains("deepseek-embedding")) {
            System.err.println("[Config] 警告：DeepSeek 不提供 /v1/embeddings，上传文档将返回 404");
            System.err.println("[Config] 请在 .env 中配置独立的 OPENAI_EMBEDDING_BASE_URL 与 OPENAI_EMBEDDING_API_KEY");
        }
        if (embedBase.contains("dashscope") && !embedBase.endsWith("/v1") && !embedBase.endsWith("/v1/")) {
            System.err.println("[Config] 提示：DashScope embedding 将在启动时自动补 /v1（path=/embeddings 时必需，见 INC-010）");
        }
    }

    public static String resolve(String name) {
        String value = System.getenv(name);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = System.getProperty(name);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return null;
    }

    public static Optional<Path> findEnvFile() {
        Path cwd = Paths.get(System.getProperty("user.dir"));
        List<Path> candidates = List.of(
            cwd.resolve(".env"),
            cwd.resolve("..").resolve(".env").normalize(),
            cwd.resolve("rag-engine").resolve("..").resolve(".env").normalize()
        );
        return candidates.stream()
            .filter(Files::isRegularFile)
            .findFirst();
    }

    private static void readEnvFile(Path envFile, Map<String, Object> target) {
        System.out.println("[Config] 加载环境变量文件: " + envFile.toAbsolutePath());
        try (Stream<String> lines = Files.lines(envFile)) {
            lines.map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .forEach(line -> applyLine(line, target));
        } catch (IOException e) {
            System.err.println("[Config] 读取 .env 失败: " + e.getMessage());
        }
    }

    private static void applyIfAbsent(String key, String value) {
        String env = System.getenv(key);
        String prop = System.getProperty(key);
        if ((env == null || env.isBlank()) && (prop == null || prop.isBlank())) {
            System.setProperty(key, value);
            return;
        }
        // 允许 .env 规范化结果覆盖先前写入的非 /v1 DashScope base-url
        if ("OPENAI_EMBEDDING_BASE_URL".equals(key) && value.contains("dashscope") && value.endsWith("/v1")
            && prop != null && prop.contains("dashscope") && !prop.endsWith("/v1")) {
            System.setProperty(key, value);
            return;
        }
        if (env != null && !env.isBlank() && !env.equals(value)) {
            System.err.println("[Config] 警告: OS 环境变量 " + key + " 已存在，.env 中的值被忽略");
        }
    }

    private static void applyLine(String line, Map<String, Object> target) {
        int eq = line.indexOf('=');
        if (eq <= 0) {
            return;
        }
        String key = line.substring(0, eq).trim();
        String value = unquote(line.substring(eq + 1).trim());
        target.put(key, value);
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
