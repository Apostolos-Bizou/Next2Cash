package com.next2me.next2cash.controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S86.2.3 — Pricing Calculator Migration Controller.
 *
 * Applies the V2026_05_20_001 DDL for the Pricing Calculator + AI CFO Advisor.
 * Idempotent: ALL statements use IF NOT EXISTS / DROP IF EXISTS, safe to re-run.
 *
 * Endpoints (ADMIN role required, manual check pattern as in MigrationStatsController):
 *   POST /api/admin/pricing-migration/apply  -- apply the DDL
 *   GET  /api/admin/pricing-migration/status -- check which columns already exist
 *
 * NOTE: This controller can be safely left in production. The migration is
 * idempotent and useful as a re-apply / verify tool for ops.
 */
@RestController
@RequestMapping("/api/admin/pricing-migration")
public class PricingMigrationController {

    private static final Logger log = LoggerFactory.getLogger(PricingMigrationController.class);

    @PersistenceContext
    private EntityManager em;

    /** The 10 columns we expect after migration. Used by /status. */
    private static final List<String> EXPECTED_COLUMNS = Arrays.asList(
        "direct_burn_monthly",
        "opex_allocation_pct",
        "current_mrr",
        "current_customers",
        "cac_per_customer",
        "gross_margin_pct",
        "monthly_churn_pct",
        "annual_billing_pct",
        "annual_discount_pct",
        "annual_churn_pct"
    );

    // ------------------------------------------------------------
    //  GET /status -- read-only check
    // ------------------------------------------------------------
    @GetMapping("/status")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> status() {
        ResponseEntity<Map<String, Object>> authError = requireAdmin();
        if (authError != null) return authError;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("expectedColumns", EXPECTED_COLUMNS);

        List<String> present = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String col : EXPECTED_COLUMNS) {
            String sql =
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_name = 'projects' AND column_name = ?1";
            Number count = (Number) em.createNativeQuery(sql)
                .setParameter(1, col)
                .getSingleResult();
            if (count.longValue() > 0) {
                present.add(col);
            } else {
                missing.add(col);
            }
        }

