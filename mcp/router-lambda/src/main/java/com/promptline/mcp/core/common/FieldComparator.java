package com.promptline.mcp.core.common;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptline.mcp.model.live.ConfigChange;
import com.promptline.mcp.model.pr.OpenPrFieldCheck;

public final class FieldComparator {
    private FieldComparator() {}

    public static List<OpenPrFieldCheck> compareDesiredVsJson(
            ObjectMapper om,
            List<ConfigChange> desiredChanges,
            JsonNode uiJson,
            JsonNode policyJson
    ) {
        List<OpenPrFieldCheck> out = new ArrayList<>();
        if (desiredChanges == null) return out;

        for (ConfigChange ch : desiredChanges) {
            if (ch == null) continue;

            String target = safe(ch.target());
            String path = safe(ch.path());
            Object desiredObj = ch.value();

            JsonNode desiredNode = om.valueToTree(desiredObj);

            JsonNode root = switch (target) {
                case "ui" -> uiJson;
                case "policy" -> policyJson;
                default -> null;
            };

            JsonNode prValueNode = JsonPath.get(root, path).orElse(null);
            boolean matches = (prValueNode != null) && prValueNode.equals(desiredNode);

            Object prValue = prValueNode == null ? null : toPlainJava(om, prValueNode);

            out.add(new OpenPrFieldCheck(
                    target,
                    path,
                    desiredObj,
                    prValue,
                    matches
            ));
        }

        return out;
    }

    private static Object toPlainJava(ObjectMapper om, JsonNode n) {
        if (n == null || n.isNull()) return null;
        if (n.isTextual()) return n.asText();
        if (n.isBoolean()) return n.asBoolean();
        if (n.isInt() || n.isLong()) return n.asLong();
        if (n.isFloat() || n.isDouble() || n.isBigDecimal()) return n.asDouble();
        // objects/arrays → map/list
        return om.convertValue(n, Object.class);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
