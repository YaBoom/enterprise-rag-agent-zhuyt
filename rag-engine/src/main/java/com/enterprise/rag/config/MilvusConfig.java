package com.enterprise.rag.config;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;

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

    @Value("${rag.collection.name:enterprise_knowledge}")
    private String collectionName;

    @Bean
    public MilvusClientV2 milvusClient() {
        MilvusClientV2 client = new MilvusClientV2(
            io.milvus.v2.client.ConnectConfig.builder()
                .uri(milvusHost + ":" + milvusPort)
                .databaseName(database)
                .build()
        );

        createCollectionIfNotExists(client);
        
        return client;
    }

    private void createCollectionIfNotExists(MilvusClientV2 client) {
        HasCollectionReq hasReq = HasCollectionReq.builder()
            .collectionName(collectionName)
            .build();
        
        Boolean hasCollection = client.hasCollection(hasReq);
        
        if (!hasCollection) {
            CreateCollectionReq.CreateCollectionReq.FieldSchema<DataType> idField = 
                CreateCollectionReq.CreateCollectionReq.FieldSchema.builder()
                    .name("id")
                    .dataType(DataType.VarChar)
                    .maxLength(256)
                    .isPrimaryKey(true)
                    .build();
            
            CreateCollectionReq.CreateCollectionReq.FieldSchema<DataType> contentField = 
                CreateCollectionReq.CreateCollectionReq.FieldSchema.builder()
                    .name("content")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build();
            
            CreateCollectionReq.CreateCollectionReq.FieldSchema<DataType> embeddingField = 
                CreateCollectionReq.CreateCollectionReq.FieldSchema.builder()
                    .name("embedding")
                    .dataType(DataType.FloatVector)
                    .dimension(1536)
                    .build();
            
            CreateCollectionReq.CreateCollectionReq.FieldSchema<DataType> metadataField = 
                CreateCollectionReq.CreateCollectionReq.FieldSchema.builder()
                    .name("metadata")
                    .dataType(DataType.JSON)
                    .build();
            
            CreateCollectionReq createReq = CreateCollectionReq.builder()
                .collectionName(collectionName)
                .description("企业知识库向量存储")
                .fieldSchemaList(Arrays.asList(idField, contentField, embeddingField, metadataField))
                .build();
            
            client.createCollection(createReq);
            
            System.out.println("✅ Milvus Collection 创建成功：" + collectionName);
        }
    }
}