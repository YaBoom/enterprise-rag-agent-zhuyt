package com.enterprise.rag.controller;

import com.enterprise.rag.model.QueryRequest;
import com.enterprise.rag.model.QueryResponse;
import com.enterprise.rag.service.RagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * RAG API 控制器
 * 
 * @author jack.zhu
 */
@RestController
@RequestMapping("/api/v1/rag")
public class RagController {

    @Autowired
    private RagService ragService;

    /**
     * 上传文档
     */
    @PostMapping("/documents")
    public String uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            ragService.uploadDocument(file);
            return "✅ 文档上传成功：" + file.getOriginalFilename();
        } catch (Exception e) {
            return "❌ 文档上传失败：" + e.getMessage();
        }
    }

    /**
     * 查询问答
     */
    @PostMapping("/query")
    public QueryResponse query(@RequestBody QueryRequest request) {
        return ragService.query(request);
    }

    /**
     * 流式问答
     */
    @PostMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public String queryStream(@RequestBody QueryRequest request) {
        return "流式问答待实现";
    }

    /**
     * 获取文档列表
     */
    @GetMapping("/documents")
    public List<String> getDocuments(@RequestParam(required = false) String tenantId) {
        return List.of("文档列表待实现");
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/documents/{documentId}")
    public String deleteDocument(@PathVariable String documentId) {
        return "文档删除待实现：" + documentId;
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public String health() {
        return "✅ RAG Engine 运行正常";
    }
}