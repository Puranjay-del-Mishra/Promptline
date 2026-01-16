package com.promptline.mcp.app;

public record Config(
        // GitHub
        String githubToken,
        String repoOwner,
        String repoName,

        // Branches
<<<<<<< HEAD
        String configBranchLive,     // "config/live"

        // S3 runtime publishing
        String s3Bucket,
        String s3RuntimePrefix,      // "runtime/"

        // Backend notify (cache invalidation)
        String backendNotifyUrl,     // "https://.../internal/config-updated"

        // Shared internal token (RouterHandler auth header + backend notify header)
        // RouterHandler currently reads this as the expected value for x-mcp-internal-api-key
=======
        String configBranchLive,

        // S3 runtime publishing
        String s3Bucket,
        String s3RuntimePrefix,

        // Auth (internal)
        String mcpInternalApiKey,

        // Backend notify (cache invalidation)
        String backendNotifyUrl,
>>>>>>> c291eb3 (mcp: phase0 live-check + open PR detection plumbing)
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

<<<<<<< HEAD
                env("BACKEND_NOTIFY_URL", ""),

                // One token to rule them all 🧙‍♂️
=======
                env("MCP_INTERNAL_API_KEY", ""),

                env("BACKEND_NOTIFY_URL", ""),

>>>>>>> c291eb3 (mcp: phase0 live-check + open PR detection plumbing)
                env("MCP_INTERNAL_API_KEY", env("INTERNAL_NOTIFY_TOKEN", "")),

                env("BACKEND_PUBLIC_BASE_URL", "")
        );
    }

    private static String env(String key, String def) {
<<<<<<< HEAD
        String v = System.getenv(key);
=======
        // Prefer real env vars (Lambda), fall back to system properties (unit tests)
        String v = System.getenv(key);
        if (v == null) v = System.getProperty(key);

>>>>>>> c291eb3 (mcp: phase0 live-check + open PR detection plumbing)
        if (v == null) return def;
        v = v.trim();
        return v.isEmpty() ? def : v;
    }
}
