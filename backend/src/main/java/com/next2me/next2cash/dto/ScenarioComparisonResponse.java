package com.next2me.next2cash.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Top-level response for GET /api/forecast/compare.
 *
 * Runs the forecast once per active scenario of the entity and returns all of
 * their totals side-by-side, plus shared horizon metadata and the month labels
 * used for the cumulative-net curves.
 *
 * Session: S98.
 */
public record ScenarioComparisonResponse(
        UUID entityId,
        LocalDate asOf,
        int horizonMonths,
        List<String> monthLabels,
        List<ScenarioForecastDTO> scenarios
) {
}
