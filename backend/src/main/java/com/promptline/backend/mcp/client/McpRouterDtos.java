package com.promptline.backend.mcp.client;

import java.util.List;

public final class McpRouterDtos {

    public record ConfigChange(
            String target,   // "policy" | "ui"
            String op,       // "set"
            String path,     // dot path
            Object value
    ) {}

    public record ConfigCheckLiveRequest(
            String env,
            List<ConfigChange> changes
    ) {}
}
