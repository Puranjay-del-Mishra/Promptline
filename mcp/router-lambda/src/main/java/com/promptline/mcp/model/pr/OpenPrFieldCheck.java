package com.promptline.mcp.model.pr;

public record OpenPrFieldCheck(
        String target,      // "policy" | "ui"
        String path,        // dot-path
        Object desired,
        Object prValue,     // value as seen in PR head
        boolean matches
) {}
