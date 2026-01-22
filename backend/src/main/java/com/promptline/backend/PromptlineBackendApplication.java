package com.promptline.backend;

import com.promptline.backend.llm.PromptlineLlmProperties;
import com.promptline.backend.mcp.PromptlineMcpProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        PromptlineLlmProperties.class,
        PromptlineMcpProperties.class
})
public class PromptlineBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(PromptlineBackendApplication.class, args);
    }
}
