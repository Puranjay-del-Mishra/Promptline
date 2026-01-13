package com.promptline.backend.chat;

import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chats")
public class ChatController {

    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @PostMapping
    public Map<String, UUID> createChat() {
        ChatEntity chat = service.createChat();
        return Map.of("chatId", chat.getId());
    }

    @GetMapping
    public List<ChatEntity> listChats() {
        return service.listChats();
    }

    @GetMapping("/{chatId}/messages")
    public List<MessageEntity> messages(@PathVariable UUID chatId) {
        return service.getMessages(chatId);
    }

    @PostMapping("/{chatId}/messages")
    public Map<String, Object> sendMessage(
            @PathVariable UUID chatId,
            @RequestBody Map<String, String> body
    ) {
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return Map.of("status", "error", "message", "content is required");
        }

        PlanProposedResponse out = service.addUserMessageAuto(chatId, content);

        Map<String, Object> resp = new HashMap<>();
        resp.put("assistantMessage", out.assistantMessage());
        resp.put("plan", out.plan()); // null is fine here
        resp.put("status", "ok");
        return resp;
    }
}
