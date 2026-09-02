package com.enterprise.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 查询请求
 * 
 * @author jack.zhu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryRequest {

    private String question;
    private String tenantId;
    private String userId;
    private String sessionId;
    /** 会话 ID：同一会话共享 Spring AI ChatMemory 记忆，用于多轮对话；为空时回退 sessionId，再为空则后端生成新会话 */
    private String conversationId;
    private RetrievalParams retrievalParams;
    private GenerationParams generationParams;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetrievalParams {
        private Integer topK = 5;
        /** 相似度阈值：null 时使用全局配置 rag.retrieval.score-threshold（勿设默认值，否则会覆盖全局配置） */
        private Double scoreThreshold;
        /** 检索策略：当前实现向量检索（VECTOR）；混合检索（HYBRID）为规划项，尚未落地 */
        private String strategy = "VECTOR";
        private Boolean enableRerank = true;
        private List<String> filters;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerationParams {
        /** 生成模型：为空时使用服务端 spring.ai.openai.chat.options.model 配置 */
        private String model;
        /** 采样温度：RAG 生成需强约束于检索内容，默认低温以降低幻觉 */
        private Double temperature = 0.2;
        private Integer maxTokens = 2000;
        private Boolean stream = false;
    }
}