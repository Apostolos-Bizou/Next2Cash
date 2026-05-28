package com.next2me.next2cash.controller;

import com.next2me.next2cash.dto.ForecastResponse;
import com.next2me.next2cash.dto.ScenarioComparisonResponse;
import com.next2me.next2cash.service.ForecastService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Forecast endpoints -- S85 Forecast Engine, extended in S98 with scenarios.
 *
 * GET /api/forecast?entityId=&lt;uuid&gt;&horizonMonths=24&scenarioId=&lt;uuid&gt;
 *   Returns a flat list of virtual forecast entries for the given entity over
 *   the next N months (default 24, max 120). The optional scenarioId applies
 *   that scenario's revenue/expense adjustment percentages to future flows.
 *   Omitting scenarioId (or passing the Baseline scenario) yields the
 *   unadjusted forecast. Auth + entity-level access enforced by the default
 *   security pipeline.
 *
 * GET /api/forecast/compare?entityId=&lt;uuid&gt;&horizonMonths=24
 *   Runs the forecast once per active scenario of the entity and returns all
 *   their totals side-by-side, with per-month cumulative net curves. Used by
 *   the Scenario Comparison screen (CashPlanning spec section 5.8).
 */
@RestController
@RequestMapping("/api/forecast")
@RequiredArgsConstructor
public class ForecastController {

    private static final Logger log = LoggerFactory.getLogger(ForecastController.class);

    private final ForecastService forecastService;

    @GetMapping
    public ResponseEntity<ForecastResponse> getForecast(
            @RequestParam("entityId") UUID entityId,
            @RequestParam(value = "horizonMonths", required = false, defaultValue = "24")
            int horizonMonths,
            @RequestParam(value = "scenarioId", required = false) UUID scenarioId) {

        log.info("GET /api/forecast entityId={} horizonMonths={} scenarioId={}",
                entityId, horizonMonths, scenarioId);
        ForecastResponse response = forecastService.generateForecast(entityId, horizonMonths, scenarioId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/compare")
    public ResponseEntity<ScenarioComparisonResponse> compareScenarios(
            @RequestParam("entityId") UUID entityId,
            @RequestParam(value = "horizonMonths", required = false, defaultValue = "24")
            int horizonMonths) {

        log.info("GET /api/forecast/compare entityId={} horizonMonths={}", entityId, horizonMonths);
        ScenarioComparisonResponse response = forecastService.compareScenarios(entityId, horizonMonths);
        return ResponseEntity.ok(response);
    }
}
