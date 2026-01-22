package com.promptline.backend.mcp.plan;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record PlanProposalV1(
        String planVersion,
        String intent,
        String env,
        String summary,
        boolean requiresConfirmation,
        List<PlanChange> changes
) {}
