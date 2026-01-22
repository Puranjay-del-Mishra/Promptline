package com.promptline.backend.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptline.backend.mcp.client.McpRouterDtos;
import com.promptline.backend.mcp.plan.PlanParser;
import com.promptline.backend.mcp.plan.PlanProposalV1;
import com.promptline.backend.mcp.plan.PlanValidator;
import com.promptline.backend.sse.SseHub;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlanExecutionService {

    private final PlanRepository planRepo;
    private final McpRouterClient mcp;
    private final SseHub hub;
    private final ObjectMapper om;

    // plan parsing/validation
    private final PlanParser parser;
    private final PlanValidator validator;

    public PlanExecutionService(
            PlanRepository planRepo,
            McpRouterClient mcp,
            SseHub hub,
            ObjectMapper om
    ) {
        this.planRepo = planRepo;
        this.mcp = mcp;
        this.hub = hub;
        this.om = om;

        this.parser = new PlanParser(om);
        this.validator = new PlanValidator();
    }

    @Transactional
    public void confirmAndExecute(UUID planId) {
        PlanEntity plan = planRepo.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));

        if (plan.getStatus() != PlanStatus.PROPOSED) {
            throw new IllegalStateException("Plan is not PROPOSED (status=" + plan.getStatus() + ")");
        }

        // ✅ Confirm
        plan.setStatus(PlanStatus.CONFIRMED);
        planRepo.save(plan);

        broadcast("PLAN_CONFIRMED", Map.of(
                "chatId", plan.getChat().getId().toString(),
                "planId", plan.getId().toString(),
                "status", plan.getStatus().name()
        ));

        // ✅ Transition to RUNNING
        plan.setStatus(PlanStatus.RUNNING);
        planRepo.save(plan);

        broadcast("PLAN_EXECUTION_UPDATED", Map.of(
                "chatId", plan.getChat().getId().toString(),
                "planId", plan.getId().toString(),
                "status", plan.getStatus().name(),
                "phase", "START"
        ));

        try {
            // ✅ Build MCP request payload (router contract)
            PlanProposalV1 p = parser.parse(plan.getProposalJson().toString());

            var errs = validator.validate(p);
            if (!errs.isEmpty()) {
                throw new IllegalArgumentException("Invalid plan: " + String.join("; ", errs));
            }

            McpRouterDtos.ConfigCheckLiveRequest req =
                    new McpRouterDtos.ConfigCheckLiveRequest(
                            normalizeEnv(p.env()),
                            p.changes().stream()
                                    .map(c -> new McpRouterDtos.ConfigChange(
                                            normalizeTarget(c.target()),
                                            "set",
                                            c.path(),
                                            // JsonNode -> plain java value
                                            om.convertValue(c.value(), Object.class)
                                    ))
                                    .collect(Collectors.toList())
                    );

            // Phase 0
            var p0 = mcp.checkLive(req);
            broadcast("PLAN_EXECUTION_UPDATED", Map.of(
                    "planId", plan.getId().toString(),
                    "status", plan.getStatus().name(),
                    "phase", "PHASE_0_CHECK_LIVE",
                    "result", p0
            ));

            // Phase 1
            var p1 = mcp.checkOpenPr(req);
            broadcast("PLAN_EXECUTION_UPDATED", Map.of(
                    "planId", plan.getId().toString(),
                    "status", plan.getStatus().name(),
                    "phase", "PHASE_1_CHECK_OPEN_PR",
                    "result", p1
            ));

            // Phase 2
            var p2 = mcp.ensurePr(req);
            broadcast("PLAN_EXECUTION_UPDATED", Map.of(
                    "planId", plan.getId().toString(),
                    "status", plan.getStatus().name(),
                    "phase", "PHASE_2_ENSURE_PR",
                    "result", p2
            ));

            plan.setStatus(PlanStatus.COMPLETED);
            planRepo.save(plan);

            broadcast("PLAN_EXECUTION_UPDATED", Map.of(
                    "planId", plan.getId().toString(),
                    "status", plan.getStatus().name(),
                    "phase", "DONE"
            ));

        } catch (Exception e) {
            plan.setStatus(PlanStatus.FAILED);
            planRepo.save(plan);

            broadcast("PLAN_EXECUTION_UPDATED", Map.of(
                    "planId", plan.getId().toString(),
                    "status", plan.getStatus().name(),
                    "phase", "ERROR",
                    "error", e.getMessage()
            ));
        }
    }

    private static String normalizeEnv(String env) {
        return (env == null || env.isBlank()) ? "live" : env.trim();
    }

    private static String normalizeTarget(String t) {
        return (t == null) ? "" : t.trim().toLowerCase();
    }

    private void broadcast(String event, Object payload) {
        try {
            hub.broadcast(event, om.writeValueAsString(payload));
        } catch (Exception ignored) {}
    }
}
