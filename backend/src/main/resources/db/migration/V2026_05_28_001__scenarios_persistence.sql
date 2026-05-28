-- ============================================================
-- Migration: V2026_05_28_001__scenarios_persistence.sql
-- Session: S97
-- Purpose: Phase 2 -- forecast_scenarios table + FK link from
--          transactions.scenario_id (column already exists from Phase 1).
-- Spec ref: Next2Cash_CashPlanning_TechSpec_v1_1 sections 3 (Principle 3),
--           4.1, 5.8.
-- Idempotent: YES (safe to re-run). Uses IF NOT EXISTS / ON CONFLICT.
--
-- DESIGN NOTES
--   - forecast_scenarios uses UUID PK (consistent with projects / entities).
--   - Each scenario is scoped to an owner entity (owner_entity_id), exactly
--     like projects. This lets each company have its own Baseline /
--     Optimistic / Pessimistic / Custom set.
--   - scenario_type is the canonical kind: BASELINE / OPTIMISTIC /
--     PESSIMISTIC / CUSTOM (CHECK-constrained, mirrors ScenarioStatus.java).
--   - revenue_adjust_pct / expense_adjust_pct are the simple "what-if"
--     levers: a scenario shifts PLANNED revenue and expenses by these
--     percentages relative to baseline. BASELINE is always 0 / 0.
--   - is_default marks the one scenario shown when none is selected
--     (always the BASELINE per entity).
--   - The FK transactions.scenario_id -> forecast_scenarios.id is added here
--     (was deliberately deferred in Phase 1). ON DELETE SET NULL so deleting
--     a scenario reverts its PLANNED transactions to baseline (NULL).
--   - Seed: 4 scenarios per existing entity, with deterministic-enough random
--     UUIDs (gen_random_uuid). Baseline is is_default=true.
-- ============================================================

BEGIN;

-- ------------------------------------------------------------
-- 1. CREATE forecast_scenarios table
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS forecast_scenarios (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                 VARCHAR(120) NOT NULL,
    scenario_type        VARCHAR(20) NOT NULL DEFAULT 'CUSTOM',
    owner_entity_id      UUID NOT NULL REFERENCES entities(id),
    description          TEXT,
    revenue_adjust_pct   DECIMAL(6,2) NOT NULL DEFAULT 0,
    expense_adjust_pct   DECIMAL(6,2) NOT NULL DEFAULT 0,
    color                VARCHAR(7) NOT NULL DEFAULT '#6B7280',
    is_default           BOOLEAN NOT NULL DEFAULT FALSE,
    is_active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_scenario_type
        CHECK (scenario_type IN ('BASELINE','OPTIMISTIC','PESSIMISTIC','CUSTOM')),
    CONSTRAINT chk_scenario_revenue_adjust
        CHECK (revenue_adjust_pct >= -100 AND revenue_adjust_pct <= 1000),
    CONSTRAINT chk_scenario_expense_adjust
        CHECK (expense_adjust_pct >= -100 AND expense_adjust_pct <= 1000),
    CONSTRAINT uq_scenario_name_per_entity
        UNIQUE (owner_entity_id, name)
);

-- ------------------------------------------------------------
-- 2. INDEXES
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_scenarios_owner   ON forecast_scenarios(owner_entity_id);
CREATE INDEX IF NOT EXISTS idx_scenarios_type    ON forecast_scenarios(scenario_type);
CREATE INDEX IF NOT EXISTS idx_scenarios_active  ON forecast_scenarios(is_active);

-- ------------------------------------------------------------
-- 3. FK from transactions.scenario_id (column exists from Phase 1)
--    Add the foreign-key constraint only if missing.
-- ------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_transactions_scenario'
          AND table_name = 'transactions'
    ) THEN
        ALTER TABLE transactions
        ADD CONSTRAINT fk_transactions_scenario
        FOREIGN KEY (scenario_id) REFERENCES forecast_scenarios(id)
        ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_transactions_scenario ON transactions(scenario_id);

