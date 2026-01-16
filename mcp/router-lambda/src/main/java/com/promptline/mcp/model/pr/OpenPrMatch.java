package com.promptline.mcp.model.pr;

import java.util.List;

public record OpenPrMatch(
        OpenPullRequest pr,
        boolean allMatch,
        List<OpenPrFieldCheck> checks
) {}
