package com.promptline.mcp.core.git;

import java.util.List;
import java.util.Optional;

import com.promptline.mcp.model.pr.OpenPullRequest;

public interface GitProvider {

    Optional<String> getFileText(String ref, String path);

    List<OpenPullRequest> listOpenPullRequests(String baseBranch);

    // -------- Phase 2 (writes) --------
    default String getBranchHeadSha(String branch) {
        throw new UnsupportedOperationException("getBranchHeadSha not implemented for " + name());
    }

    default void createBranch(String newBranch, String fromSha) {
        throw new UnsupportedOperationException("createBranch not implemented for " + name());
    }

    default void upsertTextFile(String branch, String path, String contentUtf8, String commitMessage) {
        throw new UnsupportedOperationException("upsertTextFile not implemented for " + name());
    }

    default OpenPullRequest createPullRequest(String baseBranch, String headBranch, String title, String body) {
        throw new UnsupportedOperationException("createPullRequest not implemented for " + name());
    }

    String name();
}
