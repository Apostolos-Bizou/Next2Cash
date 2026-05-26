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
 * S86.15: extended with a list of real competitors (market anchors) that the
 * model can populate when web search is enabled. The field is optional and
 * defaults to an empty list, so existing clients are unaffected.
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
    private List<Strategy>   strategies;
    private List<Benchmark>  benchmarks;
    private List<String>     recommendations;
    private List<Competitor> competitors;   // S86.15: real market anchors (may be empty)
    private boolean marketDataUsed;          // S86.15: true when web search backed the answer

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

    // S86.15 -----------------------------------------------------------------
    public List<Competitor> getCompetitors() { return competitors; }
    public void setCompetitors(List<Competitor> v) { this.competitors = v; }

    public boolean isMarketDataUsed() { return marketDataUsed; }
    public void setMarketDataUsed(boolean v) { this.marketDataUsed = v; }

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

    /**
     * S86.15: a single real-world competitor surfaced via web search.
     * All fields are plain strings to preserve the model's formatting and
     * any qualifier it adds (e.g. "from 49 EUR/mo, annual billing").
     */
    public static class Competitor {
        private String name;     // company / product name, e.g. "Pleo"
        private String product;  // the comparable offering
        private String price;    // observed price point, as a string (summary, e.g. "from 45 EUR")
        private String note;     // short qualifier / source hint
        private List<Tier> tiers; // S86.15-T: full per-package pricing (may be empty/null)

        public Competitor() {
        }

        public String getName() { return name; }
        public void setName(String v) { this.name = v; }

        public String getProduct() { return product; }
        public void setProduct(String v) { this.product = v; }

        public String getPrice() { return price; }
        public void setPrice(String v) { this.price = v; }

        public String getNote() { return note; }
        public void setNote(String v) { this.note = v; }

        // S86.15-T: detailed pricing tiers/packages for this competitor.
        public List<Tier> getTiers() { return tiers; }
        public void setTiers(List<Tier> v) { this.tiers = v; }
    }

    /**
     * S86.15-T: a single pricing package/plan offered by a competitor.
     * Captures the full billing policy so the UI can show each plan separately
     * instead of collapsing everything into one averaged price.
     */
    public static class Tier {
        private String name;     // plan name, e.g. "Free" | "Starter" | "Pro" | "Enterprise"
        private String price;    // price for this plan, as a string, e.g. "90 EUR"
        private String billing;  // billing cadence, e.g. "monthly" | "annual" | "per user/mo"
        private String features; // short note on what this plan includes

        public Tier() {
        }

        public String getName() { return name; }
        public void setName(String v) { this.name = v; }

        public String getPrice() { return price; }
        public void setPrice(String v) { this.price = v; }

        public String getBilling() { return billing; }
        public void setBilling(String v) { this.billing = v; }

        public String getFeatures() { return features; }
        public void setFeatures(String v) { this.features = v; }
    }
}
