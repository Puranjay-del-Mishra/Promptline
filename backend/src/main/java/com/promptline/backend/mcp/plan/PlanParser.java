package com.promptline.backend.mcp.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class PlanParser {
    private final ObjectMapper om;

    public PlanParser(ObjectMapper om) { this.om = om; }

    public PlanProposalV1 parse(String rawJson) throws Exception {
        JsonNode node = om.readTree(rawJson);
        return om.treeToValue(node, PlanProposalV1.class);
    }

    public JsonNode parseTree(String rawJson) throws Exception {
        return om.readTree(rawJson);
    }
}
