package com.promptline.mcp.model.config;

import java.util.List;

public record PublishCanonicalResponse(
        String ref,
        List<String> updated,
        String bucket,
        String uiKey,
        String policyKey
) {}
