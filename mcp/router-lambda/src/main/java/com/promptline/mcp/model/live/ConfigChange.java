package com.promptline.mcp.model.live;

public record ConfigChange(
        String target,   // "policy" | "ui"
        String op,       // "set" (PoC)
        String path,     // dot-path e.g. "rateLimit.rpm"
        Object value
) {}
