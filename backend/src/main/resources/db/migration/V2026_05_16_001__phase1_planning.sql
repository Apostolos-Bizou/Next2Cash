-- =====================================================================
-- V2026_05_16_001__phase1_planning.sql
-- =====================================================================
-- Phase 1: Mode-Aware Form & Recurring Engine
--
-- Adds 9 columns to transactions table (ACTUAL/PLANNED support, recurring,
-- project tracking, scenario tagging, conversion audit trail).
-- Creates recurrence_patterns table for recurring transaction definitions.
--
-- VERIFIED SCHEMA FACTS (from Transaction.java + live DB):
--   - transactions.id is INTEGER (auto-increment, @GeneratedValue IDENTITY)
--   - transaction logical date column is doc_date (LocalDate docDate)
--   - There is NO column named "date" - all date columns are prefixed
--     (doc_date, payment_date, due_date)
--   - Self-FKs to transactions.id must be INTEGER
--   - New master-data tables (recurrence_patterns, future projects,
--     scenarios) use UUID for consistency with entities/users/bank_accounts
--
-- Design decisions:
--   - project_id and scenario_id are UUID nullable WITHOUT FK constraint
--     in this migration. FKs will be added in Phase 2 once projects and
--     forecast_scenarios tables exist.
--   - All existing transactions get entry_mode='ACTUAL' (backward compat).
--   - Composite index on (entity_id, entry_mode, doc_date) for fast
--     forecast queries (90-day forecast view filter pattern).
--   - Idempotent: safe to re-run. Uses IF NOT EXISTS everywhere.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- Section 1: Extend transactions table with 9 new columns
-- ---------------------------------------------------------------------

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS entry_mode VARCHAR(10) NOT NULL DEFAULT 'ACTUAL';

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS is_recurring BOOLEAN NOT NULL DEFAULT false;

-- UUID: points to recurrence_patterns (new master-data table)
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS recurrence_pattern_id UUID;

-- INTEGER: self-FK to transactions.id (which is INTEGER auto-increment)
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS parent_recurring_id INTEGER;

-- UUID: points to future projects table (Phase 2)
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS project_id UUID;

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS confidence_pct INTEGER NOT NULL DEFAULT 100;

-- UUID: points to future forecast_scenarios table (Phase 2)
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS scenario_id UUID;

-- INTEGER: self-FK to transactions.id (audit trail PLANNED -> ACTUAL)
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS converted_to_transaction_id INTEGER;

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS converted_at TIMESTAMP;

-- ---------------------------------------------------------------------
-- Section 2: CHECK constraints for data integrity
-- ---------------------------------------------------------------------

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'chk_transactions_entry_mode'
        AND table_name = 'transactions'
    ) THEN
        ALTER TABLE transactions
            ADD CONSTRAINT chk_transactions_entry_mode
            CHECK (entry_mode IN ('ACTUAL', 'PLANNED'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'chk_transactions_confidence_pct'
        AND table_name = 'transactions'
    ) THEN
        ALTER TABLE transactions
            ADD CONSTRAINT chk_transactions_confidence_pct
            CHECK (confidence_pct >= 0 AND confidence_pct <= 100);
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- Section 3: Self-referential FKs (INTEGER -> transactions.id)
-- ---------------------------------------------------------------------

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_transactions_parent_recurring'
        AND table_name = 'transactions'
    ) THEN
        ALTER TABLE transactions
            ADD CONSTRAINT fk_transactions_parent_recurring
            FOREIGN KEY (parent_recurring_id) REFERENCES transactions(id)
            ON DELETE SET NULL;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_transactions_converted_to'
        AND table_name = 'transactions'
    ) THEN
        ALTER TABLE transactions
            ADD CONSTRAINT fk_transactions_converted_to
            FOREIGN KEY (converted_to_transaction_id) REFERENCES transactions(id)
            ON DELETE SET NULL;
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- Section 4: Create recurrence_patterns table (UUID master data)
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS recurrence_patterns (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    frequency         VARCHAR(20) NOT NULL,
    interval_count    INTEGER NOT NULL DEFAULT 1,
    day_of_month      INTEGER,
    day_of_week       INTEGER,
    start_date        DATE NOT NULL,
    end_date          DATE,
    max_occurrences   INTEGER,
    timezone          VARCHAR(50) NOT NULL DEFAULT 'Europe/Athens',
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_recurrence_frequency
        CHECK (frequency IN ('DAILY','WEEKLY','MONTHLY','QUARTERLY','YEARLY','CUSTOM')),
    CONSTRAINT chk_recurrence_interval
        CHECK (interval_count >= 1),
    CONSTRAINT chk_recurrence_day_of_month
        CHECK (day_of_month IS NULL OR (day_of_month >= 1 AND day_of_month <= 31)),
    CONSTRAINT chk_recurrence_day_of_week
        CHECK (day_of_week IS NULL OR (day_of_week >= 1 AND day_of_week <= 7)),
    CONSTRAINT chk_recurrence_max_occurrences
        CHECK (max_occurrences IS NULL OR max_occurrences >= 1),
    CONSTRAINT chk_recurrence_end_logic
        CHECK (end_date IS NULL OR end_date >= start_date)
);

