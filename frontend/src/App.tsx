import { useState } from 'react'
import { Bubble, useXChat, useXChatHelper } from '@ant-design/x';
import type { GetProp } from 'antd';
import { Card, Typography, Layout, message } from 'antd';
import axios from 'axios';
import './App.css';

const { Title, Paragraph } = Typography;
const { Header, Content } = Layout;

type MessageType = GetProp<typeof Bubble, 'message'>;

function App() {
  const [loading, setLoading] = useState(false);

  // 使用 Ant Design X 的 Chat 组件
  const [agent] = useXChatHelper({
    async agent({ message }) {
      setLoading(true);
      try {
        // 调用后端 API
        const response = await axios.post('/api/v1/rag/query', {
          question: message.content,
          retrievalParams: {
            topK: 5,
            strategy: 'HYBRID',
            enableRerank: true
          },
          generationParams: {
            model: 'gpt-4-turbo',
            temperature: 0.7
          }
        });

        const data = response.data;
        
        // 构建回复消息
        let replyContent = data.answer;
        
        // 添加来源引用
        if (data.sources && data.sources.length > 0) {
          replyContent += '\n\n---\n**参考来源：**\n';
          data.sources.forEach((source: any, index: number) => {
            replyContent += `\n${index + 1}. ${source.title || '文档'} (相似度: ${(source.score * 100).toFixed(1)}%)`;
          });
        }
        
        return {
          content: replyContent,
          role: 'assistant'
        };
      } catch (error: any) {
        message.error('查询失败：' + (error.response?.data?.message || error.message));
        return {
          content: '抱歉，查询过程中出现错误，请稍后重试。',
          role: 'assistant'
        };
      } finally {
        setLoading(false);
      }
    }
  });

  const { messages, onRequest } = useXChat({
    agent
  });

  return (
    <Layout style={{ height: '100vh' }}>
      <Header style={{ 
        background: '#fff', 
        padding: '0 24px',
        borderBottom: '1px solid #f0f0f0'
      }}>
        <Title level={3} style={{ margin: '16px 0', color: '#1890ff' }}>
          🦞 Enterprise RAG Agent
        </Title>
        <Paragraph style={{ margin: 0, color: '#666' }}>
          企业级智能问答系统 - Java 21 + SpringAI + LangChain4j
        </Paragraph>
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
          bodyStyle={{ 
            flex: 1, 
            overflow: 'hidden',
            padding: 0
          }}
        >
          <div style={{ 
            height: '100%', 
            display: 'flex', 
            flexDirection: 'column',
            padding: '16px'
          }}>
            {/* 使用 Ant Design X 的 Bubble 组件 */}
            <div style={{ flex: 1, overflowY: 'auto', marginBottom: '16px' }}>
              {messages.map((msg, index) => (
                <Bubble
                  key={index}
                  placement={msg.role === 'user' ? 'end' : 'start'}
                  message={msg as MessageType}
                  styles={{
                    content: {
                      backgroundColor: msg.role === 'user' ? '#1890ff' : '#f0f0f0',
                      color: msg.role === 'user' ? '#fff' : '#000'
                    }
                  }}
                />
              ))}
              {loading && (
                <Bubble
                  placement="start"
                  message={{ content: '思考中...', role: 'assistant' }}
                  styles={{
                    content: {
                      backgroundColor: '#f0f0f0',
                      fontStyle: 'italic'
                    }
                  }}
                />
              )}
            </div>

            {/* 输入框 */}
            <div style={{ display: 'flex', gap: '8px' }}>
              <input
                style={{
                  flex: 1,
                  padding: '12px 16px',
                  border: '1px solid #d9d9d9',
                  borderRadius: '8px',
                  fontSize: '14px',
                  outline: 'none'
                }}
                placeholder="请输入您的问题..."
                onKeyPress={(e) => {
                  if (e.key === 'Enter' && e.currentTarget.value.trim()) {
                    onRequest({ content: e.currentTarget.value, role: 'user' });
                    e.currentTarget.value = '';
                  }
                }}
              />
              <button
                style={{
                  padding: '12px 24px',
                  background: '#1890ff',
                  color: '#fff',
                  border: 'none',
                  borderRadius: '8px',
                  cursor: 'pointer',
                  fontSize: '14px'
                }}
                onClick={() => {
                  const input = document.querySelector('input') as HTMLInputElement;
                  if (input && input.value.trim()) {
                    onRequest({ content: input.value, role: 'user' });
                    input.value = '';
                  }
                }}
              >
                发送
              </button>
            </div>
          </div>
        </Card>
      </Content>
    </Layout>
  );
}

export default App;