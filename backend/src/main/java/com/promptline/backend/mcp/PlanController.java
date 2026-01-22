package com.promptline.backend.mcp;

import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanExecutionService executor;

    public PlanController(PlanExecutionService executor) {
        this.executor = executor;
    }

    @PostMapping("/{planId}/confirm")
    public Map<String, Object> confirm(@PathVariable UUID planId) {
        executor.confirmAndExecute(planId);
        return Map.of("status", "ok", "planId", planId.toString());
    }
}
