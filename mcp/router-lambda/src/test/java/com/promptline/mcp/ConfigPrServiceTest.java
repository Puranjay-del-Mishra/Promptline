package com.promptline.mcp;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptline.mcp.core.git.GitProvider;
import com.promptline.mcp.core.pr.ConfigPrService;
import com.promptline.mcp.core.pr.OpenPrDecisionService;
import com.promptline.mcp.model.live.ConfigChange;
import com.promptline.mcp.model.live.ConfigCheckLiveRequest;
import com.promptline.mcp.model.pr.OpenPullRequest;

public class ConfigPrServiceTest {

    @Test
    void ensurePr_returnsExistingPr_whenAlreadyInOpenPr() throws Exception {
        ObjectMapper om = new ObjectMapper();

        FakeGit git = new FakeGit();
        String baseBranch = "config/live";

        // Open PR where head already contains the desired changes
        OpenPullRequest pr = new OpenPullRequest(
                12, "config: update live (agent)", "http://example/pr/12",
                "sha-12", "branch-12", baseBranch, "2026-01-16T00:00:00Z"
        );
        git.openPrs = List.of(pr);

        // PR head contains the updated files
        git.files.put("sha-12:config/ui.json", """
                {"rateLimit":{"rpm":123},"flag":true}
                """);
        git.files.put("sha-12:config/policy.json", """
                {"rules":{"mode":"strict"}}
                """);

        OpenPrDecisionService openPrDecision = new OpenPrDecisionService(git, om, baseBranch);
        ConfigPrService svc = new ConfigPrService(git, om, baseBranch, openPrDecision);

        ConfigCheckLiveRequest req = new ConfigCheckLiveRequest("live", List.of(
                new ConfigChange("ui", "set", "rateLimit.rpm", 123),
                new ConfigChange("policy", "set", "rules.mode", "strict")
        ));

        var out = svc.ensurePr(req);

        assertEquals("ALREADY_IN_OPEN_PR", out.decision());
        assertNotNull(out.pr());
        assertEquals(12, out.pr().number());
        assertEquals("branch-12", out.headBranch());

        // Ensure Phase 2 did NOT run (no branch creation / commits / PR creation)
        assertEquals(0, git.getBranchHeadShaCalls);
        assertEquals(0, git.createBranchCalls);
        assertEquals(0, git.upsertCalls.size());
        assertEquals(0, git.createPrCalls);

        // Optional: if you want, assert applied checks list exists and size==2
        // (works regardless of the record component name)
        List<?> applied = recordListComponent(out, "checks", "applied", "fieldChecks");
        if (applied != null) assertEquals(2, applied.size());
    }

    @Test
    void ensurePr_createsNewPr_whenNoMatchingOpenPr() throws Exception {
        ObjectMapper om = new ObjectMapper();

        FakeGit git = new FakeGit();
        String baseBranch = "config/live";
        git.openPrs = List.of(); // no open PRs

        // Base branch head sha
        git.branchHeadSha.put(baseBranch, "base-sha");

        // Service reads files by SHA (baseSha), not by branch name
        git.files.put("base-sha:config/ui.json", """
                {"rateLimit":{"rpm":50},"flag":false}
                """);
        git.files.put("base-sha:config/policy.json", """
                {"rules":{"mode":"lenient"}}
                """);

        // What createPullRequest should return
        git.prToReturn = new OpenPullRequest(
                99, "config: update live (agent)", "http://example/pr/99",
                "head-sha", "IGNORED", baseBranch, Instant.now().toString()
        );

        OpenPrDecisionService openPrDecision = new OpenPrDecisionService(git, om, baseBranch);
        ConfigPrService svc = new ConfigPrService(git, om, baseBranch, openPrDecision);

        ConfigCheckLiveRequest req = new ConfigCheckLiveRequest("live", List.of(
                new ConfigChange("ui", "set", "rateLimit.rpm", 123),
                new ConfigChange("policy", "set", "rules.mode", "strict")
        ));

        var out = svc.ensurePr(req);

        assertEquals("CREATED_NEW_PR", out.decision());
        assertNotNull(out.pr());
        assertEquals(99, out.pr().number());
        assertNotNull(out.headBranch());
        assertTrue(out.headBranch().startsWith("config/agent/live/"));

        // Phase 2 git actions happened
        assertEquals(1, git.getBranchHeadShaCalls);
        assertEquals(1, git.createBranchCalls);
        assertEquals(2, git.upsertCalls.size());
        assertEquals(1, git.createPrCalls);

        // verify written content includes updated values
        String uiWritten = git.upsertCalls.stream().filter(c -> c.path.equals("config/ui.json")).findFirst().orElseThrow().content;
        String policyWritten = git.upsertCalls.stream().filter(c -> c.path.equals("config/policy.json")).findFirst().orElseThrow().content;

        assertTrue(uiWritten.contains("\"rpm\""));
        assertTrue(uiWritten.contains("123"));
        assertTrue(policyWritten.contains("\"mode\""));
        assertTrue(policyWritten.contains("strict"));

        // Optional applied checks list size==2 (if record has such a component)
        List<?> applied = recordListComponent(out, "checks", "applied", "fieldChecks");
        if (applied != null) assertEquals(2, applied.size());
    }

