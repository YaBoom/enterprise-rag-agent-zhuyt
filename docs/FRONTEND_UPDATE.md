# 前端说明

前端是基于 React 18 + Ant Design X 的聊天界面，提供问答与文档上传，通过 Vite 开发服务器代理调用后端 API。

## 技术栈

- React 18.3、TypeScript 5.5、Vite 5.4
- Ant Design X 1.0：`Bubble`、`Sender`、`useXAgent`、`useXChat` 聊天组件
- Ant Design 5.20：布局、上传等基础组件
- Axios 1.7：HTTP 调用

## 目录结构

```
frontend/
├── src/
│   ├── App.tsx        # 聊天主界面：问答、来源展示、文档上传
│   ├── main.tsx       # 入口
│   ├── App.css        # 组件样式
│   └── index.css      # 全局样式
├── index.html
├── vite.config.ts     # 端口 3000，/api 代理到 8089
├── tsconfig.json
└── package.json
```

## 与后端的接口约定

- 所有请求经 Vite 代理转发：`/api` → `http://localhost:8089`。
- 问答：`POST /api/v1/rag/query`，请求体携带 `question`、`conversationId`、`retrievalParams`。
- 上传：`POST /api/v1/rag/documents`（multipart，字段名 `file`），接受 `.txt/.md/.pdf/.doc/.docx`。
- 文档列表：`GET /api/v1/rag/documents`。
- 后端统一响应结构 `ApiResult<T>`：`{ code, message, data }`，`code=0` 表示成功。

## 多轮对话

首次问答不传 `conversationId`，由后端生成并在响应中回传；前端用 `useRef` 保存该 ID，后续请求回传以延续会话记忆。这里用 ref 而非 state，是为了避免 `useXAgent` 闭包缓存旧值。

## 运行

```powershell
cd frontend
npm install
npm run dev    # http://localhost:3000
```

生产构建：`npm run build`（先执行 `tsc` 类型检查，再 `vite build`，产物输出到 `dist/`）。
