package com.promptline.backend.llm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PromptlineLlmProperties.class)
public class LlmConfig {

    @Bean
    @ConditionalOnProperty(name = "promptline.llm.provider", havingValue = "openrouter")
    public LlmClient openRouterLlmClient(PromptlineLlmProperties props, RestClient.Builder builder) {
        return new OpenRouterLlmClient(props, builder);
    }

    @Bean
    @ConditionalOnMissingBean(LlmClient.class)
    public LlmClient noopLlmClient() {
        return new NoopLlmClient();
    }
}
