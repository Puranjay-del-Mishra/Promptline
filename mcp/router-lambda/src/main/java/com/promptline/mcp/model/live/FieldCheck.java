package com.promptline.mcp.model.live;

public record FieldCheck(
        String target,
        String path,
        Object desired,
        Object live,
        boolean matches
) {}
