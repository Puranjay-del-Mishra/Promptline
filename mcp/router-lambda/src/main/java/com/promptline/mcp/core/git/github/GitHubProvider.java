package com.promptline.mcp.core.git.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptline.mcp.core.git.GitProvider;
import com.promptline.mcp.model.pr.OpenPullRequest;
import com.promptline.mcp.util.ApiException;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class GitHubProvider implements GitProvider {

    private final String owner;
    private final String repo;
    private final ObjectMapper om;
    private final GitHubHttp gh;

    public GitHubProvider(HttpClient http, String token, String owner, String repo, ObjectMapper om) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.repo = Objects.requireNonNull(repo, "repo");
        this.om = Objects.requireNonNull(om, "om");
        this.gh = new GitHubHttp(Objects.requireNonNull(http, "http"), Objects.requireNonNull(token, "token"));
    }

    @Override
    public Optional<String> getFileText(String ref, String path) {
        if (ref == null || ref.isBlank()) throw new IllegalArgumentException("ref is required");
        if (path == null || path.isBlank()) throw new IllegalArgumentException("path is required");

        String encPath = encodePath(path);
        String encRef = URLEncoder.encode(ref, StandardCharsets.UTF_8);
        String url = "https://api.github.com/repos/%s/%s/contents/%s?ref=%s"
                .formatted(owner, repo, encPath, encRef);

        try {
            String json = gh.getJson(url);
            JsonNode node = om.readTree(json);

            String contentB64 = node.path("content").asText(null);
            if (contentB64 == null) return Optional.empty();

            contentB64 = contentB64.replace("\n", "").replace("\r", "");
            byte[] bytes = Base64.getDecoder().decode(contentB64);
            return Optional.of(new String(bytes, StandardCharsets.UTF_8));
        } catch (ApiException e) {
            if (e.status() == 404) return Optional.empty();
            throw e;
        } catch (Exception e) {
            throw new ApiException(500, "Failed to parse GitHub file response for " + path + ": " + e.getMessage());
        }
    }

    @Override
    public List<OpenPullRequest> listOpenPullRequests(String baseBranch) {
        if (baseBranch == null || baseBranch.isBlank()) throw new IllegalArgumentException("baseBranch is required");
        String encBase = URLEncoder.encode(baseBranch, StandardCharsets.UTF_8);

        String url = "https://api.github.com/repos/%s/%s/pulls?state=open&base=%s&per_page=100"
                .formatted(owner, repo, encBase);

        try {
            String json = gh.getJson(url);
            JsonNode arr = om.readTree(json);
            if (!arr.isArray()) throw new ApiException(502, "GitHub pulls list did not return an array");

            List<OpenPullRequest> out = new ArrayList<>();
            for (JsonNode pr : arr) {
                int number = pr.path("number").asInt();
                String title = pr.path("title").asText("");
                String htmlUrl = pr.path("html_url").asText("");
                String updatedAt = pr.path("updated_at").asText("");

                JsonNode head = pr.path("head");
                String headSha = head.path("sha").asText("");
                String headRef = head.path("ref").asText("");

                JsonNode base = pr.path("base");
                String baseRef = base.path("ref").asText("");

                out.add(new OpenPullRequest(number, title, htmlUrl, headSha, headRef, baseRef, updatedAt));
            }
            return out;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(502, "Failed to list open PRs: " + e.getMessage());
        }
    }

    @Override
    public String name() {
        return "github:" + owner + "/" + repo;
    }

    private static String encodePath(String path) {
        String[] parts = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append("/");
            sb.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
