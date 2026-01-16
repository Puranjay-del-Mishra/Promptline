package com.promptline.mcp.model.pr;

import java.util.List;

public record ConfigCheckOpenPrResponse(
        String env,
        String baseBranch,
        String decision,            // "ALREADY_IN_OPEN_PR" | "NEEDS_NEW_PR"
        int openPrCount,
        int matchingPrCount,
        List<OpenPrMatch> matches,
        String message
) {}
