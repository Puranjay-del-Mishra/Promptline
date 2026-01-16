package com.promptline.mcp.model.live;

import java.util.List;

public record ConfigCheckLiveResponse(
        String env,
        String decision,           // "NO_CHANGE_NEEDED" | "NEEDS_PR"
        boolean allMatch,
        List<FieldCheck> checks,
        String message
) {}
