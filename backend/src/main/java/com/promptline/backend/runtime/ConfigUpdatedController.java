package com.promptline.backend.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptline.backend.sse.SseHub;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/internal")
public class ConfigUpdatedController {

    public record ConfigUpdatedRequest(String env, List<String> updated, String version) {}

    private final RuntimeConfigStore store;
    private final SseHub hub;
    private final ObjectMapper om;

    @Value("${internal.notify.token:}")
    private String internalToken;

    public ConfigUpdatedController(RuntimeConfigStore store, SseHub hub, ObjectMapper om) {
        this.store = store;
        this.hub = hub;
        this.om = om;
    }

    @PostMapping("/config-updated")
    public void configUpdated(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody ConfigUpdatedRequest req
    ) throws Exception {

        if (internalToken != null && !internalToken.isBlank()) {
            if (token == null || !internalToken.equals(token)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid internal token");
            }
        }

        // Invalidate caches based on what changed
        if (req.updated() != null) {
            if (req.updated().contains("ui")) store.invalidateUi();
            if (req.updated().contains("policy")) store.invalidatePolicy();
        }

        // JSON-safe payload
        String payload = om.writeValueAsString(req);
        hub.broadcast("CONFIG_UPDATED", payload);
    }
}
