package com.next2me.next2cash.controller;

import com.next2me.next2cash.dto.PricingCalculatorResponse;
import com.next2me.next2cash.dto.AiCfoAdviceResponse;
import com.next2me.next2cash.dto.PricingConfigDTO;
import com.next2me.next2cash.model.Project;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.ProjectRepository;
import com.next2me.next2cash.service.PricingCalculatorService;
import com.next2me.next2cash.service.PricingAiAdvisorService;
import com.next2me.next2cash.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * PricingCalculatorController -- REST API for the Pricing Calculator + AI CFO.
 *
 * Created in S86 (May 2026).
 *
 * Endpoints:
 *   GET  /api/pricing-calculator?targetMargin=0.15            -- GROUP mode (all LIVE projects)
 *   GET  /api/pricing-calculator/{projectId}?targetMargin=... -- PROJECT mode (single)
 *   PUT  /api/pricing-calculator/project/{projectId}/config   -- update pricing config
 *
 * Security:
 *   - Authentication required (handled by SecurityFilterChain).
 *   - PROJECT mode: user must have access to the owning entity of the project.
 *   - GROUP mode: any authenticated user can call (returns only LIVE projects).
 */
@RestController
@RequestMapping("/api/pricing-calculator")
@RequiredArgsConstructor
public class PricingCalculatorController {

    private static final Logger log = LoggerFactory.getLogger(PricingCalculatorController.class);

    private static final BigDecimal DEFAULT_TARGET_MARGIN = new BigDecimal("0.15");

    private final PricingCalculatorService pricingService;
    private final PricingAiAdvisorService pricingAiAdvisorService;
    private final ProjectRepository projectRepository;
    private final UserAccessService userAccessService;

