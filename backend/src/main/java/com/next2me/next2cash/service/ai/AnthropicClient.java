package com.next2me.next2cash.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Thin HTTP client for Anthropic Messages API.
 * Uses Spring WebClient (webflux) with 3-minute timeout.
 */
@Service
@Slf4j
public class AnthropicClient {

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String model;

    // S86.15: optional web_search tool support. Disabled by default so the
    // existing behaviour is unchanged; flipping the app setting to true turns
    // on real market lookups for callers that explicitly request it.
    private final boolean webSearchEnabled;
    private final int webSearchMaxUses;

    public AnthropicClient(
            @Value("${ANTHROPIC_API_KEY:}") String apiKey,
            @Value("${next2cash.ai.model:claude-sonnet-4-5-20250929}") String model,
            @Value("${next2cash.ai.websearch.enabled:false}") boolean webSearchEnabled,
            @Value("${next2cash.ai.websearch.maxUses:5}") int webSearchMaxUses
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.webSearchEnabled = webSearchEnabled;
        this.webSearchMaxUses = webSearchMaxUses;
        this.webClient = WebClient.builder()
            .baseUrl("https://api.anthropic.com/v1")
            .defaultHeader("anthropic-version", "2023-06-01")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();
    }

    /**
     * Original signature -- unchanged behaviour, no web search.
     * Existing callers (e.g. AiAnalysisService) keep working exactly as before.
     */
    public ClaudeResult createMessage(String systemPrompt, String userMessage, int maxTokens) {
        return createMessage(systemPrompt, userMessage, maxTokens, false);
    }

    /**
     * S86.15: overload that can attach the Anthropic web_search tool so Claude
     * can ground its answer in real-time market data.
     *
     * Web search is attached ONLY when both:
     *   - the caller passes enableWebSearch = true, AND
     *   - the app setting next2cash.ai.websearch.enabled = true.
     *
     * When web search is off this behaves identically to the original method.
     */
    public ClaudeResult createMessage(String systemPrompt, String userMessage,
                                      int maxTokens, boolean enableWebSearch) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY not configured");
        }

        boolean attachWebSearch = enableWebSearch && webSearchEnabled;

        ObjectNode req = mapper.createObjectNode();
        req.put("model", model);
        req.put("max_tokens", maxTokens);
        req.put("system", systemPrompt);

        ArrayNode messages = req.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);

        if (attachWebSearch) {
            // tools: [{ "type": "web_search_20250305", "name": "web_search", "max_uses": N }]
            // Minimal, schema-safe tool block. user_location is intentionally
            // omitted: it requires city+region+country+timezone together, and a
            // partial object triggers HTTP 400. European focus is steered via the
            // prompt instead ("prefer European/EUR sources").
            ArrayNode tools = req.putArray("tools");
            ObjectNode webSearch = tools.addObject();
            webSearch.put("type", "web_search_20250305");
            webSearch.put("name", "web_search");
            webSearch.put("max_uses", webSearchMaxUses);
            log.info("Anthropic call WITH web_search tool (max_uses={})", webSearchMaxUses);
        }

        try {
            JsonNode resp = webClient.post()
                .uri("/messages")
                .header("x-api-key", apiKey)
                .bodyValue(req)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofMinutes(3))
                .block();

            if (resp == null) {
                throw new RuntimeException("Empty response from Anthropic API");
            }

            // S86.15: when web_search is active the response is a SEQUENCE of
            // content blocks (server_tool_use, web_search_tool_result, and one
            // or more text blocks). We must concatenate ALL text blocks, not
            // just content[0], otherwise the final answer is lost. This is also
            // fully backward-compatible with the single-block case.
            String answer = extractText(resp.path("content"));

            int inputTokens = resp.path("usage").path("input_tokens").asInt(0);
            int outputTokens = resp.path("usage").path("output_tokens").asInt(0);
            String modelUsed = resp.path("model").asText(this.model);

            log.info("Anthropic call OK: {} in + {} out tokens", inputTokens, outputTokens);

            return new ClaudeResult(answer, inputTokens, outputTokens, modelUsed);

        } catch (Exception e) {
            log.error("Anthropic API call failed: {}", e.getMessage());
            throw new RuntimeException("AI service unavailable: " + e.getMessage(), e);
        }
    }

    /**
     * Concatenate the text from every "text" content block. Blocks of other
     * types (server_tool_use, web_search_tool_result) are skipped. Works for
     * both the single-block (no tools) and multi-block (web search) cases.
     */
    private String extractText(JsonNode content) {
        if (content == null || !content.isArray() || content.size() == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : content) {
            if (block.has("text") && "text".equals(block.path("type").asText("text"))) {
                sb.append(block.get("text").asText());
            } else if (block.has("text") && block.path("type").isMissingNode()) {
                // Defensive: a block carrying text with no explicit type.
                sb.append(block.get("text").asText());
            }
        }
        return sb.toString();
    }

    @Data
    public static class ClaudeResult {
        private final String answer;
        private final int inputTokens;
        private final int outputTokens;
        private final String modelUsed;
    }
}
