package com.promptline.backend.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@ConditionalOnProperty(prefix = "promptline.llm", name = "provider", havingValue = "openrouter")
public class OpenRouterLlmClient implements LlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient rest;
    private final PromptlineLlmProperties props;

    public OpenRouterLlmClient(PromptlineLlmProperties props, RestClient.Builder builder) {
        this.props = props;

        var or = props.getOpenrouter();

        this.rest = builder
                .baseUrl(or.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + nullToEmpty(or.getApiKey()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("HTTP-Referer", nullToEmpty(or.getAppUrl()))
                .defaultHeader("X-Title", nullToEmpty(or.getAppName()))
                .build();
    }

    @Override
    public String generateTitleFromFirstUserMessage(String firstMessage) {
        String system = """
                You generate a chat title.

                Output MUST be valid JSON and NOTHING else.
                Schema:
                {"title":"<3 to 8 words, no quotes, no trailing punctuation>"}

                Rules:
                - One line JSON only (no markdown, no extra keys)
                - title length: 3 to 8 words
                - do NOT end with punctuation
                - do NOT include newline characters
                """;

        String raw = callChatCompletion(
                props.getOpenrouter().getFastModel(),
                List.of(
                        msg("system", system),
                        msg("user", firstMessage)
                ),
                40,
                0.2
        );

        return parseTitleJsonOrFallback(raw, firstMessage);
    }

    @Override
    public String generateAssistantReply(String chatTitle, String userMessage) {
        String system = """
        You are Promptline, an assistant inside a developer tool.
        Be direct and technical. Ask one clarifying question only if required.

        Output MUST be valid JSON and NOTHING else.
        Schema:
        {"reply":"<plain text reply>"}

        Rules:
        - Return ONE line JSON only (no markdown)
        - Always include "reply"
        - DO NOT include "mcp", "actions", "summary", "steps", or any plan JSON
        - If the user asks for a plan / steps / tool workflow:
        Reply: "I can propose a plan for this. Say 'yes' to confirm you want a plan."
        """;


        String raw = callChatCompletion(
                props.getOpenrouter().getStrongModel(),
                List.of(
                        msg("system", system),
                        msg("user", userMessage)
                ),
                700,
                0.4
        );

        return parseReplyJsonOrFallback(raw);
    }

    /**
     * MCP Step 1: propose a plan.
     * This returns RAW JSON string (stored into plans.proposal_json and also as assistant msg content).
     */
    @Override
    public String generatePlanJson(String chatTitle, List<String> chatHistory, String userMessage) {
        String system = """
                You are generating a PLAN JSON for a config-change system.

                Output MUST be a single valid JSON object and NOTHING ELSE.
                No markdown, no explanation, no trailing text.

                Allowed planVersion: "v1"
                intent MUST be: "runtime_config_change"
                env MUST be: "live"

                The plan MUST include:
                - planVersion (string)
                - intent (string)
                - env (string)
                - summary (string, <= 140 chars)
                - requiresConfirmation (boolean, always true)
                - changes (array length 1..20)

                Each change MUST be:
                { "target": "ui" | "policy", "op": "set", "path": "<dot.path>", "value": <any JSON value> }

                The only allowed op is "set".

                Important:
                - Use the dot-path exactly as the JSON path inside the config files.
                - For language changes, update policy path like: rules.languageTag = "es" or similar (if that path exists).
                - For theme changes, update ui path like: theme = "dark" or "light".

                Here are the current canonical config shapes:

                UI config (config/ui.json):
                {
                "theme": "dark",
                "version": 3
                }

                POLICY config (config/policy.json):
                {
                "allowlist": ["/healthz"],
                "rateLimit": { "rpm": 69 }
                }

                Now generate the plan JSON for the user request:
                """;

        // keep context small + deterministic
        String historyBlock = (chatHistory == null || chatHistory.isEmpty())
                ? ""
                : String.join(" | ", chatHistory.stream().limit(12).toList());

        String user = """
                ChatTitle: %s
                History: %s
                UserRequest: %s
                """.formatted(
                nullToEmpty(chatTitle),
                historyBlock,
                nullToEmpty(userMessage)
        );

        String raw = callChatCompletion(
                props.getOpenrouter().getStrongModel(),
                List.of(
                        msg("system", system),
                        msg("user", user)
                ),
                600,
                0.2
        );

        // Important: return the JSON, not parsed "reply"
        String json = extractFirstJsonObject(raw);
        if (json == null) {
            // fail-soft but still valid JSON
            return """
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
        return json;
    }

    @Override
    public boolean shouldProposePlan(String chatTitle, List<String> chatHistory, String userMessage) {
        String system = """
                You are a router for a developer assistant.

                Decide if the user is requesting a TOOL/PLAN proposal (MCP plan) or a normal chat reply.

                Output MUST be valid JSON and NOTHING else. One-line JSON only.
                Schema:
                {"proposePlan": true|false}

                Guidelines:
                - proposePlan=true when user asks for: steps, a plan, implementation plan, tool calls, MCP, "do not execute", approval workflow
                - proposePlan=false for normal Q&A or recommendations (e.g. "what are the best tools for git repos?")
                - Use chat title + brief history for context.
                """;

        String historyBlock = (chatHistory == null || chatHistory.isEmpty())
                ? ""
                : String.join(" | ", chatHistory.stream().limit(10).toList());

        String user = """
                ChatTitle: %s
                History: %s
                UserRequest: %s
                """.formatted(nullToEmpty(chatTitle), historyBlock, nullToEmpty(userMessage));

        String raw = callChatCompletion(
                props.getOpenrouter().getFastModel(),
                List.of(
                        msg("system", system),
                        msg("user", user)
                ),
                50,
                0.0
        );

        String json = extractFirstJsonObject(raw);
        if (json == null) return false;

        try {
            JsonNode node = MAPPER.readTree(json);
            return node.path("proposePlan").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    // ---- Chat completion caller ----

    private String callChatCompletion(String model, List<Map<String, String>> messages) {
        return callChatCompletion(model, messages, 500, 0.4);
    }

    private String callChatCompletion(String model,
                                      List<Map<String, String>> messages,
                                      int maxTokens,
                                      double temperature) {

        var body = Map.of(
                "model", model,
                "messages", messages,
                "temperature", temperature,
                "max_tokens", maxTokens
        );

        ChatCompletionResponse res = rest.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(ChatCompletionResponse.class);

        if (res == null || res.choices == null || res.choices.isEmpty()
                || res.choices.get(0).message == null
                || res.choices.get(0).message.content == null) {
            return "";
        }

        return res.choices.get(0).message.content.trim();
    }

    // ---- JSON parsing helpers ----

    private static String parseTitleJsonOrFallback(String raw, String firstMessage) {
        String json = extractFirstJsonObject(raw);
        if (json == null) return fallbackTitle(firstMessage);

        try {
            JsonNode node = MAPPER.readTree(json);
            JsonNode titleNode = node.get("title");
            if (titleNode == null || titleNode.asText().isBlank()) return fallbackTitle(firstMessage);
            return sanitizeTitle(titleNode.asText());
        } catch (Exception e) {
            return fallbackTitle(firstMessage);
        }
    }

    private static String parseReplyJsonOrFallback(String raw) {
        String json = extractFirstJsonObject(raw);
        if (json == null) {
            return (raw == null || raw.isBlank()) ? "⚠️ LLM returned an empty response" : raw.trim();
        }

        try {
            JsonNode node = MAPPER.readTree(json);
            JsonNode replyNode = node.get("reply");
            if (replyNode == null || replyNode.asText().isBlank()) {
                return "⚠️ LLM returned an empty response";
            }
            return replyNode.asText().trim();
        } catch (Exception e) {
            return (raw == null || raw.isBlank()) ? "⚠️ LLM returned an empty response" : raw.trim();
        }
    }

    private static String extractFirstJsonObject(String raw) {
        if (raw == null) return null;

        int start = raw.indexOf('{');
        if (start < 0) return null;

        int depth = 0;
        for (int i = start; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return raw.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private static String sanitizeTitle(String t) {
        if (t == null) return "New chat";

        t = t.replaceAll("\\R", " ");
        t = t.replaceAll("\\s+", " ").trim();
        t = t.replaceAll("[\\p{Punct}]+$", "").trim();

        if (t.isBlank()) return "New chat";
        if (t.length() > 60) t = t.substring(0, 60).trim();
        return t;
    }

    private static String fallbackTitle(String firstMessage) {
        if (firstMessage == null) return "New chat";
        String cleaned = firstMessage.replaceAll("\\s+", " ").trim();
        if (cleaned.isBlank()) return "New chat";
        return cleaned.length() <= 42 ? cleaned : cleaned.substring(0, 42) + "…";
    }

    private static Map<String, String> msg(String role, String content) {
        return Map.of("role", role, "content", content);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public static class ChatCompletionResponse {
        public List<Choice> choices;

        public static class Choice {
            public Message message;
        }

        public static class Message {
            public String role;
            public String content;
        }
    }
}
