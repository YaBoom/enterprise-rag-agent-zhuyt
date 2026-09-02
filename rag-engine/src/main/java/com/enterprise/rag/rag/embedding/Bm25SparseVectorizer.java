package com.enterprise.rag.rag.embedding;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BM25 风格稀疏向量生成器（混合检索关键词通道）。
 * <p>
 * 分词策略：
 * - ASCII 字母数字连续段（转小写，长度 ≥ 2）
 * - 连续中文字段按覆盖式二元组（bigram）切分，兼容中文检索且无需分词器
 * <p>
 * 权重为 sublinear TF（1 + ln(tf))，配合 Milvus IP 度量即可按词面重合度排序；
 * token 经 FNV-1a 64 哈希映射为稀疏向量下标，保证同 token 稳定映射。
 *
 * @author jack.zhu
 */
@Component
public class Bm25SparseVectorizer {

    /** ASCII 字母数字 token（长度 ≥ 2 才保留，过滤单字符噪音） */
    private static final Pattern ASCII_TOKEN = Pattern.compile("[A-Za-z0-9]+");
    /** 连续 CJK 中文字段 */
    private static final Pattern CJK_RUN = Pattern.compile("[\\u4E00-\\u9FFF]+");

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /**
     * 将文本向量化为稀疏向量：token 哈希下标 → sublinear TF 权重
     *
     * @param text 文档片段或查询文本
     * @return token id → 权重（有序）；无有效 token 时返回空 Map
     */
    public SortedMap<Long, Float> vectorize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySortedMap();
        }

        String normalized = text.toLowerCase();
        Map<String, Integer> termFrequency = new TreeMap<>();

        Matcher asciiMatcher = ASCII_TOKEN.matcher(normalized);
        while (asciiMatcher.find()) {
            String token = asciiMatcher.group();
            if (token.length() >= 2) {
                termFrequency.merge(token, 1, Integer::sum);
            }
        }

        Matcher cjkMatcher = CJK_RUN.matcher(normalized);
        while (cjkMatcher.find()) {
            String run = cjkMatcher.group();
            for (int i = 0; i + 2 <= run.length(); i++) {
                termFrequency.merge(run.substring(i, i + 2), 1, Integer::sum);
            }
        }

        if (termFrequency.isEmpty()) {
            return Collections.emptySortedMap();
        }

        SortedMap<Long, Float> sparse = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : termFrequency.entrySet()) {
            // sublinear TF：抑制长片段对高频词权重的放大
            double weight = 1.0 + Math.log(entry.getValue());
            sparse.put(fnv1a64(entry.getKey()), (float) weight);
        }
        return sparse;
    }

    /**
     * FNV-1a 64 哈希后映射到 Milvus 合法 token id 区间 (0, 2^32-1)：
     * 取模 2^32-2 再 +1，得到 [1, 2^32-2] 内的非负值
     */
    private long fnv1a64(String token) {
        long hash = FNV_OFFSET_BASIS;
        for (byte b : token.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xFF;
            hash *= FNV_PRIME;
        }
        return (hash & Long.MAX_VALUE) % (0xFFFFFFFEL) + 1L;
    }
}
