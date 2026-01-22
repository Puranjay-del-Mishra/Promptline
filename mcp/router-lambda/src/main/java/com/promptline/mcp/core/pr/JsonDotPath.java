package com.promptline.mcp.core.pr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.promptline.mcp.util.ApiException;

public final class JsonDotPath {
    private JsonDotPath() {}

    public static void set(ObjectMapper om, ObjectNode root, String dotPath, Object value) {
        if (root == null) throw new ApiException(500, "root json is null");
        if (dotPath == null || dotPath.isBlank()) throw new ApiException(400, "path is required");

        String[] parts = dotPath.split("\\.");
        ObjectNode cur = root;

        for (int i = 0; i < parts.length - 1; i++) {
            String k = parts[i].trim();
            if (k.isEmpty()) throw new ApiException(400, "invalid path: " + dotPath);

            JsonNode next = cur.get(k);
            if (next == null || next.isNull()) {
                ObjectNode created = om.createObjectNode();
                cur.set(k, created);
                cur = created;
                continue;
            }
            if (!next.isObject()) {
                // We could overwrite, but that’s usually surprising; fail loud.
                throw new ApiException(400, "path segment is not an object: " + k + " in " + dotPath);
            }
            cur = (ObjectNode) next;
        }

        String leaf = parts[parts.length - 1].trim();
        if (leaf.isEmpty()) throw new ApiException(400, "invalid path: " + dotPath);

        JsonNode leafNode = (value == null) ? om.nullNode() : om.valueToTree(value);
        cur.set(leaf, leafNode);
    }
}
