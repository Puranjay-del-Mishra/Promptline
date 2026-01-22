package com.promptline.backend.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "promptline.mcp")
public class PromptlineMcpProperties {

    /**
     * Example:
     * https://702s1q2eid.execute-api.us-east-1.amazonaws.com
     */
    private String baseUrl;

    /**
     * Value sent as:
     * X-Internal-Token: <internalToken>
     */
    private String internalToken;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getInternalToken() { return internalToken; }
    public void setInternalToken(String internalToken) { this.internalToken = internalToken; }
}
