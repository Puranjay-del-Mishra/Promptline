package com.promptline.backend.mcp.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class PlanValidator {

    private static final Set<String> TARGETS = Set.of("ui", "policy");

    public List<String> validate(PlanProposalV1 p) {
        List<String> errs = new ArrayList<>();
        if (p == null) return List.of("plan is null");

        if (!"v1".equals(p.planVersion())) errs.add("planVersion must be v1");
        if (!"runtime_config_change".equals(p.intent())) errs.add("intent must be runtime_config_change");
        if (p.env() == null || p.env().isBlank()) errs.add("env is required");
        if (p.summary() == null || p.summary().isBlank()) errs.add("summary is required");
        if (p.summary() != null && p.summary().length() > 140) errs.add("summary too long");
        if (!p.requiresConfirmation()) errs.add("requiresConfirmation must be true");

        if (p.changes() == null || p.changes().isEmpty()) errs.add("changes[] is required");
        if (p.changes() != null && p.changes().size() > 20) errs.add("changes[] too large");

        if (p.changes() != null) {
            for (int i = 0; i < p.changes().size(); i++) {
                var c = p.changes().get(i);
                if (c == null) { errs.add("changes[" + i + "] is null"); continue; }

                String target = safeLower(c.target());
                String op = safeLower(c.op());
                String path = (c.path() == null) ? "" : c.path().trim();

                if (!TARGETS.contains(target)) errs.add("changes[" + i + "].target invalid");
                if (!"set".equals(op)) errs.add("changes[" + i + "].op must be set");
                if (path.isBlank()) errs.add("changes[" + i + "].path required");
                if (c.value() == null) errs.add("changes[" + i + "].value required");
            }
        }
        return errs;
    }

    private static String safeLower(String s) {
        return (s == null) ? "" : s.trim().toLowerCase();
    }
}