        result.put("present", present);
        result.put("presentCount", present.size());
        result.put("missing", missing);
        result.put("missingCount", missing.size());
        result.put("ready", missing.isEmpty());
        return ResponseEntity.ok(result);
    }

    // ------------------------------------------------------------
    //  POST /apply -- DDL execution in a single transaction
    // ------------------------------------------------------------
    @PostMapping("/apply")
    @Transactional
    public ResponseEntity<Map<String, Object>> apply() {
        ResponseEntity<Map<String, Object>> authError = requireAdmin();
        if (authError != null) return authError;

        log.info("=== S86.2.3 Pricing Calculator migration APPLY started ===");

        List<String> executed = new ArrayList<>();
        List<Map<String, String>> errors = new ArrayList<>();

        // ---- 10 ADD COLUMN statements ----
        String[] addColumns = new String[] {
            "ALTER TABLE projects ADD COLUMN IF NOT EXISTS direct_burn_monthly   DECIMAL(15,2)",
            "ALTER TABLE projects ADD COLUMN IF NOT EXISTS opex_allocation_pct   DECIMAL(5,2)  DEFAULT 0",
            "ALTER TABLE projects ADD COLUMN IF NOT EXISTS current_mrr           DECIMAL(15,2) DEFAULT 0",
            "ALTER TABLE projects ADD COLUMN IF NOT EXISTS current_customers     INTEGER       DEFAULT 0",
            "ALTER TABLE projects ADD COLUMN IF NOT EXISTS cac_per_customer      DECIMAL(15,2) DEFAULT 0",
            "ALTER TABLE projects ADD COLUMN IF NOT EXISTS gross_margin_pct      DECIMAL(5,2)  DEFAULT 75.00",
            "ALTER TABLE projects ADD COLUMN IF NOT EXISTS monthly_churn_pct     DECIMAL(5,2)  DEFAULT 3.00",
            "ALTER TABLE projects ADD COLUMN IF NOT EXISTS annual_billing_pct    DECIMAL(5,2)  DEFAULT 0",
            "ALTER TABLE projects ADD COLUMN IF NOT EXISTS annual_discount_pct   DECIMAL(5,2)  DEFAULT 15.00",
            "ALTER TABLE projects ADD COLUMN IF NOT EXISTS annual_churn_pct      DECIMAL(5,2)  DEFAULT 0.50"
        };

        for (String stmt : addColumns) {
            executeStmt(stmt, executed, errors);
        }

        // ---- 9 CHECK constraints (drop-then-add for idempotency) ----
        String[][] constraints = new String[][] {
            { "chk_opex_allocation_pct",
              "ALTER TABLE projects ADD CONSTRAINT chk_opex_allocation_pct CHECK (opex_allocation_pct >= 0 AND opex_allocation_pct <= 100)" },
            { "chk_gross_margin_pct",
              "ALTER TABLE projects ADD CONSTRAINT chk_gross_margin_pct CHECK (gross_margin_pct >= 0 AND gross_margin_pct <= 100)" },
            { "chk_monthly_churn_pct",
              "ALTER TABLE projects ADD CONSTRAINT chk_monthly_churn_pct CHECK (monthly_churn_pct >= 0 AND monthly_churn_pct <= 100)" },
            { "chk_annual_billing_pct",
              "ALTER TABLE projects ADD CONSTRAINT chk_annual_billing_pct CHECK (annual_billing_pct >= 0 AND annual_billing_pct <= 100)" },
            { "chk_annual_discount_pct",
              "ALTER TABLE projects ADD CONSTRAINT chk_annual_discount_pct CHECK (annual_discount_pct >= 0 AND annual_discount_pct <= 100)" },
            { "chk_annual_churn_pct",
              "ALTER TABLE projects ADD CONSTRAINT chk_annual_churn_pct CHECK (annual_churn_pct >= 0 AND annual_churn_pct <= 100)" },
            { "chk_current_customers",
              "ALTER TABLE projects ADD CONSTRAINT chk_current_customers CHECK (current_customers >= 0)" },
            { "chk_current_mrr",
              "ALTER TABLE projects ADD CONSTRAINT chk_current_mrr CHECK (current_mrr >= 0)" },
            { "chk_cac_per_customer",
              "ALTER TABLE projects ADD CONSTRAINT chk_cac_per_customer CHECK (cac_per_customer >= 0)" }
        };

        for (String[] pair : constraints) {
            String name = pair[0];
            String addStmt = pair[1];
            // Drop first (idempotent)
            executeStmt("ALTER TABLE projects DROP CONSTRAINT IF EXISTS " + name, executed, errors);
            // Then add
            executeStmt(addStmt, executed, errors);
        }

        // ---- Column comments (informational, never fail) ----
        String[] comments = new String[] {
            "COMMENT ON COLUMN projects.direct_burn_monthly IS 'Manual override for monthly direct burn. If NULL, auto-computed from recurrence patterns (S86 PricingCalculatorService).'",
            "COMMENT ON COLUMN projects.opex_allocation_pct IS 'Manual % of total OpEx allocated to this project. 0-100. Sum across projects should ideally = 100%.'",
            "COMMENT ON COLUMN projects.current_mrr IS 'Current Monthly Recurring Revenue from this project (EUR). Manually maintained.'",
            "COMMENT ON COLUMN projects.current_customers IS 'Current paying customers count for this project. Manually maintained.'",
            "COMMENT ON COLUMN projects.cac_per_customer IS 'Customer Acquisition Cost in EUR per customer. Used for CAC Payback + LTV:CAC metrics.'",
            "COMMENT ON COLUMN projects.gross_margin_pct IS 'Gross margin % (revenue minus COGS / revenue). SaaS default 75%. Used for LTV calculation.'",
            "COMMENT ON COLUMN projects.monthly_churn_pct IS 'Monthly customer churn % for monthly-billing customers. Used for LTV + churn-adjusted target.'",
            "COMMENT ON COLUMN projects.annual_billing_pct IS 'Pct of customers on annual prepay contracts. Affects effective blended churn + cash flow.'",
            "COMMENT ON COLUMN projects.annual_discount_pct IS 'Pct discount given for annual prepay (vs monthly sticker). Default 15%.'",
            "COMMENT ON COLUMN projects.annual_churn_pct IS 'Annual contract churn % (renewal failures). Typically much lower than monthly. Default 0.5%.'"
        };
        for (String stmt : comments) {
            executeStmt(stmt, executed, errors);
        }

        // ---- Build response ----
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", errors.isEmpty());
        response.put("statementsExecuted", executed.size());
        response.put("errorCount", errors.size());
        response.put("errors", errors);
        response.put("executedSummary", buildExecutedSummary(executed));

        if (errors.isEmpty()) {
            log.info("=== S86.2.3 Pricing Calculator migration APPLY SUCCESS ({} statements) ===", executed.size());
            return ResponseEntity.ok(response);
        } else {
            log.error("=== S86.2.3 Pricing Calculator migration APPLY FAILED ({} errors) ===", errors.size());
            return ResponseEntity.status(500).body(response);
        }
    }

    // ------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------
    private void executeStmt(String sql, List<String> executed, List<Map<String, String>> errors) {
        try {
            em.createNativeQuery(sql).executeUpdate();
            executed.add(sql);
            log.info("[S86 migration] OK: {}", sql);
        } catch (Exception ex) {
            log.error("[S86 migration] FAIL: {} -- {}", sql, ex.getMessage());
            Map<String, String> err = new LinkedHashMap<>();
            err.put("sql", sql);
            err.put("error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
            errors.add(err);
            // Transaction will rollback at the end due to @Transactional
            throw new RuntimeException("Migration step failed: " + ex.getMessage(), ex);
        }
    }

    private List<String> buildExecutedSummary(List<String> executed) {
        // Just return the first verb + table/column for compact response
        List<String> summary = new ArrayList<>();
        for (String s : executed) {
            String trimmed = s.length() > 100 ? s.substring(0, 100) + "..." : s;
            summary.add(trimmed);
        }
        return summary;
    }

    private ResponseEntity<Map<String, Object>> requireAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "Unauthenticated");
            return ResponseEntity.status(401).body(err);
        }
        boolean isAdmin = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> a.equalsIgnoreCase("ROLE_ADMIN")
                        || a.equalsIgnoreCase("ADMIN")
                        || a.equalsIgnoreCase("admin"));
        if (!isAdmin) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "Admin role required");
            err.put("authoritiesSeen", auth.getAuthorities().toString());
            return ResponseEntity.status(403).body(err);
        }
        return null;
    }
}
