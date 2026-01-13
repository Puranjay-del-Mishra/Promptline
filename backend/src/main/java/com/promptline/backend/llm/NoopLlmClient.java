package com.promptline.backend.llm;

import java.util.List;

public class NoopLlmClient implements LlmClient {

    @Override
    public String generateTitleFromFirstUserMessage(String firstMessage) {
        return "New chat";
    }

    @Override
    public String generateAssistantReply(String chatTitle, String userMessage) {
        return "⏳ Assistant response placeholder (LLM not wired yet)";
    }

    @Override
    public String generatePlanJson(String chatTitle, List<String> chatHistory, String userMessage) {
        return """
               {"summary":"noop plan","steps":[]}
               """;
    }

    @Override
    public boolean shouldProposePlan(String chatTitle, List<String> chatHistory, String userMessage) {
        return false; // noop always routes to normal chat
    }
}
