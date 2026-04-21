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

    public AnthropicClient(
            @Value("${ANTHROPIC_API_KEY:}") String apiKey,
            @Value("${next2cash.ai.model:claude-sonnet-4-5-20250929}") String model
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.webClient = WebClient.builder()
            .baseUrl("https://api.anthropic.com/v1")
            .defaultHeader("anthropic-version", "2023-06-01")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();
    }

    public ClaudeResult createMessage(String systemPrompt, String userMessage, int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY not configured");
        }

        ObjectNode req = mapper.createObjectNode();
        req.put("model", model);
        req.put("max_tokens", maxTokens);
        req.put("system", systemPrompt);

        ArrayNode messages = req.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);

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

            String answer = "";
            JsonNode content = resp.path("content");
            if (content.isArray() && content.size() > 0) {
                JsonNode first = content.get(0);
                if (first.has("text")) {
                    answer = first.get("text").asText();
                }
            }

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

    @Data
    public static class ClaudeResult {
        private final String answer;
        private final int inputTokens;
        private final int outputTokens;
        private final String modelUsed;
    }
}
