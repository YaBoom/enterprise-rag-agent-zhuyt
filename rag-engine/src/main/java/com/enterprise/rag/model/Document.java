package com.enterprise.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 文档实体
 * 
 * @author jack.zhu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    private String id;
    /** 同一上传文件的所有切片共享此 ID */
    private String documentId;
    private String content;
    private String title;
    private String source;
    private String type;
    private Integer chunkIndex;
    private float[] embedding;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}