package com.promptline.backend.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptline.backend.chat.ChatEntity;
import com.promptline.backend.chat.MessageEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class McpPlanService {

    private final PlanRepository planRepo;
    private final ToolCallRepository toolCallRepo;
    private final ObjectMapper om;

    // TODO: replace with config if you want (application.yml)
    private static final Set<String> ALLOWED_TOOLS = Set.of(
            "git_ls_files",
            "git_ls",
            "text"
            // add real MCP tool names here
    );

    public McpPlanService(PlanRepository planRepo, ToolCallRepository toolCallRepo, ObjectMapper om) {
        this.planRepo = planRepo;
        this.toolCallRepo = toolCallRepo;
        this.om = om;
    }

    @Transactional
    public PlanEntity createProposedPlan(ChatEntity chat, MessageEntity assistantPlanMessage, String proposalJson) {
        JsonNode proposalNode;
        try {
            proposalNode = om.readTree(proposalJson);
        } catch (Exception e) {
            proposalNode = om.createObjectNode()
                    .put("type", "invalid_json_from_llm")
                    .put("raw", proposalJson);
        }

        PlanEntity plan = new PlanEntity();
        plan.setId(UUID.randomUUID());
        plan.setChat(chat);
        plan.setMessage(assistantPlanMessage);
        plan.setStatus(PlanStatus.PROPOSED);
        plan.setProposalJson(proposalNode);
        plan.setCreatedAt(OffsetDateTime.now());
        plan.setUpdatedAt(OffsetDateTime.now());
        planRepo.save(plan);

        List<ToolCallEntity> calls = extractToolCalls(plan, proposalNode);
        toolCallRepo.saveAll(calls);

        return plan;
    }

    private List<ToolCallEntity> extractToolCalls(PlanEntity plan, JsonNode proposal) {
        List<ToolCallEntity> out = new ArrayList<>();

        JsonNode steps = proposal.get("steps");
        if (steps == null || !steps.isArray()) return out;

        for (JsonNode step : steps) {
            String toolRaw = step.path("tool").asText(null);
            JsonNode args = step.get("args");

            if (toolRaw == null || toolRaw.isBlank()) continue;
            if (args == null || !args.isObject()) args = om.createObjectNode();

            String tool = normalizeTool(toolRaw);

            ToolCallEntity tc = new ToolCallEntity();
            tc.setId(UUID.randomUUID());
            tc.setPlan(plan);
            tc.setTool(tool);
            tc.setArgsJson(args);
            tc.setCreatedAt(OffsetDateTime.now());
            tc.setUpdatedAt(OffsetDateTime.now());

            // Enforce allowlist
            if (!ALLOWED_TOOLS.contains(tool)) {
                tc.setStatus(ToolCallStatus.FAILED);
                tc.setError("Tool not allowed: " + toolRaw);
            } else {
                tc.setStatus(ToolCallStatus.PENDING);
            }

            out.add(tc);
        }

        return out;
    }

    private String normalizeTool(String tool) {
        // keep it deterministic + safe
        String t = tool.trim().toLowerCase(Locale.ROOT);
        t = t.replaceAll("\\s+", "_");
        t = t.replaceAll("[^a-z0-9_\\-]", "");
        return t;
    }
}
