package com.promptline.mcp.core.git.github;

import com.promptline.mcp.util.ApiException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class GitHubHttp {
    private final HttpClient http;
    private final String token;

    public GitHubHttp(HttpClient http, String token) {
        this.http = http;
        this.token = token;
    }

    public String getJson(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + token)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();

            if (code == 404) throw new ApiException(404, "Not found: " + url);
            if (code >= 400) throw new ApiException(code, "GitHub error " + code + " for " + url + ": " + resp.body());
            return resp.body();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(502, "GitHub request failed: " + e.getMessage());
        }
    }
}
