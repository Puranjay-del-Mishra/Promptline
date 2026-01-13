package com.promptline.mcp.routing;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class Router {
    private final Map<Key, Route> routes = new HashMap<>();

    public Router add(String method, String path, Route handler) {
        routes.put(new Key(method.toUpperCase(), path), handler);
        return this;
    }

    public Route match(APIGatewayV2HTTPEvent e) {
        String m = null;

        if (e.getRequestContext() != null && e.getRequestContext().getHttp() != null) {
            m = e.getRequestContext().getHttp().getMethod();
        }

        // Optional fallback for tests: allow overriding via headers
        if (m == null && e.getHeaders() != null) {
            m = e.getHeaders().get("x-http-method");
        }

        String p = e.getRawPath();
        if (m == null || p == null) return null;

        return routes.get(new Key(m.toUpperCase(), p));
    }



    private record Key(String method, String path) {
        @Override public boolean equals(Object o) {
            if (!(o instanceof Key k)) return false;
            return Objects.equals(method, k.method) && Objects.equals(path, k.path);
        }
        @Override public int hashCode() { return Objects.hash(method, path); }
    }
}
