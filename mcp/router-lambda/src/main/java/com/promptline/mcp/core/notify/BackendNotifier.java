package com.promptline.mcp.core.notify;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class BackendNotifier {
    private final HttpClient http;
    private final ObjectMapper om;
    private final String notifyUrl;
    private final String token;

    public BackendNotifier(HttpClient http, ObjectMapper om, String notifyUrl, String token) {
        this.http = http;
        this.om = om;
        this.notifyUrl = notifyUrl;
        this.token = token;
    }

    public void notifyConfigUpdated(String env, java.util.List<String> updated, String version) throws Exception {
        if (notifyUrl == null || notifyUrl.isBlank()) return; // allow empty until EC2 is up

        String body = om.writeValueAsString(Map.of(
                "env", env == null ? "" : env,
                "updated", updated == null ? java.util.List.of() : updated,
                "version", version == null ? "" : version
        ));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(notifyUrl))
                .header("content-type", "application/json")
                .header("X-Internal-Token", token == null ? "" : token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("backend notify failed: " + resp.statusCode());
        }
    }
}
