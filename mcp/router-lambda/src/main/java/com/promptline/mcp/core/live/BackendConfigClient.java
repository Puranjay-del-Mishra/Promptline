package com.promptline.mcp.core.live;

import com.promptline.mcp.util.ApiException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class BackendConfigClient {

    private final HttpClient http;
    private final String baseUrl;

    public BackendConfigClient(HttpClient http, String baseUrl) {
        this.http = http;
        this.baseUrl = (baseUrl == null) ? "" : baseUrl.trim();
    }

    public String getPolicyJson() {
        return get("/policy");
    }

    public String getUiJson() {
        return get("/ui-config");
    }

    private String get(String path) {
        if (baseUrl.isBlank()) throw new ApiException(500, "BACKEND_PUBLIC_BASE_URL is not set");
        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        url = url + path;

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            if (code / 100 != 2) {
                throw new ApiException(502, "backend GET failed " + code + " for " + path);
            }
            return resp.body() == null ? "" : resp.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(502, "backend request failed for " + path + ": " + e.getMessage());
        }
    }
}
