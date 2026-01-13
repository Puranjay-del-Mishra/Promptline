package com.promptline.backend.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RuntimeConfigStore {

    public record RuntimeBlob(String json, String sourceKey) {}

    private final S3Client s3;
    private final String bucket;
    private final String uiKey;
    private final String policyKey;

    private final AtomicReference<RuntimeBlob> uiCache = new AtomicReference<>();
    private final AtomicReference<RuntimeBlob> policyCache = new AtomicReference<>();

    public RuntimeConfigStore(
            S3Client s3,
            @Value("${promptline.s3.bucket}") String bucket,
            @Value("${promptline.s3.uiKey}") String uiKey,
            @Value("${promptline.s3.policyKey}") String policyKey
    ) {
        this.s3 = s3;
        this.bucket = bucket;
        this.uiKey = uiKey;
        this.policyKey = policyKey;
    }

    public RuntimeBlob getUiConfig() {
        var cached = uiCache.get();
        if (cached != null) return cached;

        var fresh = readJsonFromS3(uiKey);
        uiCache.set(fresh);
        return fresh;
    }

    public RuntimeBlob getPolicy() {
        var cached = policyCache.get();
        if (cached != null) return cached;

        var fresh = readJsonFromS3(policyKey);
        policyCache.set(fresh);
        return fresh;
    }

    public void invalidateUi() { uiCache.set(null); }
    public void invalidatePolicy() { policyCache.set(null); }

    private RuntimeBlob readJsonFromS3(String key) {
        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        ResponseBytes<GetObjectResponse> bytes = s3.getObjectAsBytes(req);
        String json = bytes.asString(StandardCharsets.UTF_8);
        return new RuntimeBlob(json, key);
    }
}
