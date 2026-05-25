package com.next2me.next2cash.dto;

import java.util.List;

/**
 * AiCfoAdviceResponse -- structured output of the AI CFO Advisor.
 *
 * Created in S86.8 (May 2026).
 *
 * Returned by POST /api/pricing-calculator/{projectId}/ai-advice.
 * Holds three pricing strategies (Conservative / Balanced / Aggressive),
 * a list of competitive benchmarks, and a list of actionable recommendations.
 *
 * This is a read-only, display-only payload. It is NOT persisted to the
 * database; results are cached in-memory for 24h per (projectId + margin).
 */
public class AiCfoAdviceResponse {

    private boolean success;
    private String  generatedAt;     // ISO-8601 timestamp of generation
    private boolean fromCache;       // true if served from the 24h cache
    private String  modelUsed;       // e.g. claude-sonnet-4-5-...
    private String  summary;         // one-paragraph executive summary
    private List<Strategy>  strategies;
    private List<Benchmark> benchmarks;
    private List<String>    recommendations;

    public AiCfoAdviceResponse() {
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean v) { this.success = v; }

    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String v) { this.generatedAt = v; }

    public boolean isFromCache() { return fromCache; }
    public void setFromCache(boolean v) { this.fromCache = v; }

    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String v) { this.modelUsed = v; }

    public String getSummary() { return summary; }
    public void setSummary(String v) { this.summary = v; }

    public List<Strategy> getStrategies() { return strategies; }
    public void setStrategies(List<Strategy> v) { this.strategies = v; }

    public List<Benchmark> getBenchmarks() { return benchmarks; }
    public void setBenchmarks(List<Benchmark> v) { this.benchmarks = v; }

    public List<String> getRecommendations() { return recommendations; }
    public void setRecommendations(List<String> v) { this.recommendations = v; }

    /**
     * A single pricing strategy option.
     */
    public static class Strategy {
        private String name;          // "Conservative" | "Balanced" | "Aggressive"
        private String monthlyPrice;  // string to preserve the model's formatting, e.g. "79 EUR/mo"
        private String positioning;   // short market positioning sentence
        private String tradeoff;      // what you gain vs what you give up
        private String bestWhen;      // when this strategy fits best

        public Strategy() {
        }

        public String getName() { return name; }
        public void setName(String v) { this.name = v; }

        public String getMonthlyPrice() { return monthlyPrice; }
        public void setMonthlyPrice(String v) { this.monthlyPrice = v; }

        public String getPositioning() { return positioning; }
        public void setPositioning(String v) { this.positioning = v; }

        public String getTradeoff() { return tradeoff; }
        public void setTradeoff(String v) { this.tradeoff = v; }

        public String getBestWhen() { return bestWhen; }
        public void setBestWhen(String v) { this.bestWhen = v; }
    }

    /**
     * A single competitive / industry benchmark.
     */
    public static class Benchmark {
        private String metric;   // e.g. "LTV:CAC ratio"
        private String yours;    // current value for this project/group
        private String industry; // typical industry range
        private String verdict;  // "good" | "watch" | "risk" (plain text)

        public Benchmark() {
        }

        public String getMetric() { return metric; }
        public void setMetric(String v) { this.metric = v; }

        public String getYours() { return yours; }
        public void setYours(String v) { this.yours = v; }

        public String getIndustry() { return industry; }
        public void setIndustry(String v) { this.industry = v; }

        public String getVerdict() { return verdict; }
        public void setVerdict(String v) { this.verdict = v; }
    }
}
