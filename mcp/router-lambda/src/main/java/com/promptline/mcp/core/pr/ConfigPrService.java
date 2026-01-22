package com.promptline.mcp.core.pr;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.promptline.mcp.core.git.GitProvider;
import com.promptline.mcp.model.live.ConfigChange;
import com.promptline.mcp.model.live.ConfigCheckLiveRequest;
import com.promptline.mcp.model.pr.ConfigCheckOpenPrResponse;
import com.promptline.mcp.model.pr.ConfigEnsurePrResponse;
import com.promptline.mcp.model.pr.OpenPrFieldCheck;
import com.promptline.mcp.model.pr.OpenPrMatch;
import com.promptline.mcp.model.pr.OpenPullRequest;
import com.promptline.mcp.util.ApiException;

public final class ConfigPrService {

    private final GitProvider git;
    private final ObjectMapper om;
    private final String baseBranch;     // config/live
    private final OpenPrDecisionService openPrDecision;

    public ConfigPrService(GitProvider git, ObjectMapper om, String baseBranch, OpenPrDecisionService openPrDecision) {
        this.git = Objects.requireNonNull(git, "git");
        this.om = Objects.requireNonNull(om, "om");
        this.baseBranch = (baseBranch == null) ? "" : baseBranch.trim();
        this.openPrDecision = Objects.requireNonNull(openPrDecision, "openPrDecision");
    }

    public ConfigEnsurePrResponse ensurePr(ConfigCheckLiveRequest req) throws Exception {
        if (req == null) throw new ApiException(400, "body is required");
        if (req.changes() == null || req.changes().isEmpty()) throw new ApiException(400, "changes[] is required");
        if (baseBranch.isBlank()) throw new ApiException(500, "CONFIG_BRANCH_LIVE is not set");

        String env = (req.env() == null || req.env().isBlank()) ? "live" : req.env().trim();

        // 1) Phase 1 check: is it already in any open PR?
        ConfigCheckOpenPrResponse prCheck = openPrDecision.checkOpenPr(req);
        if ("ALREADY_IN_OPEN_PR".equals(prCheck.decision()) && prCheck.matches() != null && !prCheck.matches().isEmpty()) {
            OpenPrMatch best = prCheck.matches().get(0);
            return new ConfigEnsurePrResponse(
                    env,
                    baseBranch,
                    "ALREADY_IN_OPEN_PR",
                    best.pr(),
                    best.pr().headRef(),
                    best.checks(),
                    "Change already exists in an open PR; returning that PR."
            );
        }

        // 2) Create new branch from base
        String baseSha = git.getBranchHeadSha(baseBranch);

        String headBranch = "config/agent/" + env + "/" + Instant.now().toString().replace(":", "").replace(".", "");
        git.createBranch(headBranch, baseSha);

        // 3) Load base config files at baseSha
        ObjectNode ui = readObjectAtRef(baseSha, "config/ui.json");
        ObjectNode policy = readObjectAtRef(baseSha, "config/policy.json");

        if (ui == null) ui = om.createObjectNode();
        if (policy == null) policy = om.createObjectNode();

        // 4) Apply changes
        List<OpenPrFieldCheck> applied = new ArrayList<>();

        for (ConfigChange c : req.changes()) {
            if (c == null) continue;

            String target = safeLower(c.target());
            String op = safeLower(c.op());
            String path = (c.path() == null) ? "" : c.path().trim();

            if (!"set".equals(op)) throw new ApiException(400, "unsupported op: " + c.op());
            if (!"policy".equals(target) && !"ui".equals(target)) throw new ApiException(400, "unsupported target: " + c.target());
            if (path.isBlank()) throw new ApiException(400, "path is required");

            if ("ui".equals(target)) {
                JsonDotPath.set(om, ui, path, c.value());
                applied.add(new OpenPrFieldCheck("ui", path, c.value(), c.value(), true));
            } else {
                JsonDotPath.set(om, policy, path, c.value());
                applied.add(new OpenPrFieldCheck("policy", path, c.value(), c.value(), true));
            }
        }

        // 5) Commit updated files to new branch
        git.upsertTextFile(headBranch, "config/ui.json", om.writerWithDefaultPrettyPrinter().writeValueAsString(ui) + "\n",
                "config: update " + env + " ui (agent)");
        git.upsertTextFile(headBranch, "config/policy.json", om.writerWithDefaultPrettyPrinter().writeValueAsString(policy) + "\n",
                "config: update " + env + " policy (agent)");

        // 6) Open PR
        String title = "config: update " + env + " (agent)";
        String body = "Triggered by agent.\n\nBase: `" + baseBranch + "`\n\nChanges:\n" + summarize(req.changes());
        OpenPullRequest pr = git.createPullRequest(baseBranch, headBranch, title, body);

        return new ConfigEnsurePrResponse(
                env,
                baseBranch,
                "CREATED_NEW_PR",
                pr,
                headBranch,
                applied,
                "Created new PR with requested config changes."
        );
    }

    private ObjectNode readObjectAtRef(String ref, String path) throws Exception {
        Optional<String> raw = git.getFileText(ref, path);
        if (raw.isEmpty()) return null;
        String s = raw.get();
        if (s == null || s.isBlank()) return null;
        JsonNode n = om.readTree(s);
        if (n == null || n.isNull()) return null;
        if (!n.isObject()) throw new ApiException(400, "expected JSON object at " + path);
        return (ObjectNode) n;
    }

    private static String summarize(List<ConfigChange> changes) {
        StringBuilder sb = new StringBuilder();
        for (ConfigChange c : changes) {
            if (c == null) continue;
            sb.append("- ").append(c.target()).append(".").append(c.path()).append(" = ").append(String.valueOf(c.value())).append("\n");
        }
        return sb.toString();
    }

    private static String safeLower(String s) {
        return (s == null) ? "" : s.trim().toLowerCase();
    }
}
