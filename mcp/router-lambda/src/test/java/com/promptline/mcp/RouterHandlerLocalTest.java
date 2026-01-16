package com.promptline.mcp;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.promptline.mcp.handler.RouterHandler;

public class RouterHandlerLocalTest {

    @Test
    void healthz_ok() {
        RouterHandler h = new RouterHandler();

        APIGatewayV2HTTPEvent evt = newEvent("GET", "/healthz", null);

        var resp = h.handleRequest(evt, new FakeContext());
        System.out.println("healthz status=" + resp.getStatusCode());
        System.out.println("healthz body=" + resp.getBody());

        assertTrue(resp.getStatusCode() / 100 == 2);
    }

    @Test
    void gitGetFile_emptyBody_is400() {
        RouterHandler h = new RouterHandler();

        APIGatewayV2HTTPEvent evt = newEvent("POST", "/git/get-file", "");

        var resp = h.handleRequest(evt, new FakeContext());
        System.out.println("git/get-file status=" + resp.getStatusCode());
        System.out.println("git/get-file body=" + resp.getBody());

        // should fail *after* auth (empty body)
        assertTrue(resp.getStatusCode() == 400 || resp.getStatusCode() == 422);
    }

    private static APIGatewayV2HTTPEvent newEvent(String method, String path, String body) {
        APIGatewayV2HTTPEvent evt = new APIGatewayV2HTTPEvent();
        evt.setRawPath(path);
        evt.setBody(body);

        // IMPORTANT: set both casings (API Gateway often lowercases headers)
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/json");
        headers.put("X-Internal-Token", "test");
        headers.put("x-internal-token", "test");
        evt.setHeaders(headers);

        // requestContext.http.method/path
        APIGatewayV2HTTPEvent.RequestContext rc = new APIGatewayV2HTTPEvent.RequestContext();
        APIGatewayV2HTTPEvent.RequestContext.Http http = new APIGatewayV2HTTPEvent.RequestContext.Http();
        http.setMethod(method);
        http.setPath(path);
        rc.setHttp(http);
        evt.setRequestContext(rc);

        return evt;
    }

    static final class FakeContext implements Context {
        private final LambdaLogger logger = new LambdaLogger() {
            @Override public void log(String message) { System.out.println(message); }
            @Override public void log(byte[] message) { System.out.println(new String(message)); }
        };

        @Override public String getAwsRequestId() { return "test"; }
        @Override public String getLogGroupName() { return "test"; }
        @Override public String getLogStreamName() { return "test"; }
        @Override public String getFunctionName() { return "test"; }
        @Override public String getFunctionVersion() { return "test"; }
        @Override public String getInvokedFunctionArn() { return "test"; }
        @Override public CognitoIdentity getIdentity() { return null; }
        @Override public ClientContext getClientContext() { return null; }
        @Override public int getRemainingTimeInMillis() { return 30_000; }
        @Override public int getMemoryLimitInMB() { return 512; }
        @Override public LambdaLogger getLogger() { return logger; }
    }
}
