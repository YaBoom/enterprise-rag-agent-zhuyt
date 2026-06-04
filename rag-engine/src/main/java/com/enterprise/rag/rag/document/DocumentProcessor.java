package com.enterprise.rag.rag.document;

import com.enterprise.rag.model.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * 文档解析服务
 * 
 * @author jack.zhu
 */
@Service
public class DocumentProcessor {

    private static final int CHUNK_SIZE = 1000;
    private static final int CHUNK_OVERLAP = 200;

    /**
     * 解析并切片文档
     */
    public List<Document> processDocument(MultipartFile file) throws IOException {
        String content = extractContent(file);
        
        dev.langchain4j.data.document.Document lcDoc = 
            dev.langchain4j.data.document.Document.from(content);
        
        DocumentSplitter splitter = DocumentSplitters.recursive(CHUNK_SIZE, CHUNK_OVERLAP);
        List<TextSegment> segments = splitter.split(lcDoc);
        
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            
            Document doc = Document.builder()
                .id(UUID.randomUUID().toString())
                .content(segment.text())
                .title(file.getOriginalFilename())
                .source(file.getOriginalFilename())
                .type(getFileType(file.getOriginalFilename()))
                .chunkIndex(i)
                .metadata(new HashMap<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalTime.now())
                .build();
            
            documents.add(doc);
        }
        
        return documents;
    }

    private String extractContent(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        String fileType = getFileType(filename);
        
        switch (fileType.toLowerCase()) {
            case "txt":
            case "md":
            case "markdown":
                return new String(file.getBytes(), StandardCharsets.UTF_8);
            default:
                return new String(file.getBytes(), StandardCharsets.UTF_8);
        }
    }

    private String getFileType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "unknown";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}