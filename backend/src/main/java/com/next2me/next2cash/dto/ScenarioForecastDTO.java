package com.next2me.next2cash.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One scenario's forecast totals within a comparison response.
 *
 * Produced by GET /api/forecast/compare. Each row corresponds to one active
 * scenario of the entity, with the forecast re-run under that scenario's
 * revenue/expense adjustment percentages. The {@code netVsBaseline} field is
 * the difference of this scenario's net cash flow against the Baseline
 * scenario's net (positive = better than baseline).
 *
 * cumulativeNet holds the running net cash flow at the end of each month
 * across the horizon (index 0 = end of month 0), used to draw the comparison
 * curves on the frontend.
 *
 * Session: S98.
 */
public record ScenarioForecastDTO(
        UUID scenarioId,
        String name,
        String scenarioType,
        String color,
        BigDecimal revenueAdjustPct,
        BigDecimal expenseAdjustPct,
        boolean isBaseline,

        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal netCashFlow,
        BigDecimal netVsBaseline,

        java.util.List<BigDecimal> cumulativeNet
) {
}
