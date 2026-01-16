package com.promptline.mcp.model.pr;

public record OpenPullRequest(
        int number,
        String title,
        String htmlUrl,
        String headSha,
        String headRef,
        String baseRef,
        String updatedAt
) {}
