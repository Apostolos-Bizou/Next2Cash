package com.next2me.next2cash.controller;

import com.next2me.next2cash.dto.AiAnalysisRequest;
import com.next2me.next2cash.dto.AiAnalysisResponse;
import com.next2me.next2cash.model.AiQueryHistory;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.AiQueryHistoryRepository;
import com.next2me.next2cash.service.UserAccessService;
import com.next2me.next2cash.service.ai.AiAnalysisService;
import com.next2me.next2cash.service.ai.ReportExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;
    private final UserAccessService userAccessService;
    private final ReportExportService reportExportService;
    private final AiQueryHistoryRepository historyRepo;

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody AiAnalysisRequest request) {

        User user = userAccessService.getCurrentUser(authHeader);
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            return ResponseEntity.status(403).body(Map.of("success", false, "error", "forbidden_ai_requires_admin"));
        }
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "question_required"));
        }
        if (request.getAnalysisType() == null || request.getAnalysisType().isBlank()) request.setAnalysisType("Πλήρης Ανάλυση");
        if (request.getEntityScope() == null) request.setEntityScope("all");
        if (request.getDateRange() == null) request.setDateRange("ytd");

        log.info("AI analyze from {}: type={}, scope={}, range={}",
                user.getUsername(), request.getAnalysisType(), request.getEntityScope(), request.getDateRange());

        try {
            return ResponseEntity.ok(aiAnalysisService.analyze(request, user));
        } catch (IllegalStateException e) {
            log.error("AI config error: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("success", false, "error", "ai_not_configured", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("AI analysis failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "ai_analysis_failed", "message", e.getMessage()));
        }
    }

    @GetMapping("/export/{historyId}/pdf")
    public ResponseEntity<?> exportPdf(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long historyId) {

        User user = userAccessService.getCurrentUser(authHeader);
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) return ResponseEntity.status(403).build();

        Optional<AiQueryHistory> opt = historyRepo.findById(historyId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        AiQueryHistory h = opt.get();
        if (!h.getUserId().equals(user.getId())) return ResponseEntity.status(403).build();

        try {
            byte[] pdf = reportExportService.generatePdf(h);
            String filename = "Next2Cash_AI_" + safeForFilename(h.getAnalysisType()) + "_" + historyId + ".pdf";
            String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                    .body(pdf);
        } catch (Exception e) {
            log.error("PDF export failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "pdf_export_failed"));
        }
    }

    @GetMapping("/export/{historyId}/docx")
    public ResponseEntity<?> exportDocx(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long historyId) {

        User user = userAccessService.getCurrentUser(authHeader);
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) return ResponseEntity.status(403).build();

        Optional<AiQueryHistory> opt = historyRepo.findById(historyId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        AiQueryHistory h = opt.get();
        if (!h.getUserId().equals(user.getId())) return ResponseEntity.status(403).build();

        try {
            byte[] docx = reportExportService.generateWord(h);
            String filename = "Next2Cash_AI_" + safeForFilename(h.getAnalysisType()) + "_" + historyId + ".docx";
            String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                    .body(docx);
        } catch (Exception e) {
            log.error("DOCX export failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "docx_export_failed"));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(@RequestHeader("Authorization") String authHeader) {
        User user = userAccessService.getCurrentUser(authHeader);
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(Map.of("success", true, "feature", "ai_analysis", "status", "ready"));
    }

    private String safeForFilename(String s) {
        if (s == null) return "report";
        return s.replaceAll("[^a-zA-Z0-9_-]", "_").replaceAll("_+", "_");
    }
}
