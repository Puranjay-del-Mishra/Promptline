package com.promptline.backend.chat;

import com.promptline.backend.mcp.PlanEntity;

public record ChatPostResponse(
        MessageEntity assistantMessage,
        PlanEntity plan,          // null when no plan was proposed
        String status
) {}
