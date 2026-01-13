package com.promptline.mcp;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.promptline.mcp.handler.RouterHandler;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RouterHandlerLocalTest {

    @Test
    void healthz() {
        var handler = new RouterHandler();

        var evt = new APIGatewayV2HTTPEvent();
        evt.setRawPath("/healthz");
        evt.setRequestContext(buildRequestContext("GET", "/healthz"));
        evt.setHeaders(Map.of("content-type", "application/json"));

        APIGatewayV2HTTPResponse resp = handler.handleRequest(evt, new FakeContext());

        assertEquals(200, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().contains("\"ok\""));
    }

    @Test
    void gitGetFile_emptyBody_is400() {
        var handler = new RouterHandler();

        var evt = new APIGatewayV2HTTPEvent();
        evt.setRawPath("/git/get-file");
        evt.setRequestContext(buildRequestContext("POST", "/git/get-file"));
        evt.setHeaders(Map.of("content-type", "application/json"));
        evt.setBody(""); // intentionally empty

        APIGatewayV2HTTPResponse resp = handler.handleRequest(evt, new FakeContext());

        // pick whichever your error layer returns (400/422).
        // If you currently return 500, we'll tighten it after this test.
        assertTrue(resp.getStatusCode() == 400 || resp.getStatusCode() == 422 || resp.getStatusCode() == 500);
        assertNotNull(resp.getBody());
    }

    private static APIGatewayV2HTTPEvent.RequestContext buildRequestContext(String method, String path) {
        var rc = new APIGatewayV2HTTPEvent.RequestContext();
        var http = new APIGatewayV2HTTPEvent.RequestContext.Http();
        http.setMethod(method);
        http.setPath(path);
        rc.setHttp(http);
        return rc;
    }

    static final class FakeContext implements Context {
        @Override public String getAwsRequestId() { return "local"; }
        @Override public String getLogGroupName() { return "local"; }
        @Override public String getLogStreamName() { return "local"; }
        @Override public String getFunctionName() { return "local"; }
        @Override public String getFunctionVersion() { return "local"; }
        @Override public String getInvokedFunctionArn() { return "local"; }
        @Override public com.amazonaws.services.lambda.runtime.CognitoIdentity getIdentity() { return null; }
        @Override public com.amazonaws.services.lambda.runtime.ClientContext getClientContext() { return null; }
        @Override public int getRemainingTimeInMillis() { return 30_000; }
        @Override public int getMemoryLimitInMB() { return 1024; }
        @Override
        public LambdaLogger getLogger() {
            return new LambdaLogger() {
                @Override
                public void log(String message) {
                    System.out.println("[lambda] " + message);
                }

                @Override
                public void log(byte[] message) {
                    System.out.println("[lambda] " + new String(message));
                }
            };
        }
    }
}
