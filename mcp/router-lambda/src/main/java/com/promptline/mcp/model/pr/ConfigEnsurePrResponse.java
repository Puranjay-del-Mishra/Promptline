package com.promptline.mcp.model.pr;

import java.util.List;

public record ConfigEnsurePrResponse(
        String env,
        String baseBranch,
        String decision,          // "ALREADY_IN_OPEN_PR" | "CREATED_NEW_PR"
        OpenPullRequest pr,       // existing matching PR OR newly created PR
        String headBranch,        // for created PR (or existing PR headRef)
        List<OpenPrFieldCheck> appliedChecks,
        String message
) {}
