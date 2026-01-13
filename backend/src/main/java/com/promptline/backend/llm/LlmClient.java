package com.promptline.backend.llm;

import java.util.List;

public interface LlmClient {
    String generateTitleFromFirstUserMessage(String firstMessage);

    String generateAssistantReply(String chatTitle, String userMessage);

    String generatePlanJson(String chatTitle, List<String> chatHistory, String userMessage);

    boolean shouldProposePlan(String chatTitle, List<String> chatHistory, String userMessage);
}
