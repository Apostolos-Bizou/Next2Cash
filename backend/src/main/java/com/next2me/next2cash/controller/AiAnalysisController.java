package com.next2me.next2cash.controller;

import com.next2me.next2cash.dto.AiAnalysisRequest;
import com.next2me.next2cash.dto.AiAnalysisResponse;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.service.UserAccessService;
import com.next2me.next2cash.service.ai.AiAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;
    private final UserAccessService userAccessService;

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody AiAnalysisRequest request) {

        User user = userAccessService.getCurrentUser(authHeader);
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            return ResponseEntity.status(403).body(Map.of(
                "success", false,
                "error", "forbidden_ai_requires_admin"
            ));
        }

        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "question_required"
            ));
        }
        if (request.getAnalysisType() == null || request.getAnalysisType().isBlank()) {
            request.setAnalysisType("Πλήρης Ανάλυση");
        }
        if (request.getEntityScope() == null) request.setEntityScope("all");
        if (request.getDateRange() == null) request.setDateRange("ytd");

        log.info("AI analyze request from user {}: type={}, scope={}, range={}",
                user.getUsername(), request.getAnalysisType(),
                request.getEntityScope(), request.getDateRange());

        try {
            AiAnalysisResponse response = aiAnalysisService.analyze(request, user);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            log.error("AI config error: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of(
                "success", false,
                "error", "ai_not_configured",
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("AI analysis failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "ai_analysis_failed",
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(@RequestHeader("Authorization") String authHeader) {
        User user = userAccessService.getCurrentUser(authHeader);
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(Map.of(
            "success", true,
            "feature", "ai_analysis",
            "status", "ready"
        ));
    }
}
