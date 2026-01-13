package com.promptline.mcp.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

public final class InternalAuth {
    private InternalAuth() {}

    // Case-insensitive header lookup + constant-time compare
    public static boolean isAuthorized(Map<String, String> headers, String headerName, String expectedSecret) {
        if (expectedSecret == null || expectedSecret.isBlank()) return false; // secure-by-default
        if (headers == null || headers.isEmpty()) return false;

        String got = getHeaderIgnoreCase(headers, headerName);
        if (got == null || got.isBlank()) return false;

        return constantTimeEquals(got.trim(), expectedSecret.trim());
    }

    private static String getHeaderIgnoreCase(Map<String, String> headers, String name) {
        for (var e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(ab, bb);
    }
}
