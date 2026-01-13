package com.promptline.mcp.routing;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;

@FunctionalInterface
public interface Route {
    Object handle(APIGatewayV2HTTPEvent event, Context ctx) throws Exception;
}
