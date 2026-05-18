package com.next2me.next2cash.dto;

import lombok.Data;

/**
 * Incoming payload for POST /api/ai/analyze.
 */
@Data
public class AiAnalysisRequest {

    /** The user's question in natural language (Greek/English mix is fine). */
    private String question;

    /** One of the 9 analysis types (e.g. "Executive Summary", "Investor Report"). */
    private String analysisType;

    /** Entity scope: 'next2me', 'house', or 'all'. */
    private String entityScope;

    /**
     * Predefined date range labels:
     *   'last_30_days', 'last_3_months', 'ytd', 'last_year',
     *   'year_2025', 'year_2024', ..., 'all_data', 'custom'
     */
    private String dateRange;

    /** Only used when dateRange == 'custom' (YYYY-MM-DD). */
    private String customFrom;
    private String customTo;

    /**
     * Entry-mode filter for the dataset given to the AI:
     *   "ACTUAL"  - only real transactions (default if null)
     *   "PLANNED" - only budget / forecast transactions
     *   "ALL"     - both (no entry_mode filter)
     * Added by S70 (May 2026).
     */
    private String entryMode;
}
