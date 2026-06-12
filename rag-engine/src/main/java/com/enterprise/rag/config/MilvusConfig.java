package com.enterprise.rag.config;

import io.milvus.v2.client.MilvusClientV2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 向量数据库配置
 *
 * @author jack.zhu
 */
@Configuration
public class MilvusConfig {

    @Value("${spring.ai.milvus.host:localhost}")
    private String milvusHost;

    @Value("${spring.ai.milvus.port:19530}")
    private Integer milvusPort;

    @Value("${spring.ai.milvus.database:default}")
    private String database;

    @Bean
    public MilvusClientV2 milvusClientV2() {
        try {
            String uri = milvusHost.startsWith("http") ? milvusHost : "http://" + milvusHost;
            uri = uri + ":" + milvusPort;
            return new MilvusClientV2(
                io.milvus.v2.client.ConnectConfig.builder()
                    .uri(uri)
                    .dbName(database)
                    .build()
            );
        } catch (Exception e) {
            System.out.println("⚠️ Milvus 连接失败：" + e.getMessage() + "，跳过 Milvus 配置");
            return null;
        }
    }
}
