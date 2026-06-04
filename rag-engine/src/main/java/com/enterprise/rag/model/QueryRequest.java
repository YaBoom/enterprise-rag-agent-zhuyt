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
    private RetrievalParams retrievalParams;
    private GenerationParams generationParams;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetrievalParams {
        private Integer topK = 5;
        private Double scoreThreshold = 0.7;
        private String strategy = "HYBRID";
        private Boolean enableRerank = true;
        private List<String> filters;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerationParams {
        private String model = "gpt-4-turbo";
        private Double temperature = 0.7;
        private Integer maxTokens = 2000;
        private Boolean stream = false;
    }
}