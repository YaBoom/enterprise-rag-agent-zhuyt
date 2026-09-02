package com.enterprise.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 查询响应
 * 
 * @author jack.zhu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResponse {

    private String answer;
    private List<SourceReference> sources;
    private Double confidence;
    private Long totalTime;
    private StageTiming stageTiming;
    private String sessionId;
    /** 本次问答实际使用的会话 ID（请求未携带时由后端生成），客户端应保存并在后续请求中回传 */
    private String conversationId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceReference {
        private String documentId;
        private String title;
        private String snippet;
        private Double score;
        private String sourceUrl;
        /** 完整 chunk 内容，供 RAG 评估（RAGAS）使用，避免 snippet 截断导致指标失真 */
        private String content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageTiming {
        private Long intentRecognitionTime;
        private Long retrievalTime;
        private Long rerankTime;
        private Long generationTime;
    }
}