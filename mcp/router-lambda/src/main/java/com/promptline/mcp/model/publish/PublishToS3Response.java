package com.promptline.mcp.model.publish;

public record PublishToS3Response(String bucket, String key, String etag, long bytes) {}
