package com.next2me.next2cash.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One virtual forecast entry. Represents a future occurrence of a recurring
 * transaction, materialized in-memory (never persisted).
 *
 * Fields mirror Transaction model plus denormalized project info so the
 * frontend can render without an extra lookup per row.
 *
 * Created in S85 (Forecast Engine, May 2026).
 */
public record ForecastEntryDTO(
        // Source pattern + mother (so the UI can deep-link back)
        UUID patternId,
        Integer motherTransactionId,

        // Forecast-instance fields (no DB id; never persisted)
        LocalDate date,
        String type,                // income / expense
        BigDecimal amount,
        String description,
        String category,
        String subcategory,
        String counterparty,

        // Project denormalization
        UUID projectId,             // null = OpEx
        String projectName,         // null when projectId is null
        String projectStatus,       // null when projectId is null
        String projectColor,        // null when projectId is null

        // Recurrence metadata
        String patternFrequency,    // DAILY / WEEKLY / MONTHLY / QUARTERLY / YEARLY

        // Confidence inherited from mother (default 100)
        Integer confidencePct,

        // True when projectId is null
        boolean isOpex
) {
}
