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