    /**
     * Tries to read a List<?> record component from a record without knowing its exact name.
     * Pass a few likely candidate names; returns null if none exist.
     */
    private static List<?> recordListComponent(Object record, String... candidateNames) {
        if (record == null) return null;
        try {
            if (!record.getClass().isRecord()) return null;

            for (String cand : candidateNames) {
                for (RecordComponent rc : record.getClass().getRecordComponents()) {
                    if (rc.getName().equals(cand) && List.class.isAssignableFrom(rc.getType())) {
                        Object v = rc.getAccessor().invoke(record);
                        if (v instanceof List<?> l) return l;
                    }
                }
            }

            // fallback: first List component if exactly one exists
            List<RecordComponent> listComps = new ArrayList<>();
            for (RecordComponent rc : record.getClass().getRecordComponents()) {
                if (List.class.isAssignableFrom(rc.getType())) listComps.add(rc);
            }
            if (listComps.size() == 1) {
                Object v = listComps.get(0).getAccessor().invoke(record);
                if (v instanceof List<?> l) return l;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // -----------------------
    // Fake GitProvider
    // -----------------------
    static final class FakeGit implements GitProvider {

        Map<String, String> files = new HashMap<>();
        Map<String, String> branchHeadSha = new HashMap<>();
        List<OpenPullRequest> openPrs = List.of();

        int getBranchHeadShaCalls = 0;
        int createBranchCalls = 0;

        static final class UpsertCall {
            final String branch, path, content, msg;
            UpsertCall(String branch, String path, String content, String msg) {
                this.branch = branch; this.path = path; this.content = content; this.msg = msg;
            }
        }
        final List<UpsertCall> upsertCalls = new ArrayList<>();

        int createPrCalls = 0;
        OpenPullRequest prToReturn;

        @Override
        public Optional<String> getFileText(String ref, String path) {
            return Optional.ofNullable(files.get(ref + ":" + path));
        }

        @Override
        public List<OpenPullRequest> listOpenPullRequests(String baseBranch) {
            return openPrs;
        }

        @Override
        public String name() {
            return "fake";
        }

        // ---- Phase 2 methods used by ConfigPrService ----
        public String getBranchHeadSha(String branch) {
            getBranchHeadShaCalls++;
            String sha = branchHeadSha.get(branch);
            if (sha == null) throw new RuntimeException("missing head sha for " + branch);
            return sha;
        }

        public void createBranch(String headBranch, String fromSha) {
            createBranchCalls++;
            // no-op; just recording the call is enough for this unit test
        }

        public void upsertTextFile(String branch, String path, String contentUtf8, String commitMessage) {
            upsertCalls.add(new UpsertCall(branch, path, contentUtf8, commitMessage));
            files.put(branch + ":" + path, contentUtf8);
        }

        public OpenPullRequest createPullRequest(String baseBranch, String headBranch, String title, String body) {
            createPrCalls++;
            if (prToReturn != null) {
                return new OpenPullRequest(
                        prToReturn.number(),
                        prToReturn.title(),
                        prToReturn.htmlUrl(),
                        prToReturn.headSha(),
                        headBranch,          // ensure headRef matches the branch used
                        baseBranch,
                        prToReturn.updatedAt()
                );
            }
            return new OpenPullRequest(1, title, "http://example/pr/1", "head-sha", headBranch, baseBranch, "now");
        }
    }
}
