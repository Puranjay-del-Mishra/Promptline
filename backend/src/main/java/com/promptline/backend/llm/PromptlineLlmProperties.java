package com.promptline.backend.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "promptline.llm")
public class PromptlineLlmProperties {

    private String provider = "noop";
    private OpenRouter openrouter = new OpenRouter();

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public OpenRouter getOpenrouter() { return openrouter; }
    public void setOpenrouter(OpenRouter openrouter) { this.openrouter = openrouter; }

    public static class OpenRouter {
        private String apiKey;
        private String baseUrl = "https://openrouter.ai/api/v1";
        private String fastModel;
        private String strongModel;
        private String appUrl;
        private String appName;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public String getFastModel() { return fastModel; }
        public void setFastModel(String fastModel) { this.fastModel = fastModel; }

        public String getStrongModel() { return strongModel; }
        public void setStrongModel(String strongModel) { this.strongModel = strongModel; }

        public String getAppUrl() { return appUrl; }
        public void setAppUrl(String appUrl) { this.appUrl = appUrl; }

        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }
    }
}
