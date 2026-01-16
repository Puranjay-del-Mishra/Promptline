package com.promptline.mcp.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.promptline.mcp.app.App;
import com.promptline.mcp.routing.ApiError;
import com.promptline.mcp.routing.Responses;

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
                    String got = event.getHeaders() != null ? event.getHeaders().get("x-internal-token") : null;
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

        } catch (IllegalArgumentException e) {
            return Responses.error(app.om, 400, ApiError.of("BAD_REQUEST", e.getMessage()));
        } catch (Exception e) {
            return Responses.error(app.om, 500, ApiError.of("INTERNAL_ERROR", "unexpected error"));
        }
    }
}
