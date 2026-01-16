package com.promptline.mcp.core.pr;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class OpenPrDecisionService {

    public enum Decision { ALREADY_IN_OPEN_PR, NEEDS_NEW_PR }

    private final GitProvider git;
    private final ObjectMapper om;
    private final String baseBranch;

    public OpenPrDecisionService(GitProvider git, ObjectMapper om, String baseBranch) {
        this.git = Objects.requireNonNull(git, "git");
        this.om = Objects.requireNonNull(om, "om");
        this.baseBranch = (baseBranch == null) ? "" : baseBranch.trim();
    }

    public ConfigCheckOpenPrResponse checkOpenPr(ConfigCheckLiveRequest req) throws Exception {
        if (req == null) throw new ApiException(400, "body is required");
        if (req.changes() == null || req.changes().isEmpty()) throw new ApiException(400, "changes[] is required");
        if (baseBranch.isBlank()) throw new ApiException(500, "CONFIG_BRANCH_LIVE is not set");

        String env = (req.env() == null || req.env().isBlank()) ? "live" : req.env().trim();
        List<OpenPullRequest> open = git.listOpenPullRequests(baseBranch);

        List<OpenPrMatch> matches = new ArrayList<>();

        for (OpenPullRequest pr : open) {
            if (pr == null) continue;
            if (!baseBranch.equals(pr.baseRef())) continue;

            List<OpenPrFieldCheck> checks = new ArrayList<>();
            JsonNode policy = null;
            JsonNode ui = null;

            for (ConfigChange c : req.changes()) {
                if (c == null) continue;

                String target = safeLower(c.target());
                String op = safeLower(c.op());
                String path = (c.path() == null) ? "" : c.path().trim();

                if (!"set".equals(op)) throw new ApiException(400, "unsupported op: " + c.op());
                if (!"policy".equals(target) && !"ui".equals(target)) throw new ApiException(400, "unsupported target: " + c.target());
                if (path.isBlank()) throw new ApiException(400, "path is required");

                if ("policy".equals(target)) {
                    if (policy == null) policy = readJsonAtRef(pr.headSha(), "config/policy.json");
                    checks.add(checkOne("policy", policy, path, c.value()));
                } else {
                    if (ui == null) ui = readJsonAtRef(pr.headSha(), "config/ui.json");
                    checks.add(checkOne("ui", ui, path, c.value()));
                }
            }

            boolean allMatch = checks.stream().allMatch(OpenPrFieldCheck::matches);
            if (allMatch) matches.add(new OpenPrMatch(pr, true, checks));
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

    private OpenPrFieldCheck checkOne(String target, JsonNode root, String dotPath, Object desiredRaw) {
        JsonNode n = getByDotPath(root, dotPath);
        Object prValue = toPlainJava(n);
        boolean matches = valuesEqual(prValue, desiredRaw);
        return new OpenPrFieldCheck(target, dotPath, desiredRaw, prValue, matches);
    }

    private static JsonNode getByDotPath(JsonNode root, String dotPath) {
        if (root == null) return null;
        String[] parts = dotPath.split("\\.");
        JsonNode cur = root;
        for (String p : parts) {
            if (cur == null) return null;
            cur = cur.get(p);
        }
        return cur;
    }

    private static Object toPlainJava(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        if (n.isTextual()) return n.asText();
        if (n.isBoolean()) return n.asBoolean();
        if (n.isIntegralNumber()) return n.asLong();
        if (n.isFloatingPointNumber()) return n.asDouble();
        return n.toString();
    }

    private static boolean valuesEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a instanceof Number an && b instanceof Number bn) {
            return Double.compare(an.doubleValue(), bn.doubleValue()) == 0;
        }
        return Objects.equals(a, b);
    }

    private static String safeLower(String s) {
        return (s == null) ? "" : s.trim().toLowerCase();
    }
}
