package com.enterprise.rag.service;

import com.enterprise.rag.config.RagProperties;
import com.enterprise.rag.model.Document;
import com.enterprise.rag.model.QueryRequest;
import com.enterprise.rag.model.QueryResponse;
import com.enterprise.rag.rag.document.DocumentProcessor;
import com.enterprise.rag.rag.embedding.EmbeddingService;
import com.enterprise.rag.rag.retrieval.RetrievalService;
import com.enterprise.rag.rag.rerank.RerankService;
import com.enterprise.rag.rag.vectorstore.MilvusVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 核心服务
 */
@Slf4j
@Service
public class RagService {

    @Autowired
    private DocumentProcessor documentProcessor;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private RetrievalService retrievalService;

    @Autowired
    private RerankService rerankService;

    @Autowired
    private MilvusVectorStore milvusVectorStore;

    @Autowired
    private RagProperties ragProperties;

    @Autowired(required = false)
    private ChatClient.Builder chatClientBuilder;

    public UploadResult uploadDocument(MultipartFile file) throws IOException {
        List<Document> documents = documentProcessor.processDocument(file);
        documents = embeddingService.embedDocuments(documents);
        milvusVectorStore.insertDocuments(documents);

        String documentId = documents.isEmpty() ? null : documents.get(0).getDocumentId();
        log.info("[RAG] 文档上传成功, file={}, chunks={}, documentId={}",
            file.getOriginalFilename(), documents.size(), documentId);

        return new UploadResult(file.getOriginalFilename(), documentId, documents.size());
    }

    public QueryResponse query(QueryRequest request) {
        long startTime = System.currentTimeMillis();

        QueryRequest.RetrievalParams retrievalParams = request.getRetrievalParams();
        if (retrievalParams == null) {
            retrievalParams = QueryRequest.RetrievalParams.builder().build();
        }

        int topK = retrievalParams.getTopK() != null
            ? retrievalParams.getTopK()
            : ragProperties.getRetrieval().getTopK();

        long retrievalStart = System.currentTimeMillis();
        var retrievalResult = retrievalService.retrieve(
            request.getQuestion(),
            topK,
            retrievalParams.getStrategy()
        );
        long retrievalTime = System.currentTimeMillis() - retrievalStart;

        List<Document> documents = retrievalResult.getDocuments();
        List<Double> scores = retrievalResult.getScores();

        long rerankStart = System.currentTimeMillis();
        if (Boolean.TRUE.equals(retrievalParams.getEnableRerank()) && !documents.isEmpty()) {
            documents = rerankService.rerank(documents, scores, "SCORE", topK);
        }
        long rerankTime = System.currentTimeMillis() - rerankStart;

        String context = buildContext(documents);

        long generationStart = System.currentTimeMillis();
        String answer = generateAnswer(request.getQuestion(), context, request.getGenerationParams());
        long generationTime = System.currentTimeMillis() - generationStart;

        long totalTime = System.currentTimeMillis() - startTime;

        List<QueryResponse.SourceReference> sources = documents.stream()
            .map(doc -> QueryResponse.SourceReference.builder()
                .documentId(doc.getDocumentId())
                .title(doc.getTitle())
                .snippet(doc.getContent().length() > 200
                    ? doc.getContent().substring(0, 200) + "..."
                    : doc.getContent())
                .score(doc.getMetadata() != null && doc.getMetadata().get("score") instanceof Number
                    ? ((Number) doc.getMetadata().get("score")).doubleValue()
                    : null)
                .build())
            .collect(Collectors.toList());

        double confidence = sources.isEmpty() ? 0.0
            : sources.stream()
                .mapToDouble(s -> s.getScore() != null ? s.getScore() : 0.0)
                .average()
                .orElse(0.0);

        return QueryResponse.builder()
            .answer(answer)
            .sources(sources)
            .confidence(confidence)
            .totalTime(totalTime)
            .stageTiming(QueryResponse.StageTiming.builder()
                .retrievalTime(retrievalTime)
                .rerankTime(rerankTime)
                .generationTime(generationTime)
                .build())
            .build();
    }

    public List<String> listDocuments() {
        return milvusVectorStore.listDocumentTitles();
    }

    public void deleteDocument(String documentId) {
        milvusVectorStore.deleteByDocumentId(documentId);
    }

    private String buildContext(List<Document> documents) {
        if (documents.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            context.append("[文档").append(i + 1).append(": ").append(doc.getTitle()).append("]\n");
            context.append(doc.getContent()).append("\n\n");
        }
        return context.toString();
    }

    private String generateAnswer(String question, String context, QueryRequest.GenerationParams params) {
        if (context.isBlank()) {
            return "未在知识库中找到相关内容。请先上传相关文档，或尝试换一种问法。";
        }

        if (chatClientBuilder == null) {
            return "ChatClient 未配置，请设置环境变量 OPENAI_API_KEY。\n\n检索到的文档片段：\n" + context;
        }

        String systemPrompt = """
            你是企业知识库智能助手。请根据检索到的文档片段回答用户问题。
            要求：
            1. 仅基于提供的上下文回答，不要编造信息
            2. 若上下文不足以回答，请明确说明
            3. 回答简洁专业，使用中文
            """;

        return chatClientBuilder.build()
            .prompt()
            .system(systemPrompt)
            .user(u -> u.text("问题：{question}\n\n参考文档：\n{context}")
                .param("question", question)
                .param("context", context))
            .call()
            .content();
    }

    public record UploadResult(String filename, String documentId, int chunkCount) {}
}
