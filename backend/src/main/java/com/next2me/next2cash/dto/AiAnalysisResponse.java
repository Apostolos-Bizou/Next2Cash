package com.next2me.next2cash.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response from POST /api/ai/analyze.
 * Contains the AI's answer plus metadata (cost, tokens, tier, timing).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisResponse {

    private boolean success;
    private Long historyId;          // id in ai_query_history (for later export lookup)
    private String answer;
    private String error;

    // Metadata
    private String tier;             // tier1_summary / tier2_range / tier3_full
    private Integer rowsAnalyzed;
    private Integer inputTokens;
    private Integer outputTokens;
    private BigDecimal costUsd;
    private BigDecimal costEur;
    private Integer processingTimeMs;

    // Echo back the resolved date range (useful for UI display)
    private String entityScope;
    private String dateRangeLabel;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String analysisType;
    private String question;
    private String modelUsed;
}
