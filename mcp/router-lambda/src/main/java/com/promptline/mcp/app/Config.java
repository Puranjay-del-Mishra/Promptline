package com.promptline.mcp.app;

public record Config(
        // GitHub
        String githubToken,
        String repoOwner,
        String repoName,

        // Branches
        String configBranchLive,

        // S3 runtime publishing
        String s3Bucket,
        String s3RuntimePrefix,

        // Auth (single shared token across MCP<->Backend)
        String mcpInternalApiKey,   // the canonical source of truth
        String internalToken,       // kept for compatibility (RouterHandler uses this)

        // Backend notify (cache invalidation)
        String backendNotifyUrl,

        // Backend read (Phase 0 live-check)
        String backendPublicBaseUrl
) {
    public static Config fromEnv() {
        // One shared token.
        // We accept both keys for backwards compatibility, but MCP_INTERNAL_API_KEY wins.
        String shared = env("MCP_INTERNAL_API_KEY", env("INTERNAL_NOTIFY_TOKEN", ""));

        return new Config(
                env("GITHUB_TOKEN", ""),
                env("REPO_OWNER", ""),
                env("REPO_NAME", ""),

                env("CONFIG_BRANCH_LIVE", "config/live"),

                env("S3_BUCKET", ""),
                env("S3_RUNTIME_PREFIX", "runtime/"),

                shared,
                shared,

                env("BACKEND_NOTIFY_URL", ""),

                env("BACKEND_PUBLIC_BASE_URL", "")
        );
    }

    private static String env(String key, String def) {
        // Lambda uses env vars; unit tests can use System properties.
        String v = System.getenv(key);
        if (v == null) v = System.getProperty(key);
        if (v == null) return def;
        v = v.trim();
        return v.isEmpty() ? def : v;
    }
}
