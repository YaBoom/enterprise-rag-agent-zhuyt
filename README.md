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

## 🎯 项目定位

### 为什么选择单一技术栈？

**三套技术栈的问题**：
- ❌ 精力分散，每个都学不精
- ❌ 项目复杂度过高，难以持续迭代
- ❌ 不符合"找准一个点持续发力"的初衷

**单一技术栈的优势**：
- ✅ 精力聚焦，深度掌握
- ✅ 快速迭代，持续优化
- ✅ 市场稀缺，竞争最小

### 为什么选择 Java + SpringAI？

**匹配你的情况**：
- ✅ 你已有8年Java经验，基础扎实
- ✅ Spring生态熟悉，学习成本低
- ✅ 企业级开发经验丰富

**匹配市场需求**：
- ✅ 企业现有系统是Java，需要融入AI能力
- ✅ Java+AI复合型人才极缺
- ✅ 薪资溢价最高（40%+）

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

---

## 💰 变现路径

### 1. 企业咨询（5-20万/项目）
- 帮助企业搭建私有知识库
- Java系统融入AI能力
- RAG系统定制开发

### 2. 技术培训（3000-8000/人）
- Java工程师AI转型培训
- SpringAI实战课程
- 企业内训与技术分享

### 3. 开源贡献（长期价值）
- GitHub项目积累Star
- 打造"Java+AI"技术品牌
- 吸引优质企业机会

### 4. 跳槽筹码
- 简历中的"杀手级项目"
- Java+AI复合能力证明
- **薪资溢价40%+**

---

## 📚 学习路线（4周，聚焦Java）

### Week 1：RAG基础
- [ ] SpringAI框架入门
- [ ] 文档解析与切片
- [ ] 向量嵌入生成
- [ ] 基础检索实现

### Week 2：高级RAG
- [ ] 混合检索策略
- [ ] Query改写优化
- [ ] Rerank重排序
- [ ] LangChain4j集成

### Week 3：企业级特性
- [ ] 多租户架构
- [ ] 权限管理
- [ ] 性能优化
- [ ] 生产部署

### Week 4：实战优化
- [ ] 真实场景测试
- [ ] 性能调优
- [ ] 文档完善
- [ ] 技术文章输出

---

## 📊 对比：精简前 vs 精简后

| 维度 | 精简前（三套技术栈） | 精简后（单一Java） |
|------|---------------------|-------------------|
| 技术栈数量 | 3套（Java/Python/Node.js） | 1套（Java） |
| 学习难度 | ⭐⭐⭐⭐⭐ 极高 | ⭐⭐⭐ 适中 |
| 精力分散 | ❌ 严重 | ✅ 聚焦 |
| 迭代速度 | ❌ 慢 | ✅ 快 |
| 市场匹配 | ⚠️ Python竞争激烈 | ✅ Java+AI极缺 |
| 薪资溢价 | 30%+ | **40%+** |
| 适合亚腾 | ⚠️ 需补Python/算法 | ✅ 完美匹配 |

---

## ✅ 决策依据

### 数据来源：
1. "懂大模型的Java工程师正被疯抢" - CSDN 2026
2. "Java开发者薪资溢价40%+" - 葡萄城开发者空间 2026
3. "复合型人才稀缺：懂Java+AI技术的人才正是企业争抢的目标" - CSDN 2026
4. "企业现有系统多以Java为核心" - 多篇技术文章
5. "Python更适合中小型项目，Java更适合大型企业级应用" - 知乎对比分析

---

**创建时间**: 2026-06-04  
**技术栈**: Java 21 + SpringAI + LangChain4j（单一聚焦）  
**核心理由**: 市场最稀缺、竞争最小、薪资溢价最高、完美匹配你的背景

**🦞 龙虾AI - 数据驱动，精准定位，单一发力！**