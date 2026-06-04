package com.enterprise.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Enterprise RAG Engine - 企业级智能问答系统
 * 
 * 单一技术栈：Java 21 + SpringAI + LangChain4j
 * 
 * 核心能力：
 * 1. 文档解析与智能切片
 * 2. 向量嵌入生成与存储
 * 3. 混合检索（向量 + 关键词）
 * 4. 重排序优化
 * 5. 对话生成
 * 
 * @author jack.zhu
 * @version 1.0.0
 */
@SpringBootApplication
public class RagEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagEngineApplication.class, args);
        System.out.println("\n✅ Enterprise RAG Engine 启动成功！");
        System.out.println("📚 单一Java技术栈，精准定位，持续发力！");
        System.out.println("🎯 目标：成为市场上最稀缺的Java+AI复合型人才\n");
    }
}