-- ------------------------------------------------------------
-- 4. SEED 4 scenarios for every existing entity.
--    Baseline = is_default true, 0/0 adjustments.
--    Optimistic = +20% revenue, -5% expenses.
--    Pessimistic = -20% revenue, +10% expenses.
--    Custom = 0/0 (user-editable starting point).
--    Idempotent via uq_scenario_name_per_entity + ON CONFLICT.
-- ------------------------------------------------------------
INSERT INTO forecast_scenarios
    (name, scenario_type, owner_entity_id, description,
     revenue_adjust_pct, expense_adjust_pct, color, is_default)
SELECT
    'Baseline', 'BASELINE', e.id,
    'Τι θα συμβεί αν όλα συνεχίσουν όπως είναι σήμερα — το πιο πιθανό σενάριο.',
    0, 0, '#3B82F6', TRUE
FROM entities e
ON CONFLICT (owner_entity_id, name) DO NOTHING;

INSERT INTO forecast_scenarios
    (name, scenario_type, owner_entity_id, description,
     revenue_adjust_pct, expense_adjust_pct, color, is_default)
SELECT
    'Optimistic', 'OPTIMISTIC', e.id,
    'Αν κλείσουν οι deals που κυνηγάμε και ανανεωθούν οι συμβάσεις.',
    20, -5, '#10B981', FALSE
FROM entities e
ON CONFLICT (owner_entity_id, name) DO NOTHING;

INSERT INTO forecast_scenarios
    (name, scenario_type, owner_entity_id, description,
     revenue_adjust_pct, expense_adjust_pct, color, is_default)
SELECT
    'Pessimistic', 'PESSIMISTIC', e.id,
    'Αν χάσουμε πελάτη, καθυστερήσουν εισπράξεις, αυξηθούν κόστη.',
    -20, 10, '#EF4444', FALSE
FROM entities e
ON CONFLICT (owner_entity_id, name) DO NOTHING;

INSERT INTO forecast_scenarios
    (name, scenario_type, owner_entity_id, description,
     revenue_adjust_pct, expense_adjust_pct, color, is_default)
SELECT
    'Custom', 'CUSTOM', e.id,
    'Σενάριο που φτιάχνετε εσείς — προσαρμόστε τα ποσοστά ελεύθερα.',
    0, 0, '#6B7280', FALSE
FROM entities e
ON CONFLICT (owner_entity_id, name) DO NOTHING;

-- ------------------------------------------------------------
-- 5. Column documentation
-- ------------------------------------------------------------
COMMENT ON TABLE forecast_scenarios IS
    'What-if scenarios for cash planning. Each entity has Baseline/Optimistic/Pessimistic/Custom. Linked from transactions.scenario_id.';
COMMENT ON COLUMN forecast_scenarios.scenario_type IS
    'BASELINE / OPTIMISTIC / PESSIMISTIC / CUSTOM. Mirrors ScenarioStatus.java whitelist.';
COMMENT ON COLUMN forecast_scenarios.revenue_adjust_pct IS
    'Percentage shift applied to PLANNED revenue vs baseline. Baseline = 0.';
COMMENT ON COLUMN forecast_scenarios.expense_adjust_pct IS
    'Percentage shift applied to PLANNED expenses vs baseline. Baseline = 0.';
COMMENT ON COLUMN forecast_scenarios.is_default IS
    'TRUE for the one scenario (Baseline) shown when no scenario is explicitly selected.';

COMMIT;

-- ============================================================
-- Verification (post-commit)
-- ============================================================
SELECT 'forecast_scenarios table exists' AS check_name, COUNT(*) AS count
FROM information_schema.tables WHERE table_name = 'forecast_scenarios'
UNION ALL
SELECT 'scenarios seeded (4 per entity)', COUNT(*)
FROM forecast_scenarios
UNION ALL
SELECT 'fk_transactions_scenario exists', COUNT(*)
FROM information_schema.table_constraints
WHERE constraint_name = 'fk_transactions_scenario' AND table_name = 'transactions';

-- Expected:
--   forecast_scenarios table exists  | 1
--   scenarios seeded (4 per entity)  | 4 * (#entities)
--   fk_transactions_scenario exists  | 1
