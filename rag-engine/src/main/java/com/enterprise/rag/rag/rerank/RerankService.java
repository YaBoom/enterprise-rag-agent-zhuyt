package com.enterprise.rag.rag.rerank;

import com.enterprise.rag.model.Document;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 重排序服务：在向量召回结果之上做轻量重排。
 * <p>
 * 默认策略 SCORE 将向量相似度与查询词覆盖率融合排序，让字面命中查询的片段适度靠前；
 * 向量相似度仍是主导项，词面信号只做小幅修正，避免过度重排。
 * 用于展示与置信度的相似度分数保留原始向量值，融合分只影响排序。
 *
 * @author jack.zhu
 */
@Service
public class RerankService {

    /** 词面覆盖率在融合分中的权重，向量相似度占主导 */
    private static final double LEXICAL_WEIGHT = 0.15;

    /** 归一化时剔除的标点：ASCII 标点由 \p{Punct} 覆盖，其余为常见中文全角标点 */
    private static final String PUNCTUATION_CLASS = "[\\s\\p{Punct}，。！？、；：（）《》【】…—·]";

    /**
     * 执行重排序
     *
     * @param query     用户查询，用于计算词面覆盖率；为空时退化为纯向量分排序
     * @param documents 召回文档
     * @param scores    与 documents 一一对应的向量相似度
     * @param strategy  SCORE（相关性融合，默认）或 DIVERSITY（按向量分去重）
     * @param topN      截断数量
     */
    public List<Document> rerank(String query, List<Document> documents, List<Double> scores,
                                 String strategy, Integer topN) {
        if (documents.isEmpty()) {
            return documents;
        }

        List<DocumentScorePair> pairs = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            double vectorScore = (scores != null && i < scores.size() && scores.get(i) != null)
                ? scores.get(i)
                : 0.0;
            pairs.add(new DocumentScorePair(documents.get(i), vectorScore));
        }

        String mode = strategy != null ? strategy.toUpperCase() : "SCORE";
        if ("DIVERSITY".equals(mode)) {
            pairs = diversityRerank(pairs);
        } else {
            pairs = relevanceRerank(query, pairs);
        }

        // 保留原始向量相似度到 metadata，供前端展示与置信度计算
        for (DocumentScorePair pair : pairs) {
            if (pair.document.getMetadata() == null) {
                pair.document.setMetadata(new HashMap<>());
            }
            pair.document.getMetadata().put("score", pair.vectorScore);
        }

        int limit = (topN != null && topN > 0) ? topN : pairs.size();
        return pairs.stream()
            .limit(limit)
            .map(pair -> pair.document)
            .collect(Collectors.toList());
    }

    /**
     * 相关性融合重排：融合分 = 向量相似度 + LEXICAL_WEIGHT × 查询词覆盖率。
     * 覆盖率用字符二元组近似，兼容中英文，无需引入分词器。
     */
    private List<DocumentScorePair> relevanceRerank(String query, List<DocumentScorePair> pairs) {
        Set<String> queryGrams = bigrams(query);
        for (DocumentScorePair pair : pairs) {
            double lexical = queryGrams.isEmpty() ? 0.0 : coverage(queryGrams, pair.document.getContent());
            pair.fusedScore = pair.vectorScore + LEXICAL_WEIGHT * lexical;
        }
        pairs.sort((a, b) -> Double.compare(b.fusedScore, a.fusedScore));
        return pairs;
    }

    /**
     * 多样性重排序：按向量分排序后，抑制内容前缀重复的片段
     */
    private List<DocumentScorePair> diversityRerank(List<DocumentScorePair> pairs) {
        List<DocumentScorePair> selected = new ArrayList<>();
        Set<String> selectedContent = new HashSet<>();

        pairs.sort((a, b) -> Double.compare(b.vectorScore, a.vectorScore));

        for (DocumentScorePair pair : pairs) {
            if (selectedContent.add(hashContent(pair.document.getContent()))) {
                selected.add(pair);
            }
        }

        return selected;
    }

    /** 查询二元组在片段中命中的比例，取值 [0,1] */
    private double coverage(Set<String> queryGrams, String content) {
        Set<String> contentGrams = bigrams(content);
        if (contentGrams.isEmpty()) {
            return 0.0;
        }
        long hit = queryGrams.stream().filter(contentGrams::contains).count();
        return (double) hit / queryGrams.size();
    }

    /** 归一化后取字符二元组：去空白与标点、转小写，兼容中英文 */
    private Set<String> bigrams(String text) {
        Set<String> grams = new HashSet<>();
        if (text == null) {
            return grams;
        }
        String normalized = text.toLowerCase().replaceAll(PUNCTUATION_CLASS, "");
        for (int i = 0; i + 2 <= normalized.length(); i++) {
            grams.add(normalized.substring(i, i + 2));
        }
        if (grams.isEmpty() && !normalized.isEmpty()) {
            grams.add(normalized);
        }
        return grams;
    }

    private String hashContent(String content) {
        if (content == null || content.length() < 100) {
            return content;
        }
        return content.substring(0, 100);
    }

    private static class DocumentScorePair {
        Document document;
        double vectorScore;
        double fusedScore;

        DocumentScorePair(Document document, double vectorScore) {
            this.document = document;
            this.vectorScore = vectorScore;
            this.fusedScore = vectorScore;
        }
    }
}
