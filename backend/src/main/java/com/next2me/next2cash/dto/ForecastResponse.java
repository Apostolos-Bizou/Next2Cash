package com.next2me.next2cash.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Top-level response for GET /api/forecast.
 *
 * Contains the forecast horizon definition, all virtual entries, and high-level
 * totals so the UI can show summary KPIs without re-aggregating.
 *
 * Created in S85 (Forecast Engine, May 2026).
 */
public record ForecastResponse(
        UUID entityId,
        LocalDate asOf,
        int horizonMonths,
        int horizonDays,

        // Flat list of virtual entries (chronological)
        List<ForecastEntryDTO> entries,

        // Aggregate totals across the horizon (EUR; multi-currency handled later)
        BigDecimal totalExpenses,
        BigDecimal totalIncome,
        BigDecimal netCashFlow,        // income - expenses (negative = burn)
        int entryCount,
        int patternCount,
        int projectScopedCount,
        int opexCount
) {
}
