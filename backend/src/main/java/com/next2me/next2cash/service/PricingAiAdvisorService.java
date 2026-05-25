package com.next2me.next2cash.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.next2me.next2cash.dto.AiCfoAdviceResponse;
import com.next2me.next2cash.dto.PricingCalculatorResponse;
import com.next2me.next2cash.service.ai.AnthropicClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PricingAiAdvisorService -- builds a CFO-grade prompt from the pricing
 * metrics, calls Claude, and parses the JSON answer into a structured
 * AiCfoAdviceResponse.
 *
 * Created in S86.8 (May 2026).
 *
 * Design notes:
 *   - Read-only: nothing is persisted to the database.
 *   - In-memory cache: results are cached for 24h per (projectId + margin)
 *     so re-clicking the button does not re-bill the API. Cache is cleared
 *     on app restart, which is fine.
 *   - Reuses the existing AnthropicClient (same one the AI Analysis uses).
 */
@Service
@RequiredArgsConstructor
public class PricingAiAdvisorService {

    private static final Logger log = LoggerFactory.getLogger(PricingAiAdvisorService.class);

    private static final int    MAX_TOKENS = 2000;
    private static final long   CACHE_TTL_MILLIS = 24L * 60L * 60L * 1000L; // 24 hours

    private final AnthropicClient anthropicClient;
    private final PricingCalculatorService pricingService;

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Generate CFO advice for the given project (or GROUP mode if projectId is null).
     * Serves from the 24h in-memory cache when a fresh entry exists.
     */
    public AiCfoAdviceResponse advise(UUID projectId, BigDecimal targetMargin) {
        return advise(projectId, targetMargin, null);
    }

    // S86.10: entity-aware overload. entityId scopes GROUP advice to a single
    // entity's LIVE projects; included in the cache key so each scope caches
    // independently.
    public AiCfoAdviceResponse advise(UUID projectId, BigDecimal targetMargin, UUID entityId) {
        String cacheKey = (projectId == null ? "GROUP" : projectId.toString())
                + ":" + (entityId == null ? "all" : entityId.toString())
                + ":" + (targetMargin == null ? "default" : targetMargin.toPlainString());

        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MILLIS) {
            log.info("AI CFO advice served from cache for key {}", cacheKey);
            cached.payload.setFromCache(true);
            return cached.payload;
        }

        // Compute fresh pricing metrics (single source of truth = the same service the UI uses).
        PricingCalculatorResponse metrics = pricingService.calculate(projectId, targetMargin, entityId);

        String systemPrompt = buildSystemPrompt();
        String userMessage = buildUserMessage(metrics);

        AnthropicClient.ClaudeResult claude = anthropicClient.createMessage(
                systemPrompt, userMessage, MAX_TOKENS);

        AiCfoAdviceResponse response = parseAnswer(claude.getAnswer());
        response.setSuccess(true);
        response.setFromCache(false);
        response.setModelUsed(claude.getModelUsed());
        response.setGeneratedAt(Instant.now().toString());

