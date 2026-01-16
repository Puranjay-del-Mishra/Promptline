package com.promptline.mcp.core.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptline.mcp.model.live.ConfigChange;
import com.promptline.mcp.model.live.ConfigCheckLiveRequest;
import com.promptline.mcp.model.live.ConfigCheckLiveResponse;
import com.promptline.mcp.model.live.FieldCheck;
import com.promptline.mcp.util.ApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ConfigDecisionService {

    public enum Decision { NO_CHANGE_NEEDED, NEEDS_PR }

    private final BackendConfigClient backend;
    private final ObjectMapper om;

    public ConfigDecisionService(BackendConfigClient backend, ObjectMapper om) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.om = Objects.requireNonNull(om, "om");
    }

    public ConfigCheckLiveResponse checkLive(ConfigCheckLiveRequest req) throws Exception {
        if (req == null) throw new ApiException(400, "body is required");
        if (req.changes() == null || req.changes().isEmpty()) throw new ApiException(400, "changes[] is required");

        String env = (req.env() == null || req.env().isBlank()) ? "live" : req.env().trim();

        // Fetch live blobs once per target
        String policyRaw = null;
        String uiRaw = null;
        JsonNode policy = null;
        JsonNode ui = null;

        List<FieldCheck> checks = new ArrayList<>();

        for (ConfigChange c : req.changes()) {
            if (c == null) continue;

            String target = safeLower(c.target());
            String op = safeLower(c.op());
            String path = (c.path() == null) ? "" : c.path().trim();

            if (!"set".equals(op)) throw new ApiException(400, "unsupported op: " + c.op());
            if (!"policy".equals(target) && !"ui".equals(target)) throw new ApiException(400, "unsupported target: " + c.target());
            if (path.isBlank()) throw new ApiException(400, "path is required");

            if ("policy".equals(target)) {
                if (policy == null) {
                    policyRaw = backend.getPolicyJson();
                    policy = om.readTree(policyRaw);
                }
                checks.add(checkOne("policy", policy, path, c.value()));
            } else {
                if (ui == null) {
                    uiRaw = backend.getUiJson();
                    ui = om.readTree(uiRaw);
                }
                checks.add(checkOne("ui", ui, path, c.value()));
            }
        }

        boolean allMatch = checks.stream().allMatch(FieldCheck::matches);
        Decision decision = allMatch ? Decision.NO_CHANGE_NEEDED : Decision.NEEDS_PR;

        String msg = allMatch
                ? "Already live (no change needed)"
                : "Not live yet (agent should raise a PR)";

        return new ConfigCheckLiveResponse(env, decision.name(), allMatch, checks, msg);
    }

    private FieldCheck checkOne(String target, JsonNode root, String dotPath, Object desiredRaw) {
        JsonNode liveNode = getByDotPath(root, dotPath);
        Object live = toPlainJava(liveNode);

        boolean matches = valuesEqual(live, desiredRaw);

        return new FieldCheck(target, dotPath, desiredRaw, live, matches);
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
        // for objects/arrays, return stringified JSON (good enough for PoC)
        return n.toString();
    }

    private static boolean valuesEqual(Object live, Object desired) {
        if (live == null && desired == null) return true;
        if (live == null || desired == null) return false;

        // numeric normalization
        if (live instanceof Number ln && desired instanceof Number dn) {
            // compare as doubles (PoC)
            return Double.compare(ln.doubleValue(), dn.doubleValue()) == 0;
        }

        return Objects.equals(live, desired);
    }

    private static String safeLower(String s) {
        return (s == null) ? "" : s.trim().toLowerCase();
    }
}
