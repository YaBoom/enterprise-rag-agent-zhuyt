package com.enterprise.rag.service;

import com.enterprise.rag.model.Document;
import com.enterprise.rag.model.QueryRequest;
import com.enterprise.rag.model.QueryResponse;
import com.enterprise.rag.rag.document.DocumentProcessor;
import com.enterprise.rag.rag.embedding.EmbeddingService;
import com.enterprise.rag.rag.retrieval.RetrievalService;
import com.enterprise.rag.rag.rerank.RerankService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 核心服务
 * 
 * @author jack.zhu
 */
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

    @Autowired(required = false)
    private ChatClient.Builder chatClientBuilder;

    /**
     * 上传文档
     */
    public void uploadDocument(MultipartFile file) throws IOException {
        List<Document> documents = documentProcessor.processDocument(file);
        documents = embeddingService.embedDocuments(documents);
        
        System.out.println("✅ 文档切片数量：" + documents.size());
        for (Document doc : documents) {
            System.out.println("  - 切片 " + doc.getChunkIndex() + ": " + 
                (doc.getContent().length() > 50 ? doc.getContent().substring(0, 50) + "..." : doc.getContent()));
        }
    }

    /**
     * 执行问答
     */
    public QueryResponse query(QueryRequest request) {
        long startTime = System.currentTimeMillis();

        // 1. 检索
        long retrievalStart = System.currentTimeMillis();
        com.enterprise.rag.model.RetrievalResult retrievalResult = 
            retrievalService.retrieve(
                request.getQuestion(),
                request.getRetrievalParams().getTopK(),
                request.getRetrievalParams().getStrategy()
            );
        long retrievalTime = System.currentTimeMillis() - retrievalStart;

        // 2. 重排序
        long rerankStart = System.currentTimeMillis();
        List<Document> documents = rerankService.rerank(
            retrievalResult.getDocuments(),
            retrievalResult.getScores(),
            "SCORE",
            request.getRetrievalParams().getTopK()
        );
        long rerankTime = System.currentTimeMillis() - rerankStart;

        // 3. 构建上下文
        String context = buildContext(documents);

        // 4. 生成答案
        long generationStart = System.currentTimeMillis();
        String answer = generateAnswer(request.getQuestion(), context);
        long generationTime = System.currentTimeMillis() - generationStart;

        // 5. 构建响应
        long totalTime = System.currentTimeMillis() - startTime;

        List<QueryResponse.SourceReference> sources = documents.stream()
            .map(doc -> QueryResponse.SourceReference.builder()
                .documentId(doc.getId())
                .title(doc.getTitle())
                .snippet(doc.getContent().length() > 200 ? 
                    doc.getContent().substring(0, 200) + "..." : doc.getContent())
                .build())
            .collect(Collectors.toList());

        return QueryResponse.builder()
            .answer(answer)
            .sources(sources)
            .confidence(0.85)
            .totalTime(totalTime)
            .stageTiming(QueryResponse.StageTiming.builder()
                .retrievalTime(retrievalTime)
                .rerankTime(rerankTime)
                .generationTime(generationTime)
                .build())
            .build();
    }

    private String buildContext(List<Document> documents) {
        StringBuilder context = new StringBuilder();
        
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            context.append("[文档").append(i + 1).append("]\n");
            context.append(doc.getContent()).append("\n\n");
        }

        return context.toString();
    }

    private String generateAnswer(String question, String context) {
        if (chatClientBuilder == null) {
            return "⚠️ ChatClient 未配置，请检查 spring.ai.openai.api-key\n\n" +
                "检索到的文档内容：\n" + context;
        }

        return "答案生成待实现。检索到 " + context.split("\n\n").length + " 个文档片段。";
    }
}