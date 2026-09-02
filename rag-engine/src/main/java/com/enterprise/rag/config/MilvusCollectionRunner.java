package com.enterprise.rag.config;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.index.request.CreateIndexReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;

/**
 * Milvus Collection 初始化
 */
@Slf4j
@Component
public class MilvusCollectionRunner implements CommandLineRunner {

    /** 混合检索稀疏字段名（BM25 风格关键词通道） */
    private static final String SPARSE_FIELD = "sparse";

    @Autowired(required = false)
    private MilvusClientV2 milvusClient;

    @Autowired
    private RagProperties ragProperties;

    @Override
    public void run(String... args) {
        if (milvusClient == null) {
            log.warn("MilvusClient 未配置，跳过 Collection 初始化");
            return;
        }

        String collectionName = ragProperties.getCollection().getName();
        int dimension = ragProperties.getEmbedding().getDimension();

        HasCollectionReq hasReq = HasCollectionReq.builder()
            .collectionName(collectionName)
            .build();

        boolean collectionExists = Boolean.TRUE.equals(milvusClient.hasCollection(hasReq));
        if (collectionExists && !containsSparseField(collectionName)) {
            // 旧版结构（仅稠密向量）：删除重建以加入稀疏字段，原数据需重新上传
            log.warn("[Milvus] Collection {} 为旧版结构（缺少稀疏字段），自动删除重建以支持混合检索，原数据已清除，请重新上传文档",
                collectionName);
            milvusClient.dropCollection(DropCollectionReq.builder().collectionName(collectionName).build());
            collectionExists = false;
        }
        if (!collectionExists) {
            CreateCollectionReq.FieldSchema idField = field("id", DataType.VarChar, 256, true, false);
            CreateCollectionReq.FieldSchema contentField = field("content", DataType.VarChar, 65535, false, false);
            CreateCollectionReq.FieldSchema titleField = field("title", DataType.VarChar, 512, false, false);
            CreateCollectionReq.FieldSchema sourceField = field("source", DataType.VarChar, 512, false, false);
            CreateCollectionReq.FieldSchema typeField = field("type", DataType.VarChar, 32, false, false);
            CreateCollectionReq.FieldSchema documentIdField = field("document_id", DataType.VarChar, 256, false, false);
            CreateCollectionReq.FieldSchema chunkIndexField = field("chunk_index", DataType.Int32, 0, false, false);
            CreateCollectionReq.FieldSchema metadataField = field("metadata", DataType.VarChar, 65535, false, false);
            CreateCollectionReq.FieldSchema embeddingField = CreateCollectionReq.FieldSchema.builder()
                .name("embedding")
                .dataType(DataType.FloatVector)
                .dimension(dimension)
                .build();
            CreateCollectionReq.FieldSchema sparseField = CreateCollectionReq.FieldSchema.builder()
                .name(SPARSE_FIELD)
                .dataType(DataType.SparseFloatVector)
                .build();

            CreateCollectionReq.CollectionSchema collectionSchema = CreateCollectionReq.CollectionSchema.builder()
                .fieldSchemaList(Arrays.asList(
                    idField, contentField, titleField, sourceField, typeField,
                    documentIdField, chunkIndexField, metadataField, embeddingField, sparseField
                ))
                .build();

            milvusClient.createCollection(CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(collectionSchema)
                .description("企业知识库向量存储")
                .build());

            log.info("Milvus Collection 创建成功: {}, dimension={}", collectionName, dimension);
        } else {
            log.info("Milvus Collection 已存在: {}", collectionName);
        }

        createEmbeddingIndex(collectionName);
        createSparseIndex(collectionName);

        milvusClient.loadCollection(LoadCollectionReq.builder()
            .collectionName(collectionName)
            .build());
        log.info("Milvus Collection 已加载: {}", collectionName);
    }

    private void createEmbeddingIndex(String collectionName) {
        CreateIndexReq indexReq = CreateIndexReq.builder()
            .collectionName(collectionName)
            .indexParams(Collections.singletonList(
                IndexParam.builder()
                    .fieldName("embedding")
                    .indexType(IndexParam.IndexType.AUTOINDEX)
                    .metricType(IndexParam.MetricType.COSINE)
                    .build()
            ))
            .build();
        try {
            milvusClient.createIndex(indexReq);
            log.info("Milvus embedding 索引已就绪: {}", collectionName);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("already exist")) {
                log.debug("Milvus embedding 索引已存在: {}", collectionName);
            } else {
                throw e;
            }
        }
    }

    /**
     * 检查 Collection 是否已包含稀疏字段（判断是否为旧版结构）
     */
    private boolean containsSparseField(String collectionName) {
        DescribeCollectionResp resp = milvusClient.describeCollection(
            DescribeCollectionReq.builder().collectionName(collectionName).build());
        return resp.getCollectionSchema().getFieldSchemaList().stream()
            .anyMatch(f -> SPARSE_FIELD.equals(f.getName()));
    }

    private void createSparseIndex(String collectionName) {
        CreateIndexReq indexReq = CreateIndexReq.builder()
            .collectionName(collectionName)
            .indexParams(Collections.singletonList(
                IndexParam.builder()
                    .fieldName(SPARSE_FIELD)
                    .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                    .metricType(IndexParam.MetricType.IP)
                    .build()
            ))
            .build();
        try {
            milvusClient.createIndex(indexReq);
            log.info("Milvus sparse 索引已就绪: {}", collectionName);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("already exist")) {
                log.debug("Milvus sparse 索引已存在: {}", collectionName);
            } else {
                throw e;
            }
        }
    }

    private CreateCollectionReq.FieldSchema field(String name, DataType type, int maxLength,
                                                   boolean primaryKey, boolean autoId) {
        CreateCollectionReq.FieldSchema.FieldSchemaBuilder builder = CreateCollectionReq.FieldSchema.builder()
            .name(name)
            .dataType(type)
            .isPrimaryKey(primaryKey)
            .autoID(autoId);
        if (maxLength > 0) {
            builder.maxLength(maxLength);
        }
        return builder.build();
    }
}
