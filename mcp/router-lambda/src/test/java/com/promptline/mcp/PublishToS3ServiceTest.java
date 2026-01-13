package com.promptline.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptline.mcp.core.publish.PublishToS3Service;
import com.promptline.mcp.core.publish.S3Publisher;
import com.promptline.mcp.model.publish.PublishToS3Request;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PublishToS3ServiceTest {

    @Test
    void publish_ui_writes_runtime_ui_json() throws Exception {
        S3Publisher fake = (bucket, key, json) ->
                new S3Publisher.PublishResult(bucket, key, "\"etag\"", json.length());

        var svc = new PublishToS3Service(fake, "promptline", "runtime/", new ObjectMapper());

        var out = svc.publish(new PublishToS3Request("ui", "{\"a\":1}"));
        assertEquals("promptline", out.bucket());
        assertEquals("runtime/ui.json", out.key());
        assertEquals("\"etag\"", out.etag());
        assertTrue(out.bytes() > 0);
    }

    @Test
    void publish_rejects_invalid_json() {
        S3Publisher fake = (bucket, key, json) ->
                new S3Publisher.PublishResult(bucket, key, "\"etag\"", json.length());

        var svc = new PublishToS3Service(fake, "promptline", "runtime/", new ObjectMapper());

        assertThrows(Exception.class, () ->
                svc.publish(new PublishToS3Request("ui", "{not-json}"))
        );
    }
}
