package com.next2me.next2cash.service;

import com.next2me.next2cash.dto.AiCfoAdviceResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for PricingAiAdvisorService JSON parsing and code-fence
 * stripping. These do NOT call the real Anthropic API -- they exercise
 * the pure parsing logic with constructor-injected nulls (the parse
 * helpers do not touch the injected dependencies).
 *
 * Created in S86.8 (May 2026).
 */
class PricingAiAdvisorServiceTest {

    private PricingAiAdvisorService newService() {
        // The parse helpers (parseAnswer, stripCodeFences) do not use the
        // injected client or pricing service, so null is safe here.
        return new PricingAiAdvisorService(null, null);
    }

    @Test
    void parsesWellFormedJson() {
        PricingAiAdvisorService svc = newService();
        String json = "{"
                + "\"summary\": \"Synopsis here\","
                + "\"strategies\": ["
                + "  {\"name\": \"Conservative\", \"monthlyPrice\": \"79 EUR\", \"positioning\": \"p1\", \"tradeoff\": \"t1\", \"bestWhen\": \"b1\"},"
                + "  {\"name\": \"Balanced\", \"monthlyPrice\": \"99 EUR\", \"positioning\": \"p2\", \"tradeoff\": \"t2\", \"bestWhen\": \"b2\"},"
                + "  {\"name\": \"Aggressive\", \"monthlyPrice\": \"149 EUR\", \"positioning\": \"p3\", \"tradeoff\": \"t3\", \"bestWhen\": \"b3\"}"
                + "],"
                + "\"benchmarks\": ["
                + "  {\"metric\": \"LTV:CAC\", \"yours\": \"0.0x\", \"industry\": \">=3x\", \"verdict\": \"risk\"}"
                + "],"
                + "\"recommendations\": [\"r1\", \"r2\", \"r3\"]"
                + "}";

        AiCfoAdviceResponse out = svc.parseAnswer(json);

        assertNotNull(out);
        assertEquals("Synopsis here", out.getSummary());
        assertEquals(3, out.getStrategies().size());
        assertEquals("Conservative", out.getStrategies().get(0).getName());
        assertEquals("79 EUR", out.getStrategies().get(0).getMonthlyPrice());
        assertEquals(1, out.getBenchmarks().size());
        assertEquals("LTV:CAC", out.getBenchmarks().get(0).getMetric());
        assertEquals("risk", out.getBenchmarks().get(0).getVerdict());
        assertEquals(3, out.getRecommendations().size());
        assertEquals("r1", out.getRecommendations().get(0));
    }

    @Test
    void stripsMarkdownCodeFences() {
        PricingAiAdvisorService svc = newService();
        String fenced = "```json\n{\"summary\": \"X\", \"strategies\": [], \"benchmarks\": [], \"recommendations\": []}\n```";

        AiCfoAdviceResponse out = svc.parseAnswer(fenced);

        assertNotNull(out);
        assertEquals("X", out.getSummary());
        assertTrue(out.getStrategies().isEmpty());
    }

    @Test
    void fallsBackGracefullyOnInvalidJson() {
        PricingAiAdvisorService svc = newService();
        String garbage = "this is not json at all";

        AiCfoAdviceResponse out = svc.parseAnswer(garbage);

        assertNotNull(out);
        // On parse failure the raw text becomes the summary and lists are empty (not null).
        assertEquals(garbage, out.getSummary());
        assertNotNull(out.getStrategies());
        assertTrue(out.getStrategies().isEmpty());
        assertNotNull(out.getBenchmarks());
        assertNotNull(out.getRecommendations());
    }

    @Test
    void stripCodeFencesHandlesNullAndEmpty() {
        PricingAiAdvisorService svc = newService();
        assertEquals("{}", svc.stripCodeFences(null));
        assertEquals("{}", svc.stripCodeFences("   "));
    }
}
