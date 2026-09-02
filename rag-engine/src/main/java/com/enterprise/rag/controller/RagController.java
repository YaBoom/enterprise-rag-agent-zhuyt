package com.enterprise.rag.controller;

import com.enterprise.rag.config.AiConfigLogger;
import com.enterprise.rag.config.DotenvLoader;
import com.enterprise.rag.model.ApiResult;
import com.enterprise.rag.model.QueryRequest;
import com.enterprise.rag.model.QueryResponse;
import com.enterprise.rag.service.RagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * RAG API 控制器
 */
@RestController
@RequestMapping("/api/v1/rag")
public class RagController {

    @Autowired
    private RagService ragService;

    @Value("${spring.ai.openai.embedding.base-url:}")
    private String embeddingBaseUrl;

    @Value("${spring.ai.openai.embedding.embeddings-path:}")
    private String embeddingsPath;

    @PostMapping("/documents")
    public ApiResult<RagService.UploadResult> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            RagService.UploadResult result = ragService.uploadDocument(file);
            return ApiResult.ok("文档上传成功", result);
        } catch (Exception e) {
            return ApiResult.fail("文档上传失败：" + formatUploadError(e));
        }
    }

    @PostMapping("/query")
    public ApiResult<QueryResponse> query(@RequestBody QueryRequest request) {
        try {
            return ApiResult.ok(ragService.query(request));
        } catch (Exception e) {
            return ApiResult.fail("查询失败：" + formatQueryError(e));
        }
    }

    @PostMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ApiResult<String> queryStream(@RequestBody QueryRequest request) {
        return ApiResult.fail("流式问答待实现");
    }

    @GetMapping("/documents")
    public ApiResult<List<String>> getDocuments(@RequestParam(required = false) String tenantId) {
        try {
            return ApiResult.ok(ragService.listDocuments());
        } catch (Exception e) {
            return ApiResult.fail("获取文档列表失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/documents/{documentId}")
    public ApiResult<Map<String, String>> deleteDocument(@PathVariable String documentId) {
        try {
            ragService.deleteDocument(documentId);
            return ApiResult.ok("文档删除成功", Map.of("documentId", documentId));
        } catch (Exception e) {
            return ApiResult.fail("文档删除失败：" + e.getMessage());
        }
    }

    private String formatQueryError(Exception e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String msg = root.getMessage() != null ? root.getMessage() : e.getMessage();
        if (msg != null && (msg.contains("timeout") || msg.contains("Timeout"))) {
            return "Chat API 请求超时：推理模型（如 qwen3.7-plus）响应较慢，"
                + "请确认已重启后端加载 AiHttpClientConfig（读超时 3 分钟），"
                + "或改用 qwen-plus / qwen-turbo（见 INC-013）";
        }
        return msg != null ? msg : root.getClass().getSimpleName();
    }

    private String formatUploadError(Exception e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        if (msg != null && msg.contains("HTTP 404")) {
            return "Embedding API 返回 404：DashScope 需 base-url 含 /v1 且 path=/embeddings，"
                + "实际 URL 见启动日志或 GET /health 的 embeddingUrl（见 INC-010）";
        }
        if (msg != null && msg.contains("batch size is invalid")) {
            return "Embedding 批量超限：DashScope text-embedding-v4 单次最多 10 条，"
                + "请确认 rag.embedding.batch-size≤10 并已重启后端（见 INC-009）";
        }
        return e.getMessage() != null ? e.getMessage() : root.getClass().getSimpleName();
    }

    @GetMapping("/health")
    public ApiResult<Map<String, String>> health() {
        String aiStatus = DotenvLoader.hasOpenAiApiKey() ? "CONFIGURED" : "MISSING_API_KEY";
        String embeddingUrl = AiConfigLogger.resolveEmbeddingUrl(embeddingBaseUrl, embeddingsPath);
        return ApiResult.ok(Map.of(
            "status", "UP",
            "service", "enterprise-rag-engine",
            "openai", aiStatus,
            "embeddingPath", embeddingsPath.isBlank() ? "(default /v1/embeddings)" : embeddingsPath,
            "embeddingUrl", embeddingUrl,
            "envFile", DotenvLoader.findEnvFile().map(p -> p.toAbsolutePath().toString()).orElse("NOT_FOUND")
        ));
    }
}
