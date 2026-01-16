package com.promptline.mcp.app;

public record Config(
        // GitHub
        String githubToken,
        String repoOwner,
        String repoName,

        // Branches
        String configBranchLive,     // "config/live"

        // S3 runtime publishing
        String s3Bucket,
        String s3RuntimePrefix,      // "runtime/"

        // Backend notify (cache invalidation)
        String backendNotifyUrl,     // "https://.../internal/config-updated"

        // Shared internal token (RouterHandler auth header + backend notify header)
        // RouterHandler currently reads this as the expected value for x-mcp-internal-api-key
        String internalToken,

        // Backend read (Phase 0 live-check)
        String backendPublicBaseUrl
) {
    public static Config fromEnv() {
        return new Config(
                env("GITHUB_TOKEN", ""),
                env("REPO_OWNER", ""),
                env("REPO_NAME", ""),

                env("CONFIG_BRANCH_LIVE", "config/live"),

                env("S3_BUCKET", ""),
                env("S3_RUNTIME_PREFIX", "runtime/"),

                env("BACKEND_NOTIFY_URL", ""),

                // One token to rule them all 🧙‍♂️
                env("MCP_INTERNAL_API_KEY", env("INTERNAL_NOTIFY_TOKEN", "")),

                env("BACKEND_PUBLIC_BASE_URL", "")
        );
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        if (v == null) return def;
        v = v.trim();
        return v.isEmpty() ? def : v;
    }
}
