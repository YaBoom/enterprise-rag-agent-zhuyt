package com.enterprise.rag.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 核心配置
 *
 * @author jack.zhu
 */
@Configuration
public class SpringAiConfig {

    /**
     * 会话记忆存储：按 conversationId 隔离会话，滑动窗口保留最近 N 条消息，
     * 由 MessageChatMemoryAdvisor 在问答时自动写入/回传历史
     */
    @Bean
    public ChatMemory chatMemory(RagProperties ragProperties) {
        return MessageWindowChatMemory.builder()
            .maxMessages(ragProperties.getChat().getMaxMessages())
            .build();
    }
}