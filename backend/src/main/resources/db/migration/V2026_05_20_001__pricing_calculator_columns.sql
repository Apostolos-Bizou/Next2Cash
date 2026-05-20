-- ============================================================
-- Migration: V2026_05_20_001__pricing_calculator_columns.sql
-- Session: S86.2 (Pricing Calculator)
-- Purpose: Add 10 columns to projects table for Pricing Calculator
--          + AI CFO Advisor (Reverse Pricing, Billing Mix, CFO metrics)
-- Spec ref: S86 Pricing Calculator design (in-session)
-- Idempotent: YES (safe to re-run; uses ADD COLUMN IF NOT EXISTS)
-- ============================================================

-- ------------------------------------------------------------
-- 1. Pricing Calculator core fields
-- ------------------------------------------------------------
ALTER TABLE projects ADD COLUMN IF NOT EXISTS direct_burn_monthly   DECIMAL(15,2);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS opex_allocation_pct   DECIMAL(5,2)  DEFAULT 0;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS current_mrr           DECIMAL(15,2) DEFAULT 0;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS current_customers     INTEGER       DEFAULT 0;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS cac_per_customer      DECIMAL(15,2) DEFAULT 0;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS gross_margin_pct      DECIMAL(5,2)  DEFAULT 75.00;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS monthly_churn_pct     DECIMAL(5,2)  DEFAULT 3.00;

-- ------------------------------------------------------------
-- 2. Billing Mix fields (Annual vs Monthly contracts)
-- ------------------------------------------------------------
ALTER TABLE projects ADD COLUMN IF NOT EXISTS annual_billing_pct    DECIMAL(5,2)  DEFAULT 0;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS annual_discount_pct   DECIMAL(5,2)  DEFAULT 15.00;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS annual_churn_pct      DECIMAL(5,2)  DEFAULT 0.50;

-- ------------------------------------------------------------
-- 3. CHECK constraints (validation)
--    Note: idempotent via DROP IF EXISTS + ADD CONSTRAINT
-- ------------------------------------------------------------
ALTER TABLE projects DROP CONSTRAINT IF EXISTS chk_opex_allocation_pct;
ALTER TABLE projects ADD  CONSTRAINT chk_opex_allocation_pct
    CHECK (opex_allocation_pct >= 0 AND opex_allocation_pct <= 100);

ALTER TABLE projects DROP CONSTRAINT IF EXISTS chk_gross_margin_pct;
ALTER TABLE projects ADD  CONSTRAINT chk_gross_margin_pct
    CHECK (gross_margin_pct >= 0 AND gross_margin_pct <= 100);

ALTER TABLE projects DROP CONSTRAINT IF EXISTS chk_monthly_churn_pct;
ALTER TABLE projects ADD  CONSTRAINT chk_monthly_churn_pct
    CHECK (monthly_churn_pct >= 0 AND monthly_churn_pct <= 100);

ALTER TABLE projects DROP CONSTRAINT IF EXISTS chk_annual_billing_pct;
ALTER TABLE projects ADD  CONSTRAINT chk_annual_billing_pct
    CHECK (annual_billing_pct >= 0 AND annual_billing_pct <= 100);

ALTER TABLE projects DROP CONSTRAINT IF EXISTS chk_annual_discount_pct;
ALTER TABLE projects ADD  CONSTRAINT chk_annual_discount_pct
    CHECK (annual_discount_pct >= 0 AND annual_discount_pct <= 100);

ALTER TABLE projects DROP CONSTRAINT IF EXISTS chk_annual_churn_pct;
ALTER TABLE projects ADD  CONSTRAINT chk_annual_churn_pct
    CHECK (annual_churn_pct >= 0 AND annual_churn_pct <= 100);

ALTER TABLE projects DROP CONSTRAINT IF EXISTS chk_current_customers;
ALTER TABLE projects ADD  CONSTRAINT chk_current_customers
    CHECK (current_customers >= 0);

ALTER TABLE projects DROP CONSTRAINT IF EXISTS chk_current_mrr;
ALTER TABLE projects ADD  CONSTRAINT chk_current_mrr
    CHECK (current_mrr >= 0);

ALTER TABLE projects DROP CONSTRAINT IF EXISTS chk_cac_per_customer;
ALTER TABLE projects ADD  CONSTRAINT chk_cac_per_customer
    CHECK (cac_per_customer >= 0);

-- ------------------------------------------------------------
-- 4. Comments (column documentation)
-- ------------------------------------------------------------
COMMENT ON COLUMN projects.direct_burn_monthly IS
    'Manual override for monthly direct burn. If NULL, auto-computed from recurrence patterns (S86 PricingCalculatorService).';
COMMENT ON COLUMN projects.opex_allocation_pct IS
    'Manual % of total OpEx allocated to this project. 0-100. Sum across projects should ideally = 100%.';
COMMENT ON COLUMN projects.current_mrr IS
    'Current Monthly Recurring Revenue from this project (EUR). Manually maintained.';
COMMENT ON COLUMN projects.current_customers IS
    'Current paying customers count for this project. Manually maintained.';
COMMENT ON COLUMN projects.cac_per_customer IS
    'Customer Acquisition Cost in EUR per customer. Used for CAC Payback + LTV:CAC metrics.';
COMMENT ON COLUMN projects.gross_margin_pct IS
    'Gross margin % (revenue minus COGS / revenue). SaaS default 75%. Used for LTV calculation.';
COMMENT ON COLUMN projects.monthly_churn_pct IS
    'Monthly customer churn % for monthly-billing customers. Used for LTV + churn-adjusted target.';
COMMENT ON COLUMN projects.annual_billing_pct IS
    '% of customers on annual prepay contracts. Affects effective blended churn + cash flow.';
COMMENT ON COLUMN projects.annual_discount_pct IS
    '% discount given for annual prepay (vs monthly sticker). Default 15%.';
COMMENT ON COLUMN projects.annual_churn_pct IS
    'Annual contract churn % (renewal failures). Typically much lower than monthly. Default 0.5%.';

-- ============================================================
-- END OF MIGRATION
-- ============================================================
