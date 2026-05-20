package com.next2me.next2cash.controller;

import com.next2me.next2cash.dto.ForecastResponse;
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
 * Forecast endpoints — S85 Forecast Engine.
 *
 * GET /api/forecast?entityId=&lt;uuid&gt;&horizonMonths=24
 *   Returns a flat list of virtual forecast entries for the given entity
 *   over the next N months (default 24, max 120). Auth required (any role
 *   with entity access); entity-level access is enforced by the default
 *   security pipeline.
 *
 * Phase A (this commit): expenses only, sourced from PLANNED + recurring
 * mother transactions. Income from project.expectedMonthlyRevenue is
 * deferred to Phase B.
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
            int horizonMonths) {

        log.info("GET /api/forecast entityId={} horizonMonths={}", entityId, horizonMonths);
        ForecastResponse response = forecastService.generateForecast(entityId, horizonMonths);
        return ResponseEntity.ok(response);
    }
}
