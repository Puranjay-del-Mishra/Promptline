package com.promptline.mcp.core.git.github;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptline.mcp.core.git.GitProvider;
import com.promptline.mcp.model.pr.OpenPullRequest;
import com.promptline.mcp.util.ApiException;

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

    @Override
    public String getBranchHeadSha(String branch) {
        if (branch == null || branch.isBlank()) throw new IllegalArgumentException("branch is required");
        String url = "https://api.github.com/repos/%s/%s/git/ref/heads/%s"
                .formatted(owner, repo, URLEncoder.encode(branch, StandardCharsets.UTF_8));
        try {
            String json = gh.getJson(url);
            JsonNode node = om.readTree(json);
            String sha = node.path("object").path("sha").asText("");
            if (sha.isBlank()) throw new ApiException(502, "could not read branch sha for " + branch);
            return sha;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(502, "Failed to get branch head sha: " + e.getMessage());
        }
    }

    @Override
    public void createBranch(String newBranch, String fromSha) {
        if (newBranch == null || newBranch.isBlank()) throw new IllegalArgumentException("newBranch is required");
        if (fromSha == null || fromSha.isBlank()) throw new IllegalArgumentException("fromSha is required");

        String url = "https://api.github.com/repos/%s/%s/git/refs".formatted(owner, repo);
        String body = """
                {"ref":"refs/heads/%s","sha":"%s"}
                """.formatted(escapeJson(newBranch), escapeJson(fromSha));

        try {
            gh.postJson(url, body);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(502, "Failed to create branch: " + e.getMessage());
        }
    }

    @Override
    public void upsertTextFile(String branch, String path, String contentUtf8, String commitMessage) {
        if (branch == null || branch.isBlank()) throw new IllegalArgumentException("branch is required");
        if (path == null || path.isBlank()) throw new IllegalArgumentException("path is required");
        if (commitMessage == null || commitMessage.isBlank()) commitMessage = "update " + path;

        try {
            // Check if file exists on this branch to get sha (needed for update)
            Optional<String> existingSha = getFileSha(branch, path);

            String url = "https://api.github.com/repos/%s/%s/contents/%s"
                    .formatted(owner, repo, encodePath(path));

            String b64 = Base64.getEncoder().encodeToString(contentUtf8.getBytes(StandardCharsets.UTF_8));

            String payload;
            if (existingSha.isPresent()) {
                payload = """
                        {"message":"%s","content":"%s","branch":"%s","sha":"%s"}
                        """.formatted(
                        escapeJson(commitMessage),
                        escapeJson(b64),
                        escapeJson(branch),
                        escapeJson(existingSha.get())
                );
            } else {
                payload = """
                        {"message":"%s","content":"%s","branch":"%s"}
                        """.formatted(
                        escapeJson(commitMessage),
                        escapeJson(b64),
                        escapeJson(branch)
                );
            }

            gh.putJson(url, payload);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(502, "Failed to upsert file: " + e.getMessage());
        }
    }

    @Override
    public OpenPullRequest createPullRequest(String baseBranch, String headBranch, String title, String body) {
        if (baseBranch == null || baseBranch.isBlank()) throw new IllegalArgumentException("baseBranch is required");
        if (headBranch == null || headBranch.isBlank()) throw new IllegalArgumentException("headBranch is required");
        if (title == null || title.isBlank()) title = "config: update";

        String url = "https://api.github.com/repos/%s/%s/pulls".formatted(owner, repo);

        String payload = """
                {"title":"%s","head":"%s","base":"%s","body":"%s"}
                """.formatted(
                escapeJson(title),
                escapeJson(headBranch),
                escapeJson(baseBranch),
                escapeJson(body == null ? "" : body)
        );

        try {
            String json = gh.postJson(url, payload);
            JsonNode pr = om.readTree(json);

            int number = pr.path("number").asInt();
            String htmlUrl = pr.path("html_url").asText("");
            String updatedAt = pr.path("updated_at").asText("");

            JsonNode head = pr.path("head");
            String headSha = head.path("sha").asText("");
            String headRef = head.path("ref").asText(headBranch);

            JsonNode base = pr.path("base");
            String baseRef = base.path("ref").asText(baseBranch);

            return new OpenPullRequest(number, title, htmlUrl, headSha, headRef, baseRef, updatedAt);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(502, "Failed to create PR: " + e.getMessage());
        }
    }

    private Optional<String> getFileSha(String branch, String path) {
        try {
            String url = "https://api.github.com/repos/%s/%s/contents/%s?ref=%s"
                    .formatted(owner, repo, encodePath(path), URLEncoder.encode(branch, StandardCharsets.UTF_8));
            String json = gh.getJson(url);
            JsonNode node = om.readTree(json);
            String sha = node.path("sha").asText("");
            return sha.isBlank() ? Optional.empty() : Optional.of(sha);
        } catch (ApiException e) {
            // 404 => file doesn't exist (create mode)
            if (e.status() == 404) return Optional.empty();
            throw e;
        } catch (Exception e) {
            throw new ApiException(502, "Failed to read file sha: " + e.getMessage());
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
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
