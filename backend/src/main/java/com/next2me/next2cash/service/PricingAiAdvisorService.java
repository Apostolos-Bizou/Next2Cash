package com.next2me.next2cash.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.next2me.next2cash.dto.AiCfoAdviceResponse;
import com.next2me.next2cash.dto.PricingCalculatorResponse;
import com.next2me.next2cash.service.ai.AnthropicClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * S86.15: when web search is enabled (next2cash.ai.websearch.enabled=true),
 * the prompt asks Claude to find real competitor pricing on the open web, and
 * the parser fills the new competitors[] list. With the setting off, behaviour
 * is identical to S86.8 (cost/benchmark advice from the model's own knowledge).
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
    private static final int    MAX_TOKENS_WEB = 6000; // web+tiers answers are much longer
    private static final long   CACHE_TTL_MILLIS = 24L * 60L * 60L * 1000L; // 24 hours

    private final AnthropicClient anthropicClient;
    private final PricingCalculatorService pricingService;

    // S86.15: mirror of the client-side flag, used to decide which prompt to
    // build and whether to request the web_search tool. Defaults to false so
    // the deployed behaviour is unchanged until the setting is flipped.
    @Value("${next2cash.ai.websearch.enabled:false}")
    private boolean webSearchEnabled;

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
        // S86.15: web-search state is part of the cache key so cached cost-only
        // answers are not mistaken for market-anchored ones (and vice versa).
        String cacheKey = (projectId == null ? "GROUP" : projectId.toString())
                + ":" + (entityId == null ? "all" : entityId.toString())
                + ":" + (targetMargin == null ? "default" : targetMargin.toPlainString())
                + ":ws=" + webSearchEnabled;

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
        int tokenBudget = webSearchEnabled ? MAX_TOKENS_WEB : MAX_TOKENS;

        AnthropicClient.ClaudeResult claude = anthropicClient.createMessage(
                systemPrompt, userMessage, tokenBudget, webSearchEnabled);

        AiCfoAdviceResponse response = parseAnswer(claude.getAnswer());
        response.setSuccess(true);
        response.setFromCache(false);
        response.setMarketDataUsed(webSearchEnabled);
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
        // Shared base persona + JSON contract.
        StringBuilder sb = new StringBuilder();
        sb.append("You are a world-class SaaS CFO advisor for Next2Me Group, a small ")
          .append("European software holding. You give concise, practical pricing advice ")
          .append("grounded in standard SaaS finance (CAC payback, LTV:CAC, Rule of 40, ")
          .append("gross margin, churn). Be honest about weak metrics. ");

        if (webSearchEnabled) {
            // S86.15: market-anchored variant. Ask the model to actually search
            // the web for real competitor pricing, then anchor its strategies
            // to what the market charges -- not only to internal cost.
            sb.append("Use the web_search tool to find REAL, current pricing of ")
              .append("competing products/services in the same category as the product ")
              .append("described below (prefer European/EUR sources where relevant). ")
              .append("Base your 3 strategies on BOTH the internal cost data AND the ")
              .append("market prices you find. Only list competitors you actually found; ")
              .append("never invent names or prices. ");
        }

        sb.append("Respond ONLY with a single valid JSON object, no markdown, no code fences, ")
          .append("no preamble. The JSON schema is:\n")
          .append("{\n")
          .append("  \"summary\": \"one short executive paragraph (Greek)\",\n")
          .append("  \"strategies\": [\n")
          .append("    {\"name\": \"Conservative\", \"monthlyPrice\": \"...\", ")
          .append("\"positioning\": \"...\", \"tradeoff\": \"...\", \"bestWhen\": \"...\"},\n")
          .append("    {\"name\": \"Balanced\", ...},\n")
          .append("    {\"name\": \"Aggressive\", ...}\n")
          .append("  ],\n")
          .append("  \"benchmarks\": [\n")
          .append("    {\"metric\": \"...\", \"yours\": \"...\", \"industry\": \"...\", ")
          .append("\"verdict\": \"good|watch|risk\"}\n")
          .append("  ],\n");

        if (webSearchEnabled) {
            sb.append("  \"competitors\": [\n")
              .append("    {\"name\": \"...\", \"product\": \"...\", ")
              .append("\"price\": \"short headline price, e.g. 'from 45 EUR'\", ")
              .append("\"note\": \"short qualifier or source hint\", ")
              .append("\"tiers\": [\n")
              .append("      {\"name\": \"plan name e.g. Free/Starter/Pro/Enterprise\", ")
              .append("\"price\": \"price for this plan e.g. '90 EUR'\", ")
              .append("\"billing\": \"monthly|annual|per user/mo|on request\", ")
              .append("\"features\": \"what this plan includes (Greek)\"}\n")
              .append("    ]}\n")
              .append("  ],\n");
        }

        sb.append("  \"recommendations\": [\"actionable point 1\", \"point 2\", \"point 3\"]\n")
          .append("}\n")
          .append("All human-readable text values MUST be in Greek. ")
          .append("Keep each text field short (one sentence). ")
          .append("Provide exactly 3 strategies and 3 to 5 benchmarks.");

        if (webSearchEnabled) {
            sb.append(" Provide 3 to 6 real competitors with their actual observed prices. ")
              .append("For EACH competitor, populate the 'tiers' array with EVERY pricing ")
              .append("plan/package you can find (e.g. Free, Starter, Pro, Enterprise) -- ")
              .append("do NOT collapse them into one averaged price. If a competitor has a ")
              .append("single plan, give one tier. If pricing is 'on request', say so in ")
              .append("that tier's price. Capture the full billing policy (monthly vs annual ")
              .append("vs per-user). Competitor names and plan names stay as found (may be ")
              .append("Latin script); the 'note' and 'features' fields are in Greek.");
        }

        return sb.toString();
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

        if (webSearchEnabled) {
            sb.append("\nFirst search the web for competitor pricing in this product's ")
              .append("category, then give 3 pricing strategies (Conservative / Balanced / ")
              .append("Aggressive) anchored to both cost and market, competitive benchmarks, ")
              .append("the list of real competitors you found, and 3-5 actionable ")
              .append("recommendations. Respond as JSON per the schema. Greek text.");
        } else {
            sb.append("\nGive 3 pricing strategies (Conservative / Balanced / Aggressive), "
                    + "competitive benchmarks for the key SaaS metrics above, and 3-5 "
                    + "actionable recommendations. Respond as JSON per the schema. Greek text.");
        }
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

            // S86.15: parse competitors[] when present. Absent in cost-only mode
            // and in old cached JSON -> stays an empty list, never null.
            List<AiCfoAdviceResponse.Competitor> competitors = new ArrayList<>();
            for (JsonNode c : root.path("competitors")) {
                AiCfoAdviceResponse.Competitor cp = new AiCfoAdviceResponse.Competitor();
                cp.setName(text(c.path("name")));
                cp.setProduct(text(c.path("product")));
                cp.setPrice(text(c.path("price")));
                cp.setNote(text(c.path("note")));

                // S86.15-T: parse per-competitor pricing tiers when present.
                List<AiCfoAdviceResponse.Tier> tiers = new ArrayList<>();
                for (JsonNode t : c.path("tiers")) {
                    AiCfoAdviceResponse.Tier tr = new AiCfoAdviceResponse.Tier();
                    tr.setName(text(t.path("name")));
                    tr.setPrice(text(t.path("price")));
                    tr.setBilling(text(t.path("billing")));
                    tr.setFeatures(text(t.path("features")));
                    tiers.add(tr);
                }
                cp.setTiers(tiers);

                competitors.add(cp);
            }
            out.setCompetitors(competitors);

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
            out.setCompetitors(new ArrayList<>());
            out.setRecommendations(new ArrayList<>());
        }
        return out;
    }

    /**
     * Extract the JSON object from the model answer. Handles three cases:
     *   1. Plain JSON (no fences).
     *   2. JSON wrapped in ```json ... ``` fences at the start.
     *   3. JSON that appears AFTER some preamble text -- this is common when
     *      web search is on, because the model narrates its research first and
     *      then emits the JSON inside a fenced block. (S86.15 fix.)
     *
     * Strategy: if the trimmed text is not already a bare JSON object, locate
     * the first '{' and the last '}' and return that substring. This is robust
     * to leading prose and to ```json fences alike.
     */
    String stripCodeFences(String s) {
        if (s == null) return "{}";
        String t = s.trim();
        if (t.isEmpty()) return "{}";

        // Fast path: already a bare JSON object.
        if (t.startsWith("{") && t.endsWith("}")) {
            return t;
        }

        // General path: pull out the outermost { ... } span, ignoring any
        // surrounding prose or code fences.
        int first = t.indexOf('{');
        int last = t.lastIndexOf('}');
        if (first >= 0 && last > first) {
            return t.substring(first, last + 1);
        }

        // No JSON object found -> let the caller fall back to raw summary.
        return t;
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
