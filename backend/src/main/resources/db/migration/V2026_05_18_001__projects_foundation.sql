-- ============================================================
-- Migration: V2026_05_18_001__projects_foundation.sql
-- Session: S71-A
-- Purpose: Phase 2-A foundation -- projects table + FK link to transactions
-- Spec ref: Next2Cash_CashPlanning_TechSpec_v1_0 sections 4.4 and 10.2
-- Idempotent: YES (safe to re-run)
-- ============================================================

-- ------------------------------------------------------------
-- 1. CREATE projects table
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS projects (
    id                       UUID PRIMARY KEY,
    name                     VARCHAR(200) NOT NULL UNIQUE,
    description              TEXT,
    owner_entity_id          UUID NOT NULL REFERENCES entities(id),
    status                   VARCHAR(30) NOT NULL DEFAULT 'PLANNING'
                             CHECK (status IN ('PLANNING','IN_DEVELOPMENT','TESTING','LIVE','PAUSED','CANCELLED')),
    start_date               DATE,
    target_completion_date   DATE,
    actual_completion_date   DATE,
    total_budget             DECIMAL(15,2) DEFAULT 0,
    expected_monthly_revenue DECIMAL(15,2) DEFAULT 0,
    color                    VARCHAR(7) DEFAULT '#3B82F6',
    created_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active                BOOLEAN NOT NULL DEFAULT TRUE
);

-- ------------------------------------------------------------
-- 2. INDEXES
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_projects_owner    ON projects(owner_entity_id);
CREATE INDEX IF NOT EXISTS idx_projects_status   ON projects(status);
CREATE INDEX IF NOT EXISTS idx_projects_active   ON projects(is_active);

-- ------------------------------------------------------------
-- 3. FK from transactions.project_id (column already exists from Phase 1)
--    Add the foreign-key constraint only if missing.
-- ------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_transactions_project'
          AND table_name = 'transactions'
    ) THEN
        ALTER TABLE transactions
        ADD CONSTRAINT fk_transactions_project
        FOREIGN KEY (project_id) REFERENCES projects(id)
        ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_transactions_project ON transactions(project_id);

-- ------------------------------------------------------------
-- 4. SEED 5 initial projects (spec section 10.2)
--    Deterministic UUIDs so code/tests can reference them.
--    Owner = Next2Me Group (50317f44-9961-4fb4-add0-7a118e32dc14)
--    ON CONFLICT DO NOTHING -- idempotent.
-- ------------------------------------------------------------
INSERT INTO projects (id, name, description, owner_entity_id, status, start_date, target_completion_date, total_budget, expected_monthly_revenue, color)
VALUES
    ('a1000001-0000-0000-0000-000000000001',
     'Next2Cash',
     'Cash flow management platform - pilot project, defines the standard for the whole Group.',
     '50317f44-9961-4fb4-add0-7a118e32dc14',
     'IN_DEVELOPMENT',
     DATE '2026-02-01', DATE '2026-09-30',
     80000.00, 16000.00,
     '#F59E0B'),

    ('a1000001-0000-0000-0000-000000000002',
     'Next2View',
     'Marine operations view platform - live, generating monthly revenue.',
     '50317f44-9961-4fb4-add0-7a118e32dc14',
     'LIVE',
     DATE '2025-05-01', DATE '2025-12-31',
     60000.00, 4500.00,
     '#10B981'),

    ('a1000001-0000-0000-0000-000000000003',
     'ATLANTIS',
     'Phase 2 strategic project - in planning stage.',
     '50317f44-9961-4fb4-add0-7a118e32dc14',
     'PLANNING',
     DATE '2026-04-01', DATE '2027-04-30',
     120000.00, 0.00,
     '#3B82F6'),

    ('a1000001-0000-0000-0000-000000000004',
     'HireBase',
     'Phase 3 roadmap - recruitment platform.',
     '50317f44-9961-4fb4-add0-7a118e32dc14',
     'PLANNING',
     NULL, NULL,
     0.00, 0.00,
     '#8B5CF6'),

    ('a1000001-0000-0000-0000-000000000005',
     'WIMAS',
     'Phase 3 roadmap - training center platform (React + Node).',
     '50317f44-9961-4fb4-add0-7a118e32dc14',
     'PLANNING',
     NULL, NULL,
     0.00, 0.00,
     '#EC4899')
ON CONFLICT (id) DO NOTHING;
