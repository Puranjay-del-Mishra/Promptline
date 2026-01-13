package com.promptline.mcp.routing;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public final class Responses {
    private Responses() {}

    public static APIGatewayV2HTTPResponse json(ObjectMapper om, int status, Object body) {
        try {
            String s = om.writeValueAsString(body);
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(status)
                    .withHeaders(Map.of("content-type", "application/json"))
                    .withBody(s)
                    .build();
        } catch (Exception e) {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(500)
                    .withHeaders(Map.of("content-type", "application/json"))
                    .withBody("{\"error\":{\"code\":\"SERIALIZE_ERROR\",\"message\":\"failed to serialize response\"}}")
                    .build();
        }
    }

    public static APIGatewayV2HTTPResponse error(ObjectMapper om, int status, ApiError err) {
        return json(om, status, Map.of("error", err));
    }
}
