package com.promptline.backend.mcp.plan;

import com.fasterxml.jackson.databind.JsonNode;

public record PlanChange(
        String target,   // ui | policy
        String op,       // set
        String path,     // dot.path
        JsonNode value
) {}
