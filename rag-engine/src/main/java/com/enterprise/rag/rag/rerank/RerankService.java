package com.enterprise.rag.rag.rerank;

import com.enterprise.rag.model.Document;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 重排序服务
 * 
 * @author jack.zhu
 */
@Service
public class RerankService {

    /**
     * 执行重排序
     */
    public List<Document> rerank(List<Document> documents, List<Double> scores, String strategy, Integer topN) {
        if (documents.isEmpty()) {
            return documents;
        }

        List<DocumentScorePair> pairs = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            pairs.add(new DocumentScorePair(documents.get(i), scores.get(i)));
        }

        switch (strategy.toUpperCase()) {
            case "SCORE":
                pairs.sort((a, b) -> Double.compare(b.score, a.score));
                break;
            case "DIVERSITY":
                pairs = diversityRerank(pairs);
                break;
            default:
                pairs.sort((a, b) -> Double.compare(b.score, a.score));
        }

        for (DocumentScorePair pair : pairs) {
            if (pair.document.getMetadata() == null) {
                pair.document.setMetadata(new HashMap<>());
            }
            pair.document.getMetadata().put("score", pair.score);
        }

        return pairs.stream()
            .limit(topN)
            .map(pair -> pair.document)
            .collect(Collectors.toList());
    }

    /**
     * 多样性重排序
     */
    private List<DocumentScorePair> diversityRerank(List<DocumentScorePair> pairs) {
        List<DocumentScorePair> selected = new ArrayList<>();
        Set<String> selectedContent = new HashSet<>();

        pairs.sort((a, b) -> Double.compare(b.score, a.score));

        for (DocumentScorePair pair : pairs) {
            String contentHash = hashContent(pair.document.getContent());
            
            if (!selectedContent.contains(contentHash)) {
                selected.add(pair);
                selectedContent.add(contentHash);
            }

            if (selected.size() >= pairs.size()) {
                break;
            }
        }

        return selected;
    }

    private String hashContent(String content) {
        if (content == null || content.length() < 100) {
            return content;
        }
        return content.substring(0, 100);
    }

    private static class DocumentScorePair {
        Document document;
        double score;

        DocumentScorePair(Document document, double score) {
            this.document = document;
            this.score = score;
        }
    }
}