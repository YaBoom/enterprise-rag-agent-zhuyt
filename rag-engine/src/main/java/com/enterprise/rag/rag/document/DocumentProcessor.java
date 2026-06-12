package com.enterprise.rag.rag.document;

import com.enterprise.rag.config.RagProperties;
import com.enterprise.rag.model.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
 */
@Slf4j
@Service
public class DocumentProcessor {

    @Autowired
    private RagProperties ragProperties;

    public List<Document> processDocument(MultipartFile file) throws IOException {
        String content = extractContent(file);
        String documentId = UUID.randomUUID().toString();
        String filename = file.getOriginalFilename();

        var lcDoc = dev.langchain4j.data.document.Document.from(content);
        int chunkSize = ragProperties.getDocument().getChunkSize();
        int chunkOverlap = ragProperties.getDocument().getChunkOverlap();
        var splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
        List<TextSegment> segments = splitter.split(lcDoc);

        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            Document doc = Document.builder()
                .id(UUID.randomUUID().toString())
                .documentId(documentId)
                .content(segments.get(i).text())
                .title(filename)
                .source(filename)
                .type(getFileType(filename))
                .chunkIndex(i)
                .metadata(new HashMap<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
            documents.add(doc);
        }

        log.info("[Document] 解析完成, file={}, chunks={}", filename, documents.size());
        return documents;
    }

    private String extractContent(MultipartFile file) throws IOException {
        String fileType = getFileType(file.getOriginalFilename());

        return switch (fileType) {
            case "pdf" -> parseWith(new ApachePdfBoxDocumentParser(), file);
            case "doc", "docx", "xls", "xlsx", "ppt", "pptx" -> parseWith(new ApachePoiDocumentParser(), file);
            case "txt", "md", "markdown" -> new String(file.getBytes(), StandardCharsets.UTF_8);
            default -> new String(file.getBytes(), StandardCharsets.UTF_8);
        };
    }

    private String parseWith(DocumentParser parser, MultipartFile file) throws IOException {
        try (var inputStream = file.getInputStream()) {
            return parser.parse(inputStream).text();
        }
    }

    private String getFileType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "unknown";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
