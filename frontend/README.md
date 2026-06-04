# Enterprise RAG Agent 前端

> 最小化前端（10%精力） - 使用 Ant Design X 快速搭建 AI Chat UI

## 技术栈

- **React 18** - 前端框架
- **TypeScript** - 类型安全
- **Vite** - 构建工具
- **Ant Design X** - AI Chat UI 组件库（阿里蚂蚁出品）
- **Axios** - HTTP 请求

## 快速开始

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 访问 http://localhost:3000
```

## 架构设计

### 为什么选择 Ant Design X？

1. ✅ **5分钟快速集成** - 开箱即用的 AI Chat 组件
2. ✅ **阿里蚂蚁出品** - 企业级标准，中文友好
3. ✅ **React 生态** - 与 Java 后端架构一致
4. ✅ **最小化学习成本** - 10%精力即可完成

### 目录结构

```
frontend/
├── src/
│   ├── App.tsx          # 主应用（聊天界面）
│   ├── main.tsx         # 入口文件
│   └── index.css        # 全局样式
├── index.html           # HTML 模板
├── vite.config.ts       # Vite 配置
├── tsconfig.json        # TypeScript 配置
└── package.json         # 依赖管理
```

## 功能特性

### 已实现
- ✅ AI 问答聊天界面
- ✅ 消息气泡展示
- ✅ 来源引用显示
- ✅ 加载状态提示
- ✅ 后端 API 对接

### 待扩展（可选）
- 🔲 文档上传界面
- 🔲 历史记录管理
- 🔲 主题切换

## API 对接

前端通过 `/api/v1/rag/*` 路径访问后端 API：

- `POST /api/v1/rag/query` - 问答查询
- `POST /api/v1/rag/documents` - 上传文档
- `GET /api/v1/rag/documents` - 获取文档列表

## 构建部署

```bash
# 构建
npm run build

# 生成的文件在 dist/ 目录
```

---

**精简原则：最小化前端，聚焦后端核心能力！**