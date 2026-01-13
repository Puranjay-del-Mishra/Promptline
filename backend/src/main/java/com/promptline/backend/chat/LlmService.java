package com.promptline.backend.chat;

public interface LlmService {
    String generateChatTitle(String firstUserMessage);
    String generateAssistantReply(String userMessage);
}
