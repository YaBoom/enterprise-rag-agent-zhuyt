package com.enterprise.rag.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Spring AI 默认 OkHttp/RestClient 读超时约 10s，推理模型（如 qwen3.7-plus）+RAG 上下文易超时。
 * 见 INC-013。
 */
@Configuration
public class AiHttpClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(3);

    @Bean
    @ConditionalOnMissingBean(RestClientCustomizer.class)
    public RestClientCustomizer aiRestClientCustomizer() {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(CONNECT_TIMEOUT)
            .withReadTimeout(READ_TIMEOUT);

        return builder -> builder.requestFactory(ClientHttpRequestFactories.get(settings));
    }
}
