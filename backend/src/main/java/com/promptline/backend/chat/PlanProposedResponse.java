package com.promptline.backend.chat;

import com.promptline.backend.mcp.PlanEntity;

public record PlanProposedResponse(
        MessageEntity assistantMessage,
        PlanEntity plan
) {}
