package com.promptline.mcp.core.publish;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class AwsS3Publisher implements S3Publisher {
    private final S3Client s3;

    public AwsS3Publisher(S3Client s3) {
        this.s3 = Objects.requireNonNull(s3, "s3");
    }

    @Override
    public PublishResult putJson(String bucket, String key, String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/json")
                .build();

        var resp = s3.putObject(req, RequestBody.fromBytes(bytes));
        return new PublishResult(bucket, key, resp.eTag(), bytes.length);
    }
}
