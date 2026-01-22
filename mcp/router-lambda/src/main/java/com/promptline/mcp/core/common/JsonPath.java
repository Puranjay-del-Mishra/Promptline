package com.promptline.mcp.core.common;

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

public final class JsonPath {
    private JsonPath() {}

    /**
     * Resolve a dot-path like "a.b.c" against a JsonNode.
     * - Supports object traversal only (no array indexing for now).
     * - Returns Optional.empty() if any segment is missing.
     */
    public static Optional<JsonNode> get(JsonNode root, String dotPath) {
        if (root == null || root.isNull()) return Optional.empty();
        if (dotPath == null || dotPath.isBlank()) return Optional.empty();

        JsonNode cur = root;
        String[] parts = dotPath.split("\\.");
        for (String p : parts) {
            if (p.isBlank()) return Optional.empty();
            if (!cur.isObject()) return Optional.empty();
            cur = cur.get(p);
            if (cur == null) return Optional.empty();
        }
        return Optional.of(cur);
    }
}
