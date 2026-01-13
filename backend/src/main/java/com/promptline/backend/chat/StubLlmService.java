package com.promptline.backend.chat;

import org.springframework.stereotype.Service;

@Service
public class StubLlmService implements LlmService {

    @Override
    public String generateChatTitle(String firstUserMessage) {
        // mimic "LLM-ish" title generation for now
        String cleaned = firstUserMessage.replaceAll("\\s+", " ").trim();
        if (cleaned.isBlank()) return "New chat";
        return cleaned.length() <= 42 ? cleaned : cleaned.substring(0, 42) + "…";
    }

    @Override
    public String generateAssistantReply(String userMessage) {
        return "Stub reply: you said -> " + userMessage;
    }
}
