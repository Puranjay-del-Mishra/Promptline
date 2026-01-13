package com.promptline.mcp.core.publish;

public interface S3Publisher {
    PublishResult putJson(String bucket, String key, String json);

    record PublishResult(String bucket, String key, String etag, long bytes) {}
}
