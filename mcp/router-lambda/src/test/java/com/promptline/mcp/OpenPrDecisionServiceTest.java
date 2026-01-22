package com.promptline.mcp;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptline.mcp.core.git.GitProvider;
import com.promptline.mcp.core.pr.OpenPrDecisionService;
import com.promptline.mcp.model.live.ConfigChange;
import com.promptline.mcp.model.live.ConfigCheckLiveRequest;
import com.promptline.mcp.model.pr.OpenPullRequest;

public class OpenPrDecisionServiceTest {

    @Test
    void alreadyInOpenPr_whenAllFieldsMatch() throws Exception {
        ObjectMapper om = new ObjectMapper();

        FakeGit git = new FakeGit(
                List.of(new OpenPullRequest(
                        12, "config: update live ui", "http://example/pr/12",
                        "sha-12", "branch-12", "config/live", "2026-01-16T00:00:00Z"
                )),
                Map.of(
                        "sha-12:config/ui.json", """
                                {"rateLimit":{"rpm":123},"flag":true}
                                """,
                        "sha-12:config/policy.json", """
                                {"rules":{"mode":"strict"}}
                                """
                )
        );

        OpenPrDecisionService svc = new OpenPrDecisionService(git, om, "config/live");

        var req = new ConfigCheckLiveRequest("live", List.of(
                new ConfigChange("ui", "set", "rateLimit.rpm", 123),
                new ConfigChange("policy", "set", "rules.mode", "strict")
        ));

        var resp = svc.checkOpenPr(req);

        assertEquals("ALREADY_IN_OPEN_PR", resp.decision());
        assertEquals(1, resp.matches().size());
        assertTrue(resp.matches().get(0).allMatch());
        assertTrue(resp.matches().stream().anyMatch(m -> m.allMatch()));
    }

    @Test
    void needsNewPr_whenNoOpenPrMatchesAllFields() throws Exception {
        ObjectMapper om = new ObjectMapper();

        FakeGit git = new FakeGit(
                List.of(new OpenPullRequest(
                        9, "some other pr", "http://example/pr/9",
                        "sha-9", "branch-9", "config/live", "2026-01-16T00:00:00Z"
                )),
                Map.of(
                        "sha-9:config/ui.json", """
                                {"rateLimit":{"rpm":50}}
                                """,
                        "sha-9:config/policy.json", """
                                {"rules":{"mode":"lenient"}}
                                """
                )
        );

        OpenPrDecisionService svc = new OpenPrDecisionService(git, om, "config/live");

        var req = new ConfigCheckLiveRequest("live", List.of(
                new ConfigChange("ui", "set", "rateLimit.rpm", 123),
                new ConfigChange("policy", "set", "rules.mode", "strict")
        ));

        var resp = svc.checkOpenPr(req);

        assertEquals("NEEDS_NEW_PR", resp.decision());

        // In your service: matches[] only contains PRs that fully match.
        // So if decision is NEEDS_NEW_PR, matches should be empty.
        assertTrue(resp.matches().isEmpty());
    }

    static final class FakeGit implements GitProvider {
        private final List<OpenPullRequest> prs;
        private final Map<String, String> files;

        FakeGit(List<OpenPullRequest> prs, Map<String, String> files) {
            this.prs = prs;
            this.files = files;
        }

        @Override
        public Optional<String> getFileText(String ref, String path) {
            return Optional.ofNullable(files.get(ref + ":" + path));
        }

        @Override
        public List<OpenPullRequest> listOpenPullRequests(String baseBranch) {
            return prs;
        }

        @Override
        public String name() {
            return "fake";
        }
    }
}
