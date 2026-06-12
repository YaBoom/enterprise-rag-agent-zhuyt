package com.enterprise.rag.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 启动前加载项目根目录 .env，供 IDE 调试与 mvn spring-boot:run 使用。
 * 已存在于 OS 环境变量中的键不会被覆盖。
 */
public final class DotenvLoader {

    private static final String PLACEHOLDER_API_KEY = "your-api-key-here";

    private DotenvLoader() {
    }

    public static void load() {
        findEnvFile().ifPresent(DotenvLoader::applyFile);
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

    private static Optional<Path> findEnvFile() {
        Path cwd = Paths.get(System.getProperty("user.dir"));
        List<Path> candidates = List.of(
            cwd.resolve(".env"),
            cwd.resolve("..").resolve(".env").normalize()
        );
        return candidates.stream()
            .filter(Files::isRegularFile)
            .findFirst();
    }

    private static void applyFile(Path envFile) {
        System.out.println("[Config] 加载环境变量文件: " + envFile.toAbsolutePath());
        try (Stream<String> lines = Files.lines(envFile)) {
            lines.map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .forEach(DotenvLoader::applyLine);
        } catch (IOException e) {
            System.err.println("[Config] 读取 .env 失败: " + e.getMessage());
        }
    }

    private static void applyLine(String line) {
        int eq = line.indexOf('=');
        if (eq <= 0) {
            return;
        }
        String key = line.substring(0, eq).trim();
        String value = unquote(line.substring(eq + 1).trim());
        if (System.getenv(key) == null && System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
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