        cache.put(cacheKey, new CacheEntry(response, System.currentTimeMillis()));
        log.info("AI CFO advice generated for key {} (model {})", cacheKey, claude.getModelUsed());
        return response;
    }

    // ------------------------------------------------------------------
    //  Prompt building
    // ------------------------------------------------------------------

    private String buildSystemPrompt() {
        return "You are a world-class SaaS CFO advisor for Next2Me Group, a small "
                + "European software holding. You give concise, practical pricing advice "
                + "grounded in standard SaaS finance (CAC payback, LTV:CAC, Rule of 40, "
                + "gross margin, churn). Be honest about weak metrics. "
                + "Respond ONLY with a single valid JSON object, no markdown, no code fences, "
                + "no preamble. The JSON schema is:\n"
                + "{\n"
                + "  \"summary\": \"one short executive paragraph (Greek)\",\n"
                + "  \"strategies\": [\n"
                + "    {\"name\": \"Conservative\", \"monthlyPrice\": \"...\", "
                + "\"positioning\": \"...\", \"tradeoff\": \"...\", \"bestWhen\": \"...\"},\n"
                + "    {\"name\": \"Balanced\", ...},\n"
                + "    {\"name\": \"Aggressive\", ...}\n"
                + "  ],\n"
                + "  \"benchmarks\": [\n"
                + "    {\"metric\": \"...\", \"yours\": \"...\", \"industry\": \"...\", "
                + "\"verdict\": \"good|watch|risk\"}\n"
                + "  ],\n"
                + "  \"recommendations\": [\"actionable point 1\", \"point 2\", \"point 3\"]\n"
                + "}\n"
                + "All human-readable text values MUST be in Greek. "
                + "Keep each text field short (one sentence). "
                + "Provide exactly 3 strategies and 3 to 5 benchmarks.";
    }

    private String buildUserMessage(PricingCalculatorResponse m) {
        StringBuilder sb = new StringBuilder();
        sb.append("Pricing context for: ").append(safe(m.getProjectName()))
                .append(" (mode=").append(safe(m.getMode())).append(").\n\n");
        sb.append("Target profit margin: ").append(pct(m.getTargetMargin())).append("\n");
        sb.append("Total monthly cost: ").append(eur(m.getTotalCost())).append("\n");
        sb.append("  - Direct burn: ").append(eur(m.getDirectBurn())).append("\n");
        sb.append("  - Allocated OpEx: ").append(eur(m.getAllocatedOpex())).append("\n");
        sb.append("Required monthly revenue (at target margin): ")
                .append(eur(m.getRequiredRevenue())).append("\n");
        sb.append("Current MRR: ").append(eur(m.getCurrentMrr())).append("\n");
        sb.append("Gap to target: ").append(eur(m.getGap())).append("\n\n");

        sb.append("Unit economics:\n");
        sb.append("  - ARPU: ").append(eur(m.getArpu())).append("\n");
        sb.append("  - CAC per customer: ").append(eur(m.getCacPerCustomer())).append("\n");
        sb.append("  - CAC payback (months): ").append(plain(m.getCacPaybackMonths())).append("\n");
        sb.append("  - LTV: ").append(eur(m.getLtv())).append("\n");
        sb.append("  - LTV:CAC ratio: ").append(plain(m.getLtvCacRatio())).append("\n");
        sb.append("  - Rule of 40 score: ").append(pct(m.getRuleOf40())).append("\n");
        sb.append("  - Monthly churn: ").append(pct(m.getMonthlyChurnPct())).append("\n");
        sb.append("  - Gross margin: ").append(pct(m.getGrossMarginPct())).append("\n");
        sb.append("  - Churn-adjusted target customers: ")
                .append(m.getChurnAdjustedTargetCustomers()).append("\n");

        sb.append("\nGive 3 pricing strategies (Conservative / Balanced / Aggressive), "
                + "competitive benchmarks for the key SaaS metrics above, and 3-5 "
                + "actionable recommendations. Respond as JSON per the schema. Greek text.");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    //  Parsing
    // ------------------------------------------------------------------

    AiCfoAdviceResponse parseAnswer(String rawAnswer) {
        AiCfoAdviceResponse out = new AiCfoAdviceResponse();
        try {
            String cleaned = stripCodeFences(rawAnswer);
            JsonNode root = jsonMapper.readTree(cleaned);

            out.setSummary(text(root.path("summary")));

            List<AiCfoAdviceResponse.Strategy> strategies = new ArrayList<>();
            for (JsonNode s : root.path("strategies")) {
                AiCfoAdviceResponse.Strategy st = new AiCfoAdviceResponse.Strategy();
                st.setName(text(s.path("name")));
                st.setMonthlyPrice(text(s.path("monthlyPrice")));
                st.setPositioning(text(s.path("positioning")));
                st.setTradeoff(text(s.path("tradeoff")));
                st.setBestWhen(text(s.path("bestWhen")));
                strategies.add(st);
            }
            out.setStrategies(strategies);

            List<AiCfoAdviceResponse.Benchmark> benchmarks = new ArrayList<>();
            for (JsonNode b : root.path("benchmarks")) {
                AiCfoAdviceResponse.Benchmark bm = new AiCfoAdviceResponse.Benchmark();
                bm.setMetric(text(b.path("metric")));
                bm.setYours(text(b.path("yours")));
                bm.setIndustry(text(b.path("industry")));
                bm.setVerdict(text(b.path("verdict")));
                benchmarks.add(bm);
            }
            out.setBenchmarks(benchmarks);

            List<String> recs = new ArrayList<>();
            for (JsonNode r : root.path("recommendations")) {
                recs.add(r.asText());
            }
            out.setRecommendations(recs);

        } catch (Exception ex) {
            log.error("Failed to parse AI CFO advice JSON; returning raw summary fallback", ex);
            out.setSummary(rawAnswer != null ? rawAnswer : "");
            out.setStrategies(new ArrayList<>());
            out.setBenchmarks(new ArrayList<>());
            out.setRecommendations(new ArrayList<>());
        }
        return out;
    }

    /**
     * Strip optional markdown code fences the model might add despite instructions.
     */
    String stripCodeFences(String s) {
        if (s == null) return "{}";
        String t = s.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline >= 0) {
                t = t.substring(firstNewline + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        // Also handle a leading "json" token if any survived.
        t = t.trim();
        return t.isEmpty() ? "{}" : t;
    }

    private static String text(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull() ? "" : n.asText();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String eur(BigDecimal v) {
        return v == null ? "n/a" : v.toPlainString() + " EUR";
    }

    private static String plain(BigDecimal v) {
        return v == null ? "n/a" : v.toPlainString();
    }

    private static String pct(BigDecimal v) {
        return v == null ? "n/a" : v.toPlainString();
    }

    // ------------------------------------------------------------------
    //  Cache holder
    // ------------------------------------------------------------------

    private static final class CacheEntry {
        final AiCfoAdviceResponse payload;
        final long timestamp;
        CacheEntry(AiCfoAdviceResponse payload, long timestamp) {
            this.payload = payload;
            this.timestamp = timestamp;
        }
    }
}
