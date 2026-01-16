package com.promptline.mcp.core.git;

import com.promptline.mcp.model.pr.OpenPullRequest;

import java.util.List;
import java.util.Optional;

public interface GitProvider {
    Optional<String> getFileText(String ref, String path);

    List<OpenPullRequest> listOpenPullRequests(String baseBranch);

    String name();
}
