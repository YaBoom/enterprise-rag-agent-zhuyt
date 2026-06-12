import { useState } from 'react'
import { Bubble, Sender, useXAgent, useXChat } from '@ant-design/x';
import { Card, Typography, Layout, Upload, message, Space, Tag } from 'antd';
import { UploadOutlined } from '@ant-design/icons';
import axios from 'axios';
import './App.css';

const { Title, Paragraph } = Typography;
const { Header, Content } = Layout;

interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

interface QueryResponse {
  answer: string;
  sources?: Array<{
    title?: string;
    score?: number;
    snippet?: string;
  }>;
  confidence?: number;
  totalTime?: number;
}

function App() {
  const [content, setContent] = useState('');
  const [uploading, setUploading] = useState(false);
  const [docCount, setDocCount] = useState(0);

  const fetchDocCount = async () => {
    try {
      const res = await axios.get<ApiResult<string[]>>('/api/v1/rag/documents');
      if (res.data.code === 0 && res.data.data) {
        setDocCount(res.data.data.length);
      }
    } catch {
      // ignore
    }
  };

  const handleUpload = async (file: File) => {
    setUploading(true);
    const formData = new FormData();
    formData.append('file', file);
    try {
      const res = await axios.post<ApiResult<{ filename: string; chunkCount: number }>>(
        '/api/v1/rag/documents',
        formData,
        { headers: { 'Content-Type': 'multipart/form-data' } }
      );
      if (res.data.code === 0) {
        message.success(`上传成功：${res.data.data?.filename}（${res.data.data?.chunkCount} 个切片）`);
        await fetchDocCount();
      } else {
        message.error(res.data.message || '上传失败');
      }
    } catch (error: any) {
      message.error(error.response?.data?.message || error.message || '上传失败');
    } finally {
      setUploading(false);
    }
    return false;
  };

  const [agent] = useXAgent<string, { message: string }, string>({
    request: async (info, { onSuccess, onError }) => {
      const { message: userMessage } = info;
      try {
        const response = await axios.post<ApiResult<QueryResponse>>('/api/v1/rag/query', {
          question: userMessage,
          retrievalParams: {
            topK: 5,
            strategy: 'VECTOR',
            enableRerank: true
          }
        });

        const result = response.data;
        if (result.code !== 0) {
          onError(new Error(result.message || '查询失败'));
          return;
        }

        const data = result.data;
        let replyContent = data.answer || '';

        if (data.sources && data.sources.length > 0) {
          replyContent += '\n\n---\n**参考来源：**\n';
          data.sources.forEach((source, index) => {
            const scoreText = source.score
              ? ` (相似度: ${(source.score * 100).toFixed(1)}%)`
              : '';
            replyContent += `\n${index + 1}. ${source.title || '文档'}${scoreText}`;
          });
        }

        if (data.totalTime) {
          replyContent += `\n\n_耗时 ${data.totalTime}ms_`;
        }

        onSuccess([replyContent]);
      } catch (error: any) {
        onError(new Error(error.response?.data?.message || error.message || '查询失败'));
      }
    },
  });

  const { messages, onRequest } = useXChat({ agent });

  return (
    <Layout style={{ height: '100vh' }}>
      <Header style={{
        background: '#fff',
        padding: '0 24px',
        borderBottom: '1px solid #f0f0f0',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div>
          <Title level={3} style={{ margin: '16px 0 0', color: '#1890ff' }}>
            Enterprise RAG Agent
          </Title>
          <Paragraph style={{ margin: 0, color: '#666' }}>
            企业级智能问答 — Java 21 + Spring AI + LangChain4j
          </Paragraph>
        </div>
        <Space>
          <Tag color="blue">知识库文档: {docCount}</Tag>
          <Upload
            accept=".txt,.md,.pdf,.doc,.docx"
            showUploadList={false}
            beforeUpload={(file) => {
              handleUpload(file);
              return false;
            }}
          >
            <button
              type="button"
              disabled={uploading}
              style={{
                border: '1px solid #1890ff',
                background: '#fff',
                color: '#1890ff',
                borderRadius: 6,
                padding: '4px 12px',
                cursor: uploading ? 'not-allowed' : 'pointer'
              }}
            >
              <UploadOutlined /> {uploading ? '上传中...' : '上传文档'}
            </button>
          </Upload>
        </Space>
      </Header>

      <Content style={{
        padding: '24px',
        background: '#f5f5f5',
        display: 'flex',
        justifyContent: 'center'
      }}>
        <Card
          style={{
            width: '100%',
            maxWidth: 1000,
            height: 'calc(100vh - 150px)',
            display: 'flex',
            flexDirection: 'column'
          }}
          styles={{
            body: {
              flex: 1,
              overflow: 'hidden',
              padding: 0
            }
          }}
        >
          <div style={{
            height: '100%',
            display: 'flex',
            flexDirection: 'column',
            padding: '16px'
          }}>
            <div style={{ flex: 1, overflowY: 'auto', marginBottom: '16px' }}>
              <Bubble.List
                items={messages.map(({ id, message: msg, status }) => ({
                  key: id,
                  role: status === 'local' ? 'local' : 'ai',
                  content: msg,
                }))}
              />
            </div>

            <Sender
              value={content}
              onChange={setContent}
              onSubmit={(value) => {
                onRequest(value);
                setContent('');
              }}
              placeholder="请输入您的问题..."
            />
          </div>
        </Card>
      </Content>
    </Layout>
  );
}

export default App;
