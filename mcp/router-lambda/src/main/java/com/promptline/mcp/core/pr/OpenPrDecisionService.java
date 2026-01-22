package com.promptline.mcp.core.pr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptline.mcp.core.git.GitProvider;
import com.promptline.mcp.model.live.ConfigChange;
import com.promptline.mcp.model.live.ConfigCheckLiveRequest;
import com.promptline.mcp.model.pr.ConfigCheckOpenPrResponse;
import com.promptline.mcp.model.pr.OpenPrFieldCheck;
import com.promptline.mcp.model.pr.OpenPrMatch;
import com.promptline.mcp.model.pr.OpenPullRequest;
import com.promptline.mcp.util.ApiException;

public final class OpenPrDecisionService {

    public enum Decision { ALREADY_IN_OPEN_PR, NEEDS_NEW_PR }

    private static final String POLICY_PATH = "config/policy.json";
    private static final String UI_PATH = "config/ui.json";

    private final GitProvider git;
    private final ObjectMapper om;
    private final String baseBranch;

    public OpenPrDecisionService(GitProvider git, ObjectMapper om, String baseBranch) {
        this.git = Objects.requireNonNull(git, "git");
        this.om = Objects.requireNonNull(om, "om");
        this.baseBranch = (baseBranch == null) ? "" : baseBranch.trim();
    }

    /**
     * Phase 1:
     * - Look at all open PRs targeting baseBranch (config/live)
     * - For each PR, check whether requested changes already exist in PR head config files
     * - If at least one PR fully matches all requested changes -> ALREADY_IN_OPEN_PR
     *
     * IMPORTANT: This keeps existing behavior:
     * - Only returns matches that are full matches (all requested fields match)
     * - Does not break record shapes or response fields
     */
    public ConfigCheckOpenPrResponse checkOpenPr(ConfigCheckLiveRequest req) throws Exception {
        if (req == null) throw new ApiException(400, "body is required");
        if (req.changes() == null || req.changes().isEmpty()) throw new ApiException(400, "changes[] is required");
        if (baseBranch.isBlank()) throw new ApiException(500, "CONFIG_BRANCH_LIVE is not set");

        String env = (req.env() == null || req.env().isBlank()) ? "live" : req.env().trim();
        List<OpenPullRequest> open = git.listOpenPullRequests(baseBranch);

        List<OpenPrMatch> matches = new ArrayList<>();

        for (OpenPullRequest pr : open) {
            if (pr == null) continue;

            // Keep your existing strict filter
            if (!baseBranch.equals(pr.baseRef())) continue;

            JsonNode policy = null;
            JsonNode ui = null;

            List<OpenPrFieldCheck> checks = new ArrayList<>();

            for (ConfigChange c : req.changes()) {
                if (c == null) continue;

                String target = safeLower(c.target());
                String op = safeLower(c.op());
                String path = (c.path() == null) ? "" : c.path().trim();

                if (!"set".equals(op)) throw new ApiException(400, "unsupported op: " + c.op());
                if (!"policy".equals(target) && !"ui".equals(target)) throw new ApiException(400, "unsupported target: " + c.target());
                if (path.isBlank()) throw new ApiException(400, "path is required");

                if ("policy".equals(target)) {
                    if (policy == null) policy = readJsonAtRef(pr.headSha(), POLICY_PATH);
                    checks.add(checkOne("policy", policy, path, c.value()));
                } else {
                    if (ui == null) ui = readJsonAtRef(pr.headSha(), UI_PATH);
                    checks.add(checkOne("ui", ui, path, c.value()));
                }
            }

            boolean allMatch = checks.stream().allMatch(OpenPrFieldCheck::matches);

            // Preserve existing behavior: only include fully matching PRs
            if (allMatch) {
                matches.add(new OpenPrMatch(pr, true, checks));
            }
        }

        Decision decision = matches.isEmpty() ? Decision.NEEDS_NEW_PR : Decision.ALREADY_IN_OPEN_PR;

        String msg = matches.isEmpty()
                ? "No open PR already contains the requested config change(s). Agent should raise a new PR."
                : "Requested config change(s) already exist in an open PR. Agent should link the PR instead of opening a duplicate.";

        return new ConfigCheckOpenPrResponse(
                env,
                baseBranch,
                decision.name(),
                open.size(),
                matches.size(),
                matches,
                msg
        );
    }

    private JsonNode readJsonAtRef(String ref, String path) throws Exception {
        if (ref == null || ref.isBlank()) return null;
        Optional<String> raw = git.getFileText(ref, path);
        if (raw.isEmpty()) return null;
        String s = raw.get();
        if (s == null || s.isBlank()) return null;
        return om.readTree(s);
    }

    /**
     * Extended safely:
     * - Compare values structurally using JsonNode equality.
     * - Still returns OpenPrFieldCheck(target, path, desiredRaw, prValue, matches)
     */
    private OpenPrFieldCheck checkOne(String target, JsonNode root, String dotPath, Object desiredRaw) {
        JsonNode actualNode = getByDotPath(root, dotPath);

        Object prValue = toPlainJava(actualNode);

        boolean matches = valuesEqualJson(actualNode, desiredRaw);

        return new OpenPrFieldCheck(target, dotPath, desiredRaw, prValue, matches);
    }

    private static JsonNode getByDotPath(JsonNode root, String dotPath) {
        if (root == null || dotPath == null || dotPath.isBlank()) return null;

        String[] parts = dotPath.split("\\.");
        JsonNode cur = root;

        for (String p : parts) {
            if (cur == null) return null;
            if (p == null || p.isBlank()) return null;
            cur = cur.get(p);
        }
        return cur;
    }

    private Object toPlainJava(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        if (n.isTextual()) return n.asText();
        if (n.isBoolean()) return n.asBoolean();
        if (n.isIntegralNumber()) return n.asLong();
        if (n.isFloatingPointNumber()) return n.asDouble();

        // SAFER than n.toString(): preserve structure as Map/List for callers
        // This does not affect matching logic; it's only for response display.
        return om.convertValue(n, Object.class);
    }

    /**
     * Compare actual JSON value vs desiredRaw in a stable way:
     * - Numbers: compare as doubles (so 1 == 1.0)
     * - Everything else: compare JsonNode structural equality
     */
    private boolean valuesEqualJson(JsonNode actualNode, Object desiredRaw) {
        if (actualNode == null || actualNode.isMissingNode() || actualNode.isNull()) {
            return desiredRaw == null;
        }

        // If desiredRaw is null but node isn't null -> mismatch
        if (desiredRaw == null) return false;

        // Special-case numbers to avoid 1 vs 1.0 surprises
        if (desiredRaw instanceof Number dn && actualNode.isNumber()) {
            double a = actualNode.asDouble();
            double b = dn.doubleValue();
            return Double.compare(a, b) == 0;
        }

        // Structural compare for everything else
        JsonNode desiredNode = om.valueToTree(desiredRaw);
        return desiredNode.equals(actualNode);
    }

    private static String safeLower(String s) {
        return (s == null) ? "" : s.trim().toLowerCase();
    }
}
