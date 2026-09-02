# Enterprise RAG Agent 前端

基于 React 18 + Ant Design X 的聊天界面，提供问答与文档上传，通过 Vite 代理调用后端 API。

## 技术栈

- React 18、TypeScript、Vite
- Ant Design X：AI 聊天组件（`Bubble`、`Sender`、`useXAgent`、`useXChat`）
- Ant Design：布局与上传组件
- Axios：HTTP 请求

## 快速开始

```bash
npm install
npm run dev      # http://localhost:3000
```

开发服务器已在 `vite.config.ts` 中配置 `/api` 代理到后端 `http://localhost:8089`。

## 目录结构

```
frontend/
├── src/
│   ├── App.tsx          # 主应用（聊天界面 + 文档上传）
│   ├── main.tsx         # 入口
│   ├── App.css          # 组件样式
│   └── index.css        # 全局样式
├── index.html
├── vite.config.ts       # 端口与 /api 代理配置
├── tsconfig.json
└── package.json
```

## 功能

已实现：

- 问答聊天界面与消息气泡
- 检索来源与相似度展示
- 文档上传（`.txt/.md/.pdf/.doc/.docx`）与知识库文档计数
- 多轮对话：保存后端回传的 `conversationId` 并在后续请求回传
- 上传中状态与错误提示

待扩展：

- 历史记录管理
- 主题切换
- 流式输出展示

## API 对接

前端通过 `/api/v1/rag/*` 访问后端：

- `POST /api/v1/rag/query`：问答
- `POST /api/v1/rag/documents`：上传文档（multipart，字段名 `file`）
- `GET /api/v1/rag/documents`：文档列表

统一响应结构 `ApiResult<T>`：`{ code, message, data }`，`code=0` 表示成功。

## 构建

```bash
npm run build    # 先执行 tsc 类型检查，再 vite build，产物输出到 dist/
```
