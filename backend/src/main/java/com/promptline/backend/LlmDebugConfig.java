package com.promptline.backend;

import com.promptline.backend.llm.LlmClient;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class LlmDebugConfig {

    @Bean
    ApplicationRunner llmDebug(Environment env, ApplicationContext ctx) {
        return args -> {
            System.out.println("prop promptline.llm.provider = " + env.getProperty("promptline.llm.provider"));
            System.out.println("prop llm.provider            = " + env.getProperty("llm.provider"));
            System.out.println("env  PROMPTLINE_LLM_PROVIDER = " + env.getProperty("PROMPTLINE_LLM_PROVIDER"));

            System.out.println("beans(LlmClient)             = " + Arrays.toString(ctx.getBeanNamesForType(LlmClient.class)));
        };
    }
}
