# Enterprise RAG Agent - 企业级智能问答系统

> 单一技术栈：Java 21 + SpringAI + LangChain4j  
> 精准定位：企业最稀缺的Java+AI复合型人才

---

## 📊 市场数据（2026年1-4月）

### 核心发现：
- ✅ "懂大模型的Java工程师正被疯抢"
- ✅ "Java开发者薪资溢价 **40%+**"
- ✅ "企业现有系统多以Java为核心"
- ✅ "复合型人才稀缺：懂Java+AI技术的人才正是企业争抢的目标"

### 对比数据：
| 维度 | Java + SpringAI | Python + LangChain |
|------|----------------|-------------------|
| 岗位类型 | 企业级应用开发 | 算法岗/应用岗 |
| 人才稀缺度 | ⭐⭐⭐⭐⭐ 极缺 | ⭐⭐⭐ 一般 |
| 竞争激烈度 | ⭐⭐ 低竞争 | ⭐⭐⭐⭐⭐ 高竞争 |
| 薪资溢价 | **40%+** | 30%+ |

---



---

## 🏗️ 技术架构（精简版）

```
┌─────────────────────────────────────────────┐
│            用户交互层                         │
│     (REST API / WebSocket / 飞书机器人)      │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│         RAG 核心引擎 (Java 21)               │
│  ┌─────────────────────────────────────┐   │
│  │ DocumentProcessor - 文档解析与切片   │   │
│  ├─────────────────────────────────────┤   │
│  │ EmbeddingService  - 向量嵌入生成     │   │
│  ├─────────────────────────────────────┤   │
│  │ RetrievalService  - 混合检索策略     │   │
│  ├─────────────────────────────────────┤   │
│  │ RerankService     - 重排序优化       │   │
│  ├─────────────────────────────────────┤   │
│  │ ChatService       - 对话生成         │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  框架：Spring Boot 3.3 + Spring AI 1.0.2    │
│       + LangChain4j 0.36.2                  │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│            向量数据库层                      │
│       Milvus / ChromaDB / pgvector          │
└─────────────────────────────────────────────┘
```

**删除的层**：
- ❌ Python Agent层（LangGraph）- 算法岗为主，竞争激烈
- ❌ Node.js MCP层 - 非核心，可后续扩展

---

## 🚀 核心功能

### Phase 1：基础RAG引擎（当前）
- [x] 文档解析（PDF、Word、Markdown、TXT）
- [x] 智能切片（语义感知分块）
- [x] 向量嵌入（OpenAI 兼容 Embedding）
- [x] 向量入库与检索（Milvus）
- [x] 基础问答（ChatClient 生成）
- [x] 前端聊天界面 + 文档上传

### Phase 2：高级RAG优化
- [ ] 混合检索（Vector + BM25）
- [ ] Query改写与扩展
- [x] Rerank重排序（分数排序 + 多样性）
- [ ] 上下文窗口优化（token 预算）
- [ ] 流式问答

### Phase 3：企业级特性
- [ ] 多租户支持
- [ ] 权限管理
- [ ] 问答审计日志
- [ ] 性能监控

### Phase 4：扩展能力（可选）
- [ ] Agent基础能力（LangChain4j内置）
- [ ] 飞书机器人集成
- [ ] MCP协议支持（可选）

---

## 🛠️ 技术栈（精简）

```xml
<!-- 核心框架 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <version>3.3.0</version>
</dependency>

<!-- Spring AI -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    <version>1.0.2</version>
</dependency>

<!-- LangChain4j（增强Agent能力） -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>0.36.2</version>
</dependency>

<!-- 向量数据库 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-milvus-store-spring-boot-starter</artifactId>
    <version>1.0.2</version>
</dependency>
```

---

## 📁 项目结构（精简）

```
enterprise-rag-agent-zhuyt/
├── rag-engine/                    # Java RAG核心引擎
│   ├── src/main/java/
│   │   ├── config/              # 配置（RagProperties、Milvus）
│   │   ├── controller/          # API接口
│   │   ├── service/             # RagService 编排
│   │   ├── rag/                 # RAG实现
│   │   │   ├── document/        # 文档解析
│   │   │   ├── embedding/       # 向量嵌入
│   │   │   ├── retrieval/       # 检索服务
│   │   │   ├── rerank/          # 重排序
│   │   │   └── vectorstore/     # Milvus 入库
│   │   └── model/               # 数据模型
│   ├── pom.xml
│   └── application.yml
│
├── frontend/                      # React 聊天界面
│   ├── src/App.tsx
│   └── vite.config.ts
│
├── docker-compose.yml             # Milvus 本地部署
├── .env.example                   # 环境变量模板
├── docs/
│   └── architecture.md
│
└── README.md
```

---

## 🚀 快速启动

```powershell
# 1. 启动 Milvus
docker compose up -d

# 2. 配置环境变量
Copy-Item .env.example .env
# 编辑 .env，填入 OPENAI_API_KEY

# 3. 启动后端（端口 8089）
cd rag-engine
mvn spring-boot:run

# 4. 启动前端（端口 3000）
cd frontend
npm install
npm run dev
```

浏览器打开 http://localhost:3000 → 上传文档 → 提问测试。
