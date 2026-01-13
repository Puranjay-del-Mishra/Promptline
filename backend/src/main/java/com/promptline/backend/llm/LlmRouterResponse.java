package com.promptline.backend.llm;

import java.util.List;
import java.util.Map;

public record LlmRouterResponse(
        Mode mode,
        Assistant assistant,
        Plan plan
) {
    public enum Mode { CHAT, PLAN }

    public record Assistant(String content) {}

    public record Plan(String summary, List<Step> steps) {}

    public record Step(String tool, Map<String, Object> args, String why) {}
}
