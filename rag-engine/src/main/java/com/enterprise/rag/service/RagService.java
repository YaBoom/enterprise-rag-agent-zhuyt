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
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
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

    /** 会话记忆存储（Spring AI MessageWindowChatMemory，见 SpringAiConfig） */
    @Autowired(required = false)
    private ChatMemory chatMemory;

    /** Spring AI MessageChatMemoryAdvisor 的会话 ID 参数键（BaseChatMemoryAdvisor 内部硬编码） */
    private static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";

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
        String conversationId = resolveConversationId(request);
        log.info("[RAG] 问答开始, conversationId={}, question={}", conversationId, request.getQuestion());

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
            retrievalParams.getStrategy(),
            retrievalParams.getScoreThreshold()
        );
        long retrievalTime = System.currentTimeMillis() - retrievalStart;

        List<Document> documents = retrievalResult.getDocuments();
        List<Double> scores = retrievalResult.getScores();

        long rerankStart = System.currentTimeMillis();
        if (Boolean.TRUE.equals(retrievalParams.getEnableRerank()) && !documents.isEmpty()) {
            documents = rerankService.rerank(request.getQuestion(), documents, scores, "SCORE", topK);
        }
        long rerankTime = System.currentTimeMillis() - rerankStart;

        String context = buildContext(documents);

        long generationStart = System.currentTimeMillis();
        String answer = generateAnswer(request.getQuestion(), context, request.getGenerationParams(), conversationId);
        long generationTime = System.currentTimeMillis() - generationStart;

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("[RAG] 问答完成, conversationId={}, retrieval={}ms, rerank={}ms, generation={}ms, total={}ms, sources={}",
            conversationId, retrievalTime, rerankTime, generationTime, totalTime, documents.size());

        List<QueryResponse.SourceReference> sources = documents.stream()
            .map(doc -> QueryResponse.SourceReference.builder()
                .documentId(doc.getDocumentId())
                .title(doc.getTitle())
                .snippet(doc.getContent().length() > 200
                    ? doc.getContent().substring(0, 200) + "..."
                    : doc.getContent())
                .content(doc.getContent())
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
            .conversationId(conversationId)
            .stageTiming(QueryResponse.StageTiming.builder()
                .retrievalTime(retrievalTime)
                .rerankTime(rerankTime)
                .generationTime(generationTime)
                .build())
            .build();
    }

    /**
     * 解析会话 ID：优先 conversationId，回退 sessionId，再为空则生成新会话 UUID
     */
    private String resolveConversationId(QueryRequest request) {
        if (StringUtils.hasText(request.getConversationId())) {
            return request.getConversationId();
        }
        if (StringUtils.hasText(request.getSessionId())) {
            return request.getSessionId();
        }
        return UUID.randomUUID().toString();
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

    private String generateAnswer(String question, String context,
                                  QueryRequest.GenerationParams params, String conversationId) {
        if (context.isBlank()) {
            return "未在知识库中找到相关内容。请先上传相关文档，或尝试换一种问法。";
        }

        if (chatClientBuilder == null) {
            return "ChatClient 未配置，请设置环境变量 OPENAI_API_KEY。\n\n检索到的文档片段：\n" + context;
        }

        String systemPrompt = """
            你是企业知识库智能助手，支持多轮对话。
            回答规则：
            1. 只依据下方参考文档作答，不得使用文档之外的知识，也不要编造或推测未提及的信息
            2. 参考文档不足以回答时，直接说明"知识库中未找到相关内容"，不要强行作答
            3. 可结合对话历史理解追问（如代词指代、省略式提问），但答案依据始终以参考文档为准
            4. 回答简洁、准确、专业，使用中文
            """;

        // 挂载会话记忆 Advisor：按 conversationId 自动写入本轮问答并回传历史消息
        ChatClient.Builder builder = chatClientBuilder;
        if (chatMemory != null) {
            builder = builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build());
        }

        return builder.build()
            .prompt()
            .system(systemPrompt)
            .user(u -> u.text("问题：{question}\n\n参考文档：\n{context}")
                .param("question", question)
                .param("context", context))
            .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId))
            .call()
            .content();
    }

    public record UploadResult(String filename, String documentId, int chunkCount) {}
}
