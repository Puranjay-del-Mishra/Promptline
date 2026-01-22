package com.promptline.backend.chat;

import com.promptline.backend.llm.LlmClient;
import com.promptline.backend.mcp.McpPlanService;
import com.promptline.backend.mcp.PlanEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.promptline.backend.sse.SseHub;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

@Service
public class ChatService {

    private final ChatRepository chatRepo;
    private final MessageRepository messageRepo;
    private final LlmClient llm;
    private final McpPlanService mcpPlanService;
    private final SseHub hub;
    private final ObjectMapper om;


    public ChatService(
            ChatRepository chatRepo,
            MessageRepository messageRepo,
            LlmClient llm,
            McpPlanService mcpPlanService,
            SseHub hub,
            ObjectMapper om
    ) {
        this.chatRepo = chatRepo;
        this.messageRepo = messageRepo;
        this.llm = llm;
        this.mcpPlanService = mcpPlanService;
        this.hub = hub;
        this.om = om;
        System.out.println("LLM client wired: " + llm.getClass().getName());
    }


    public ChatEntity createChat() {
        return chatRepo.save(new ChatEntity());
    }

    public List<ChatEntity> listChats() {
        return chatRepo.findAll()
                .stream()
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MessageEntity> getMessages(UUID chatId) {
        return messageRepo.findMessagesForChat(chatId);
    }

    // ---- Router entrypoint (the key change) ----

    @Transactional
    public PlanProposedResponse addUserMessageAuto(UUID chatId, String userContent) {
        ChatEntity chat = chatRepo.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat not found"));

        List<String> history = buildHistory(chatId, 20);

        boolean proposePlan = this.llm.shouldProposePlan(chat.getTitle(), history, userContent);

        if (proposePlan) {
            return addUserMessageAndProposePlan(chatId, userContent);
        } else {
            MessageEntity assistant = addUserMessageAndRespond(chatId, userContent);
            return new PlanProposedResponse(assistant, null);
        }
    }

    // ---- MCP Step 1: propose plan ----

    /**
     * MCP Step 1:
     * - save user message
     * - ensure title
     * - generate plan JSON
     * - save assistant message (content = plan JSON)
     * - persist plans row + tool_calls placeholders
     */
    @Transactional
    public PlanProposedResponse addUserMessageAndProposePlan(UUID chatId, String userContent) {
        ChatEntity chat = chatRepo.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat not found"));

        // 1) Save user message
        MessageEntity userMsg = new MessageEntity();
        userMsg.setChat(chat);
        userMsg.setRole("user");
        userMsg.setContent(userContent);
        messageRepo.save(userMsg);

        // 2) Title once
        ensureTitle(chat, userContent);

        chat.touch();
        chatRepo.save(chat);

        // 3) Build history (for regen / context)
        List<String> history = buildHistory(chatId, 20);

        // 4) Generate plan JSON (RAW JSON string)
        String planJson = this.llm.generatePlanJson(chat.getTitle(), history, userContent);
        if (planJson == null || planJson.isBlank()) {
            planJson = """
                {
                "planVersion":"v1",
                "intent":"runtime_config_change",
                "env":"live",
                "summary":"Unable to generate plan",
                "requiresConfirmation":true,
                "changes":[]
                }
                """.trim();
        }

        // 5) Save assistant message (store JSON as message content)
        MessageEntity assistant = new MessageEntity();
        assistant.setChat(chat);
        assistant.setRole("assistant");
        assistant.setContent(planJson);
        assistant = messageRepo.save(assistant);

        chat.touch();
        chatRepo.save(chat);

        // 6) Persist plans + tool_calls placeholders
        // Supersede any older proposed plan(s) for this chat
        mcpPlanService.supersedeAllProposed(chatId);

        PlanEntity plan = mcpPlanService.createProposedPlan(chat, assistant, planJson);

        try {
            hub.broadcast("PLAN_PROPOSED", om.writeValueAsString(Map.of(
                    "chatId", chatId.toString(),
                    "planId", plan.getId().toString(),
                    "status", plan.getStatus().name(),
                    "summary", plan.getProposalJson().path("summary").asText(""),
                    "changes", plan.getProposalJson().path("changes")
            )));
        } catch (Exception e) {
            // never break chat flow due to SSE
            System.err.println("PLAN_PROPOSED broadcast failed: " + e.getMessage());
        }

        return new PlanProposedResponse(assistant, plan);
    }

    // ---- Normal chat ----

    /**
     * Normal chat:
     * - saves the user message
     * - if first message: sets firstUserMessage + generates title
     * - generates assistant reply via LLM
     * - saves assistant message
     */
    @Transactional
    public MessageEntity addUserMessageAndRespond(UUID chatId, String userContent) {
        ChatEntity chat = chatRepo.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat not found"));

        // 1) Save user message
        MessageEntity userMsg = new MessageEntity();
        userMsg.setChat(chat);
        userMsg.setRole("user");
        userMsg.setContent(userContent);
        messageRepo.save(userMsg);

        // 2) Title once
        ensureTitle(chat, userContent);

        chat.touch();
        chatRepo.save(chat);

        // 3) Assistant reply
        String reply = this.llm.generateAssistantReply(chat.getTitle(), userContent);
        if (reply == null || reply.isBlank()) {
            reply = "⚠️ LLM returned an empty response";
        }

        // 4) Save assistant message
        MessageEntity assistant = new MessageEntity();
        assistant.setChat(chat);
        assistant.setRole("assistant");
        assistant.setContent(reply);

        chat.touch();
        chatRepo.save(chat);

        return messageRepo.save(assistant);
    }

    // ---- helpers ----

    private void ensureTitle(ChatEntity chat, String userContent) {
        if (chat.getFirstUserMessage() != null) return;

        chat.setFirstUserMessage(userContent);

        String generatedTitle = this.llm.generateTitleFromFirstUserMessage(userContent);
        if (generatedTitle == null || generatedTitle.isBlank()) {
            generatedTitle = generateTitleFallback(userContent);
        }
        chat.setTitle(generatedTitle);
    }

    private String generateTitleFallback(String content) {
        String cleaned = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        if (cleaned.isBlank()) return "New chat";
        return cleaned.length() <= 42 ? cleaned : cleaned.substring(0, 42) + "…";
    }
    private List<String> buildHistory(UUID chatId, int max) {
        return messageRepo.findMessagesForChat(chatId).stream()
                .filter(m -> {
                    if (!"assistant".equals(m.getRole())) return true;
                    return !isPlanJson(m.getContent());
                })
                .limit(max)
                .map(m -> m.getRole() + ": " + m.getContent())
                .toList();
    }

    private boolean isPlanJson(String content) {
        if (content == null) return false;
        return content.contains("\"planVersion\"")
                && content.contains("\"intent\"")
                && content.contains("\"changes\"");
    }

}
