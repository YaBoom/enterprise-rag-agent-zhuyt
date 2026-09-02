# 架构设计

## 总览

系统分为三层：前端交互层、Java 后端 RAG 引擎、向量数据库层。后端按职责拆分为 API、编排、RAG 子服务与配置四组；模型通过 OpenAI 兼容接口接入，向量存储使用 Milvus。

```
前端 (React + Ant Design X, :3000)
        │ HTTP /api（Vite 代理到 :8089）
        ▼
后端 RAG 引擎 (Spring Boot 3.3 + Spring AI 1.0.2, :8089)
  RagController → RagService
        ├── DocumentProcessor   解析 + 递归分块
        ├── EmbeddingService    向量化（分批）
        ├── RetrievalService    混合检索（向量 + 稀疏 BM25，RRF 融合）
        ├── RerankService       融合重排 / 去重
        └── Chat 生成           ChatClient + 会话记忆
        ▼
Milvus 2.4.4 (:19530, 稠密 COSINE/AUTOINDEX + 稀疏 IP/SPARSE_INVERTED_INDEX)
```

## 数据流

### 文档入库

1. `RagController` 接收 multipart 文件。
2. `DocumentProcessor` 按类型解析（PDFBox / POI / 纯文本），用 LangChain4j 递归分块（chunkSize=1000、overlap=200），为每块生成两级标识：documentId（整个文件）与 id（单个切片）。
3. `EmbeddingService` 按 `rag.embedding.batch-size` 分批调用 Embedding 模型生成向量。
4. `MilvusVectorStore` 将正文、元数据与向量写入 Collection。

### 问答检索

1. `RagService` 解析 conversationId，读取请求级 topK / strategy / scoreThreshold。
2. `RetrievalService` 按 strategy 执行检索：HYBRID（默认）为稠密向量 + BM25 稀疏向量双通道召回、RRF 融合；VECTOR 为单路向量检索并按相似度阈值过滤。
3. 若启用重排，`RerankService` 以「向量相似度 + 查询词覆盖率」的融合分重新排序（向量分主导）；用于展示与置信度的分数仍保留原始向量相似度。
4. `RagService` 拼接上下文并通过 ChatClient 生成答案；system prompt 约束仅依据检索内容作答，生成温度默认 0.2 以降低幻觉。
5. 会话记忆由 `MessageChatMemoryAdvisor` 按 conversationId 写入与回传。

## 关键设计

- 两级 ID：documentId 标识整个文件，id 标识每个切片，支持按文件维度批量删除。
- 请求级检索参数：topK 与 scoreThreshold 支持按请求覆盖全局配置，便于 A/B 评估（见 `eval/`）。
- 分块策略：LangChain4j 递归分块，优先按段落 / 行 / 句子边界切分，属固定窗口分块（非语义分块）。
- 相似度阈值：text-embedding-v4 的相似度分布整体偏低，阈值默认 0.3，过高会误滤有效召回。
- 模型可插拔：Chat 与 Embedding 通过 OpenAI 兼容接口分别配置，可切换 DeepSeek / 百炼 Qwen / OpenAI。

## 向量数据库

Milvus Collection schema（由 `MilvusCollectionRunner` 在启动时创建）：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | VarChar(256) | 切片主键 |
| content | VarChar(65535) | 切片正文 |
| title | VarChar(512) | 文件标题 |
| source | VarChar(512) | 来源文件名 |
| type | VarChar(32) | 文件类型 |
| document_id | VarChar(256) | 所属文件 ID |
| chunk_index | Int32 | 切片序号 |
| metadata | VarChar(65535) | JSON 元数据 |
| embedding | FloatVector(dim) | 向量，维度取 `rag.embedding.dimension` |
| sparse | SparseFloatVector | BM25 稀疏向量（混合检索关键词通道，token id → 权重） |

稠密向量索引 AUTOINDEX、度量 COSINE；稀疏向量索引 SPARSE_INVERTED_INDEX、度量 IP。

## 演进方向

- 检索：Query 改写与扩展。
- 生成：流式输出（SSE）、上下文 token 预算控制。
- 企业特性：多租户隔离、权限管理、问答审计日志、性能监控。
