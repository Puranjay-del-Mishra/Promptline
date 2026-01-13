package com.promptline.mcp.core.publish;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptline.mcp.model.publish.PublishToS3Request;
import com.promptline.mcp.model.publish.PublishToS3Response;

import java.util.Objects;

public final class PublishToS3Service {
    private final S3Publisher publisher;
    private final String bucket;
    private final String runtimePrefix;
    private final ObjectMapper om;

    public PublishToS3Service(S3Publisher publisher, String bucket, String runtimePrefix, ObjectMapper om) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.bucket = Objects.requireNonNull(bucket, "bucket");
        this.runtimePrefix = Objects.requireNonNull(runtimePrefix, "runtimePrefix");
        this.om = Objects.requireNonNull(om, "om");
    }

    public PublishToS3Response publish(PublishToS3Request req) throws Exception {
        if (req == null || req.kind() == null || req.kind().isBlank()) {
            throw new IllegalArgumentException("kind is required");
        }
        if (req.content() == null || req.content().isBlank()) {
            throw new IllegalArgumentException("content is required");
        }

        // Validate JSON (important; prevents publishing garbage to runtime cache)
        om.readTree(req.content());

        String key = runtimeKeyFor(req.kind());
        var res = publisher.putJson(bucket, key, req.content());

        return new PublishToS3Response(res.bucket(), res.key(), res.etag(), res.bytes());
    }

    private String runtimeKeyFor(String kindRaw) {
        String kind = kindRaw.trim().toLowerCase();
        return switch (kind) {
            case "ui" -> runtimePrefix + "ui.json";
            case "policy" -> runtimePrefix + "policy.json";
            default -> throw new IllegalArgumentException("unknown kind: " + kindRaw);
        };
    }
}
