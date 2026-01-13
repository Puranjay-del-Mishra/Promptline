package com.promptline.mcp.core.git;

import java.util.Optional;

public interface GitProvider {
    /**
     * Fetch a file from the canonical repo at a given ref (branch/sha).
     * For Phase 0/1 this can be unimplemented; returning Optional.empty() is fine.
     */
    Optional<String> getFileText(String ref, String path);

    /**
     * Health check / identity (useful for logs).
     */
    String name();
}
