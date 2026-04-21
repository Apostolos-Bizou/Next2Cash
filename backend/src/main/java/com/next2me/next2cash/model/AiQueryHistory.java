package com.next2me.next2cash.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * AI Query History: stores every AI analysis query for audit + cost tracking.
 * Created in Session #35 for the AI Analysis feature.
 */
@Entity
@Table(name = "ai_query_history")
@Data
@NoArgsConstructor
public class AiQueryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "entity_scope", nullable = false)
    private String entityScope;           // 'next2me', 'house', 'all'

    @Column(name = "analysis_type", nullable = false)
    private String analysisType;

    @Column(name = "date_range_label", nullable = false)
    private String dateRangeLabel;

    @Column(name = "date_from")
    private LocalDate dateFrom;

    @Column(name = "date_to")
    private LocalDate dateTo;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(nullable = false, columnDefinition = "text")
    private String answer;

    @Column(name = "model_used", nullable = false)
    private String modelUsed;

    @Column(nullable = false)
    private String tier;                  // 'tier1_summary', 'tier2_range', 'tier3_full'

    @Column(name = "rows_analyzed", nullable = false)
    private Integer rowsAnalyzed = 0;

    @Column(name = "input_tokens", nullable = false)
    private Integer inputTokens = 0;

    @Column(name = "output_tokens", nullable = false)
    private Integer outputTokens = 0;

    @Column(name = "cost_usd", nullable = false, precision = 10, scale = 6)
    private BigDecimal costUsd = BigDecimal.ZERO;

    @Column(name = "cost_eur", nullable = false, precision = 10, scale = 6)
    private BigDecimal costEur = BigDecimal.ZERO;

    @Column(name = "processing_time_ms", nullable = false)
    private Integer processingTimeMs = 0;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
