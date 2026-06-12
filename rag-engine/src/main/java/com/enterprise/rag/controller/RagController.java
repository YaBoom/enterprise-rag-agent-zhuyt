package com.enterprise.rag.controller;

import com.enterprise.rag.config.DotenvLoader;
import com.enterprise.rag.model.ApiResult;
import com.enterprise.rag.model.QueryRequest;
import com.enterprise.rag.model.QueryResponse;
import com.enterprise.rag.service.RagService;
import org.springframework.beans.factory.annotation.Autowired;
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
            return ApiResult.fail("查询失败：" + e.getMessage());
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

    private String formatUploadError(Exception e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        if (msg != null && msg.contains("HTTP 404")) {
            return "Embedding API 返回 404：请检查 OPENAI_EMBEDDING_BASE_URL、"
                + "OPENAI_EMBEDDING_MODEL 是否正确；DashScope 需配置 embeddings-path=/embeddings（见 INC-007）";
        }
        return e.getMessage() != null ? e.getMessage() : root.getClass().getSimpleName();
    }

    @GetMapping("/health")
    public ApiResult<Map<String, String>> health() {
        String aiStatus = DotenvLoader.hasOpenAiApiKey() ? "CONFIGURED" : "MISSING_API_KEY";
        return ApiResult.ok(Map.of(
            "status", "UP",
            "service", "enterprise-rag-engine",
            "openai", aiStatus
        ));
    }
}
