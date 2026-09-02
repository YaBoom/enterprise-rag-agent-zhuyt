# Enterprise RAG Agent

基于 Java 21 + Spring AI 的企业级 RAG（检索增强生成）智能问答系统。后端负责文档解析、向量入库、检索、重排与答案生成，前端提供聊天与文档上传界面，向量存储使用 Milvus。

## 技术栈

| 层次 | 技术 |
|---|---|
| 后端框架 | Java 21、Spring Boot 3.3.0、Spring AI 1.0.2 |
| 文档解析与分块 | LangChain4j 0.36.2（Apache PDFBox、Apache POI） |
| 向量数据库 | Milvus 2.4.4（COSINE 度量、AUTOINDEX 索引） |
| 对话/嵌入模型 | OpenAI 兼容接口（DeepSeek、阿里云百炼 Qwen、OpenAI，见 `.env.example`） |
| 前端 | React 18、Vite 5、Ant Design X、Ant Design 5、Axios |
| 离线评估 | Python + RAGAS（见 `eval/`） |

## 架构

请求链路：`RagController` → `RagService` → 检索 / 重排 / 生成子服务 → Milvus 与 Chat 模型。

| 模块 | 类 | 职责 |
|---|---|---|
| API 层 | `RagController` | REST 接口：文档上传/删除/列表、问答、健康检查 |
| 编排层 | `RagService` | 串联检索 → 重排 → 生成，组装来源与分阶段耗时，挂载会话记忆 |
| 文档处理 | `DocumentProcessor` | 解析文档并递归分块（chunkSize=1000、overlap=200） |
| 向量嵌入 | `EmbeddingService` | 调用 Embedding 模型，按 batch 上限分批生成向量 |
| 检索 | `RetrievalService` | Milvus 向量检索（COSINE），支持请求级 topK 与相似度阈值过滤 |
| 重排 | `RerankService` | 向量相似度与查询词覆盖率融合重排；或按内容去重（DIVERSITY） |
| 向量存储 | `MilvusVectorStore` | Collection 入库、按 documentId 删除、文档标题列表 |
| 初始化 | `MilvusCollectionRunner` | 启动时创建 Collection 与向量索引并加载 |

多轮对话：`RagService` 通过 Spring AI `MessageChatMemoryAdvisor` + `MessageWindowChatMemory`，按 `conversationId` 维护会话记忆，窗口大小由 `rag.chat.memory.max-messages` 控制。

## 功能现状

已实现：

- 文档解析（PDF、Word、Excel、PPT、Markdown、TXT）与递归分块
- 向量嵌入、Milvus 入库、检索与按文档删除
- 向量检索 + 相似度阈值过滤（支持请求级 `topK` / `scoreThreshold`）
- 轻量重排：向量分与查询词覆盖率融合，或多样性去重
- 基于检索内容的答案生成，低温度约束以降低幻觉
- 多轮对话会话记忆
- React 聊天界面与文档上传
- 基于 RAGAS 的离线评估体系（测试集 + 采集/评估/对比脚本）

规划中（尚未实现）：

- 混合检索（向量 + BM25/稀疏向量）
- Query 改写与扩展
- 流式问答（SSE）
- 多租户、权限管理、问答审计

## 快速开始

前置条件：JDK 21、Maven 3.9+、Node.js 18+、Docker。

```powershell
# 1. 启动 Milvus
docker compose up -d

# 2. 配置环境变量：复制 .env.example 为 .env，填入 Chat 与 Embedding 的 Key / Base URL / 模型名
Copy-Item .env.example .env

# 3. 启动后端（端口 8089）
cd rag-engine
mvn spring-boot:run

# 4. 启动前端（端口 3000，已配置 /api 代理到 8089）
cd frontend
npm install
npm run dev
```

浏览器访问 http://localhost:3000 ，上传文档后即可提问。

> DeepSeek 不提供 `/v1/embeddings`，需单独配置 `OPENAI_EMBEDDING_*`（OpenAI 或百炼 text-embedding-v4），详见 `.env.example` 中的三套方案。

## API

Base URL：`http://localhost:8089/api/v1/rag`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/documents` | 上传文档（multipart，字段名 `file`） |
| GET | `/documents` | 文档标题列表 |
| DELETE | `/documents/{documentId}` | 删除该文档的所有切片 |
| POST | `/query` | 问答，返回答案、来源、置信度与分阶段耗时 |
| GET | `/health` | 健康检查，含 Embedding 配置解析结果 |

问答请求体示例：

```json
{
  "question": "事假有多少天？",
  "conversationId": "可选，用于延续多轮对话",
  "retrievalParams": { "topK": 5, "scoreThreshold": 0.3, "strategy": "VECTOR", "enableRerank": true }
}
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `rag.collection.name` | enterprise_knowledge | Milvus Collection 名 |
| `rag.document.chunk-size` | 1000 | 分块大小（字符数） |
| `rag.document.chunk-overlap` | 200 | 相邻分块重叠字符数 |
| `rag.retrieval.top-k` | 5 | 默认召回数量 |
| `rag.retrieval.score-threshold` | 0.3 | 相似度阈值，低于该值的召回被过滤 |
| `rag.embedding.dimension` | 1536 | 向量维度，需与 Embedding 模型一致 |
| `rag.embedding.batch-size` | 10 | 单次嵌入条数（百炼 text-embedding-v4 上限为 10） |
| `rag.chat.memory.max-messages` | 20 | 会话记忆窗口大小（消息条数） |
| `spring.ai.openai.chat.options.temperature` | 0.2 | 生成温度，低温有利于降低幻觉 |

向量维度或 Collection 结构变更后，需要删除并重建 Milvus Collection。

## 评估

`eval/` 目录提供基于 RAGAS 的离线评估：测试集、结果采集、四指标评估与 A/B 对比脚本。执行流程与指标说明见 [eval/README.md](eval/README.md)。

## 项目结构

```
enterprise-rag-agent-zhuyt/
├── rag-engine/                 # Java 后端（Spring Boot + Spring AI）
│   └── src/main/java/com/enterprise/rag/
│       ├── config/             # 配置：RagProperties、Milvus、Spring AI、HTTP 超时、.env 加载
│       ├── controller/         # REST 接口
│       ├── service/            # RagService 编排
│       ├── rag/                # document / embedding / retrieval / rerank / vectorstore
│       └── model/              # 数据模型
├── frontend/                   # React + Ant Design X 聊天界面
├── eval/                       # RAGAS 评估：dataset / scripts / results
├── docs/                       # 架构与前端说明
├── docker-compose.yml          # Milvus 本地部署
├── .env.example                # 环境变量模板
└── README.md
```

## 运行说明

- Embedding 模型与 Chat 模型可分别配置，互不绑定。
- 后端启动时若 Milvus 未就绪，会跳过 Collection 初始化，健康检查仍可用；待 Milvus 就绪后重启即可。
- 推理类模型响应较慢，`AiHttpClientConfig` 已将读超时放宽至 3 分钟。