-- FK from transactions.recurrence_pattern_id (UUID -> recurrence_patterns.id)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_transactions_recurrence_pattern'
        AND table_name = 'transactions'
    ) THEN
        ALTER TABLE transactions
            ADD CONSTRAINT fk_transactions_recurrence_pattern
            FOREIGN KEY (recurrence_pattern_id) REFERENCES recurrence_patterns(id)
            ON DELETE SET NULL;
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- Section 5: Performance indexes
-- ---------------------------------------------------------------------

-- Composite index for 90-day forecast queries
-- (entity_id, entry_mode, doc_date) is the natural filter combination
CREATE INDEX IF NOT EXISTS idx_transactions_entity_mode_doc_date
    ON transactions(entity_id, entry_mode, doc_date);

-- Index on parent_recurring_id for "show all instances" lookups
CREATE INDEX IF NOT EXISTS idx_transactions_parent_recurring
    ON transactions(parent_recurring_id)
    WHERE parent_recurring_id IS NOT NULL;

-- Index on recurrence_pattern_id for pattern lookups
CREATE INDEX IF NOT EXISTS idx_transactions_recurrence_pattern
    ON transactions(recurrence_pattern_id)
    WHERE recurrence_pattern_id IS NOT NULL;

-- Index on project_id (Phase 2 project drilldowns)
CREATE INDEX IF NOT EXISTS idx_transactions_project
    ON transactions(project_id)
    WHERE project_id IS NOT NULL;

-- Index on is_recurring=true (small subset, fast Recurring Manager lookup)
CREATE INDEX IF NOT EXISTS idx_transactions_is_recurring
    ON transactions(is_recurring)
    WHERE is_recurring = true;

-- Index on recurrence_patterns.start_date for engine queries
CREATE INDEX IF NOT EXISTS idx_recurrence_patterns_start_date
    ON recurrence_patterns(start_date);

-- ---------------------------------------------------------------------
-- Section 6: Column documentation
-- ---------------------------------------------------------------------

COMMENT ON COLUMN transactions.entry_mode IS
    'ACTUAL = historical transaction (default, all existing rows). PLANNED = future/forecast transaction.';
COMMENT ON COLUMN transactions.is_recurring IS
    'TRUE if this transaction is the mother of a recurring series. Instances point back via parent_recurring_id.';
COMMENT ON COLUMN transactions.recurrence_pattern_id IS
    'FK (UUID) to recurrence_patterns. Defines frequency, day, end conditions for the recurring series.';
COMMENT ON COLUMN transactions.parent_recurring_id IS
    'Self-FK (INTEGER -> transactions.id). If set, this transaction is an instance generated from a recurring mother.';
COMMENT ON COLUMN transactions.project_id IS
    'FK (UUID) to projects (Phase 2). NULL = OpEx (general company expense, not project-bound).';
COMMENT ON COLUMN transactions.confidence_pct IS
    'Probability (0-100) that a PLANNED transaction will actually occur. 100 = certain.';
COMMENT ON COLUMN transactions.scenario_id IS
    'FK (UUID) to forecast_scenarios (Phase 2). Which scenario this PLANNED transaction belongs to. NULL = baseline.';
COMMENT ON COLUMN transactions.converted_to_transaction_id IS
    'Self-FK (INTEGER -> transactions.id). Audit trail: when PLANNED converts to ACTUAL, points to the new ACTUAL row.';
COMMENT ON COLUMN transactions.converted_at IS
    'Timestamp when PLANNED was converted to ACTUAL. NULL if never converted.';

COMMENT ON TABLE recurrence_patterns IS
    'Defines how a recurring transaction repeats. Linked from transactions.recurrence_pattern_id.';

COMMIT;

-- =====================================================================
-- Verification (post-commit): expected counts
-- =====================================================================

SELECT
    'transactions new columns' AS check_name,
    COUNT(*) AS count
FROM information_schema.columns
WHERE table_name = 'transactions'
  AND column_name IN (
      'entry_mode','is_recurring','recurrence_pattern_id',
      'parent_recurring_id','project_id','confidence_pct',
      'scenario_id','converted_to_transaction_id','converted_at'
  )
UNION ALL
SELECT 'recurrence_patterns table exists', COUNT(*)
FROM information_schema.tables WHERE table_name = 'recurrence_patterns'
UNION ALL
SELECT 'new transactions indexes', COUNT(*)
FROM pg_indexes WHERE tablename = 'transactions'
  AND indexname IN (
      'idx_transactions_entity_mode_doc_date',
      'idx_transactions_parent_recurring',
      'idx_transactions_recurrence_pattern',
      'idx_transactions_project',
      'idx_transactions_is_recurring'
  );

-- Expected:
--   transactions new columns           | 9
--   recurrence_patterns table exists   | 1
--   new transactions indexes           | 5
