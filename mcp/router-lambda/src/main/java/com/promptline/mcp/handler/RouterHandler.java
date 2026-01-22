package com.promptline.mcp.handler;

import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.promptline.mcp.app.App;
import com.promptline.mcp.routing.ApiError;
import com.promptline.mcp.routing.Responses;
import com.promptline.mcp.util.ApiException;

public class RouterHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        var app = App.get();
        try {
            String path = event.getRawPath();
            String method = event.getRequestContext() != null && event.getRequestContext().getHttp() != null
                    ? event.getRequestContext().getHttp().getMethod()
                    : null;

            // Auth gate: allow healthz publicly; everything else requires X-Internal-Token
            if (!"/healthz".equals(path)) {
                String expected = app.config.internalToken();
                if (expected != null && !expected.isBlank()) {
                    String got = header(event.getHeaders(), "x-internal-token");
                    if (got == null || !expected.equals(got)) {
                        return Responses.error(app.om, 401, ApiError.of("UNAUTHORIZED", "missing/invalid X-Internal-Token"));
                    }
                }
            }

            var route = app.router.match(event);
            if (route == null) {
                return Responses.error(app.om, 404, ApiError.of("NOT_FOUND", "no route for this method/path"));
            }

            Object out = route.handle(event, context);
            return Responses.json(app.om, 200, out);

        } catch (ApiException e) {
            // IMPORTANT: preserve intended status codes like 400, 401, 404, etc.
            int code = e.status();
            String msg = (e.getMessage() == null || e.getMessage().isBlank()) ? "error" : e.getMessage();
            String errCode =
                    (code == 400) ? "BAD_REQUEST" :
                    (code == 401) ? "UNAUTHORIZED" :
                    (code == 403) ? "FORBIDDEN" :
                    (code == 404) ? "NOT_FOUND" :
                    "ERROR";
            return Responses.error(app.om, code, ApiError.of(errCode, msg));

        } catch (IllegalArgumentException e) {
            return Responses.error(app.om, 400, ApiError.of("BAD_REQUEST", e.getMessage()));

        } catch (Exception e) {
            return Responses.error(app.om, 500, ApiError.of("INTERNAL_ERROR", "unexpected error"));
        }
    }

    private static String header(Map<String, String> headers, String keyLower) {
        if (headers == null || keyLower == null) return null;

        // Fast path: API Gateway usually lowercases keys
        String v = headers.get(keyLower);
        if (v != null) return v;

        // Fallback: case-insensitive scan (helps manual lambda invoke payloads)
        for (var e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(keyLower)) {
                return e.getValue();
            }
        }
        return null;
    }
}