    // =====================================================================
    //  GET -- GROUP mode (no projectId)
    // =====================================================================
    @GetMapping
    public ResponseEntity<?> calculateGroup(
            @RequestParam(name = "targetMargin", required = false) BigDecimal targetMargin,
            HttpServletRequest request) {

        try {
            String authHeader = request.getHeader("Authorization");
            User user = userAccessService.getCurrentUser(authHeader);
            if (user == null) {
                return ResponseEntity.status(401).body(errorBody("Unauthenticated"));
            }

            BigDecimal margin = targetMargin != null ? targetMargin : DEFAULT_TARGET_MARGIN;
            log.info("Pricing calculator GROUP mode requested by user {} with margin {}",
                user.getUsername(), margin);

            PricingCalculatorResponse response = pricingService.calculate(null, margin);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorBody(ex.getMessage()));
        } catch (Exception ex) {
            log.error("Pricing calculator GROUP mode failed", ex);
            return ResponseEntity.status(500).body(errorBody(ex.getMessage()));
        }
    }

    // =====================================================================
    //  GET -- PROJECT mode
    // =====================================================================
    @GetMapping("/{projectId}")
    public ResponseEntity<?> calculateProject(
            @PathVariable UUID projectId,
            @RequestParam(name = "targetMargin", required = false) BigDecimal targetMargin,
            HttpServletRequest request) {

        try {
            String authHeader = request.getHeader("Authorization");
            User user = userAccessService.getCurrentUser(authHeader);
            if (user == null) {
                return ResponseEntity.status(401).body(errorBody("Unauthenticated"));
            }

            // Entity-scope check: user must have access to project's owning entity
            Optional<Project> projOpt = projectRepository.findById(projectId);
            if (projOpt.isEmpty()) {
                return ResponseEntity.status(404).body(errorBody("Project not found"));
            }
            Project project = projOpt.get();
            userAccessService.assertCanAccessEntity(user, project.getOwnerEntityId());

            BigDecimal margin = targetMargin != null ? targetMargin : DEFAULT_TARGET_MARGIN;
            log.info("Pricing calculator PROJECT mode for project {} by user {} with margin {}",
                projectId, user.getUsername(), margin);

            PricingCalculatorResponse response = pricingService.calculate(projectId, margin);
            return ResponseEntity.ok(response);
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            return ResponseEntity.status(403).body(errorBody("Access denied for this project"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorBody(ex.getMessage()));
        } catch (Exception ex) {
            log.error("Pricing calculator PROJECT mode failed for projectId={}", projectId, ex);
            return ResponseEntity.status(500).body(errorBody(ex.getMessage()));
        }
    }

    // =====================================================================
    //  PUT -- update pricing config for a project
    // =====================================================================
    @PutMapping("/project/{projectId}/config")
    public ResponseEntity<?> updateConfig(
            @PathVariable UUID projectId,
            @RequestBody PricingConfigDTO body,
            HttpServletRequest request) {

        try {
            String authHeader = request.getHeader("Authorization");
            User user = userAccessService.getCurrentUser(authHeader);
            if (user == null) {
                return ResponseEntity.status(401).body(errorBody("Unauthenticated"));
            }

            Optional<Project> projOpt = projectRepository.findById(projectId);
            if (projOpt.isEmpty()) {
                return ResponseEntity.status(404).body(errorBody("Project not found"));
            }
            Project project = projOpt.get();
            userAccessService.assertCanAccessEntity(user, project.getOwnerEntityId());

            // Partial update: only assign non-null fields from body
            boolean changed = false;
            if (body.getDirectBurnMonthly() != null) {
                project.setDirectBurnMonthly(body.getDirectBurnMonthly());
                changed = true;
            }
            if (body.getOpexAllocationPct() != null) {
                project.setOpexAllocationPct(body.getOpexAllocationPct());
                changed = true;
            }
            if (body.getCurrentMrr() != null) {
                project.setCurrentMrr(body.getCurrentMrr());
                changed = true;
            }
            if (body.getCurrentCustomers() != null) {
                project.setCurrentCustomers(body.getCurrentCustomers());
                changed = true;
            }
            if (body.getCacPerCustomer() != null) {
                project.setCacPerCustomer(body.getCacPerCustomer());
                changed = true;
            }
            if (body.getGrossMarginPct() != null) {
                project.setGrossMarginPct(body.getGrossMarginPct());
                changed = true;
            }
            if (body.getMonthlyChurnPct() != null) {
                project.setMonthlyChurnPct(body.getMonthlyChurnPct());
                changed = true;
            }
            if (body.getAnnualBillingPct() != null) {
                project.setAnnualBillingPct(body.getAnnualBillingPct());
                changed = true;
            }
            if (body.getAnnualDiscountPct() != null) {
                project.setAnnualDiscountPct(body.getAnnualDiscountPct());
                changed = true;
            }
            if (body.getAnnualChurnPct() != null) {
                project.setAnnualChurnPct(body.getAnnualChurnPct());
                changed = true;
            }

            if (!changed) {
                return ResponseEntity.badRequest().body(errorBody("No fields to update"));
            }

            project.setUpdatedAt(LocalDateTime.now());
            projectRepository.save(project);

            log.info("Pricing config updated for project {} by user {}", projectId, user.getUsername());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("projectId", projectId);
            result.put("message", "Pricing config updated");
            return ResponseEntity.ok(result);
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            return ResponseEntity.status(403).body(errorBody("Access denied for this project"));
        } catch (Exception ex) {
            log.error("Pricing config update failed for projectId={}", projectId, ex);
            return ResponseEntity.status(500).body(errorBody(ex.getMessage()));
        }
    }

    // =====================================================================
    //  POST -- AI CFO advice (S86.8)
    //  GROUP mode: POST /api/pricing-calculator/group/ai-advice
    //  PROJECT mode: POST /api/pricing-calculator/{projectId}/ai-advice
    // =====================================================================
    @PostMapping("/group/ai-advice")
    public ResponseEntity<?> aiAdviceGroup(
            @RequestParam(name = "targetMargin", required = false) BigDecimal targetMargin,
            HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            User user = userAccessService.getCurrentUser(authHeader);
            if (user == null) {
                return ResponseEntity.status(401).body(errorBody("Unauthenticated"));
            }
            BigDecimal margin = targetMargin != null ? targetMargin : DEFAULT_TARGET_MARGIN;
            log.info("AI CFO advice GROUP mode requested by user {} with margin {}",
                user.getUsername(), margin);
            AiCfoAdviceResponse advice = pricingAiAdvisorService.advise(null, margin);
            return ResponseEntity.ok(advice);
        } catch (IllegalStateException ex) {
            // ANTHROPIC_API_KEY not configured, etc.
            return ResponseEntity.status(503).body(errorBody(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorBody(ex.getMessage()));
        } catch (Exception ex) {
            log.error("AI CFO advice GROUP mode failed", ex);
            return ResponseEntity.status(500).body(errorBody(ex.getMessage()));
        }
    }

    @PostMapping("/{projectId}/ai-advice")
    public ResponseEntity<?> aiAdviceProject(
            @PathVariable UUID projectId,
            @RequestParam(name = "targetMargin", required = false) BigDecimal targetMargin,
            HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            User user = userAccessService.getCurrentUser(authHeader);
            if (user == null) {
                return ResponseEntity.status(401).body(errorBody("Unauthenticated"));
            }
            Optional<Project> projOpt = projectRepository.findById(projectId);
            if (projOpt.isEmpty()) {
                return ResponseEntity.status(404).body(errorBody("Project not found"));
            }
            Project project = projOpt.get();
            userAccessService.assertCanAccessEntity(user, project.getOwnerEntityId());

            BigDecimal margin = targetMargin != null ? targetMargin : DEFAULT_TARGET_MARGIN;
            log.info("AI CFO advice PROJECT mode for project {} by user {} with margin {}",
                projectId, user.getUsername(), margin);
            AiCfoAdviceResponse advice = pricingAiAdvisorService.advise(projectId, margin);
            return ResponseEntity.ok(advice);
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            return ResponseEntity.status(403).body(errorBody("Access denied for this project"));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(503).body(errorBody(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorBody(ex.getMessage()));
        } catch (Exception ex) {
            log.error("AI CFO advice PROJECT mode failed for projectId={}", projectId, ex);
            return ResponseEntity.status(500).body(errorBody(ex.getMessage()));
        }
    }

    private Map<String, Object> errorBody(String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("success", false);
        err.put("error", message);
        return err;
    }
}
