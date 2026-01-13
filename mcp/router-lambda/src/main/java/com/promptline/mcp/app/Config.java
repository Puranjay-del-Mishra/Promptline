package com.promptline.mcp.app;

public record Config(
        String githubToken,
        String repoOwner,
        String repoName,

        String configBranchLive,     // "config/live"
        String s3Bucket,
        String s3RuntimePrefix,      // "runtime/"
        String backendNotifyUrl,     // "https://.../internal/config-updated"
        String internalToken         // shared token for both MCP + backend
) {
    public static Config fromEnv() {
        return new Config(
                env("GITHUB_TOKEN"),
                env("REPO_OWNER"),
                env("REPO_NAME"),

                envOr("CONFIG_BRANCH_LIVE", "config/live"),
                env("S3_BUCKET"),
                envOr("S3_RUNTIME_PREFIX", "runtime/"),
                env("BACKEND_NOTIFY_URL"),
                env("MCP_INTERNAL_API_KEY")
        );
    }

    private static String env(String k) {
        String v = System.getenv(k);
        return v == null ? "" : v.trim();
    }

    private static String envOr(String k, String dflt) {
        String v = System.getenv(k);
        if (v == null) return dflt;
        v = v.trim();
        return v.isBlank() ? dflt : v;
    }
}
