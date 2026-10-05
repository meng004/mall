package com.macro.mall.portal.llm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;

@Configuration
public class LlmConfiguration {
    @Bean
    public LlmClient llmClient(
        @Value("${mall.llm.provider:${LLM_PROVIDER:openai-compatible}}") String provider,
        @Value("${mall.llm.base-url:${LLM_BASE_URL:}}") String baseUrl,
        @Value("${mall.llm.model:${LLM_MODEL:}}") String model,
        @Value("${mall.llm.api-key:${LLM_API_KEY:}}") String apiKey,
        @Value("${mall.llm.access-token:${LLM_ACCESS_TOKEN:}}") String accessToken) {
        if ("cursor".equals(provider))
            return new CursorCliLlmClient(model.isBlank() ? "grok-4.7-high-fast" : model,Duration.ofSeconds(60));
        if (!"openai-compatible".equals(provider)) throw new IllegalArgumentException("Unknown LLM provider");
        if (baseUrl.isBlank() || model.isBlank() || apiKey.isBlank() && accessToken.isBlank())
            return (system,input) -> {throw new IllegalStateException("LLM is not configured");};
        return new LangChain4jLlmClient(baseUrl,model,apiKey,accessToken,Duration.ofSeconds(20));
    }
}
