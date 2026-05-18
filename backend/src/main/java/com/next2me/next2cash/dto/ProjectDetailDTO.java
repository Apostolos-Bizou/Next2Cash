package com.next2me.next2cash.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO for GET /api/projects/{id}/detail (S71-D).
 *
 * Spec ref: CashPlanning TechSpec v1.0 section 5.6 (Project Deep-Dive).
 *
 * Per-category 'planned' values are null until Phase 2-E adds the
 * project_budget_lines table. ROI is null when expectedMonthlyRevenue == 0.
 */
public class ProjectDetailDTO {
    public ProjectDTO project;
    public List<CategorySpent> budgetBreakdown;
    public Totals totals;
    public LinkedTransactionsBlock linkedTransactions;
    public List<RevenueStream> revenueStreams;
    public BigDecimal weightedMonthlyRevenue;
    public RoiAnalysis roi;

    public ProjectDetailDTO() {}

    public static class CategorySpent {
        public String category;
        public BigDecimal planned;
        public BigDecimal spent;
        public BigDecimal remaining;
        public BigDecimal progressPct;
    }

    public static class Totals {
        public BigDecimal planned;
        public BigDecimal spent;
        public BigDecimal remaining;
        public BigDecimal progressPct;
    }

    public static class LinkedTransactionsBlock {
        public int count;
        public List<LinkedTransaction> transactions;
    }

    public static class LinkedTransaction {
        public Integer id;
        public Integer entityNumber;
        public LocalDate docDate;
        public String description;
        public String category;
        public String counterparty;
        public String type;
        public BigDecimal amount;
        public String entryMode;
        public String paymentStatus;
    }

    public static class RevenueStream {
        public String source;
        public BigDecimal amount;
        public Integer confidencePct;
    }

    public static class RoiAnalysis {
        public BigDecimal totalInvestment;
        public BigDecimal monthlyRevenueWeighted;
        public BigDecimal monthlyRevenueBestCase;
        public BigDecimal breakEvenMonthsWeighted;
        public BigDecimal breakEvenMonthsBest;
        public BigDecimal twelveMonthRoiWeightedPct;
        public BigDecimal twelveMonthRoiBestPct;
    }
}
