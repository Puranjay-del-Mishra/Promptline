package com.promptline.backend.mcp;

import com.promptline.backend.mcp.client.McpRouterDtos.ConfigCheckLiveRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class McpRouterClient {

    private final RestClient rest;

    public McpRouterClient(PromptlineMcpProperties props, RestClient.Builder builder) {
        this.rest = builder
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Internal-Token", props.getInternalToken())
                .build();
    }

    public String checkLive(ConfigCheckLiveRequest req) {
        return rest.post().uri("/config/check-live").body(req).retrieve().body(String.class);
    }

    public String checkOpenPr(ConfigCheckLiveRequest req) {
        return rest.post().uri("/config/check-open-pr").body(req).retrieve().body(String.class);
    }

    public String ensurePr(ConfigCheckLiveRequest req) {
        return rest.post().uri("/config/ensure-pr").body(req).retrieve().body(String.class);
    }
}
