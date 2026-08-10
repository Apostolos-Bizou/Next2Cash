-- ============================================================================
-- S105 — Report Builder Dispatches — forward migration
-- Next2Cash · manual idempotent SQL via psql (NO Flyway — deliberate policy)
-- Safe to re-run: CREATE TABLE/INDEX IF NOT EXISTS.
-- FK targets (verified via codebase audit S105):
--   entities(id)      = UUID
--   users(id)         = UUID
--   transactions(id)  = INTEGER (serial)  <-- NOT UUID
-- ============================================================================

BEGIN;

-- Header: one row per dispatch (a report sent to a recipient).
CREATE TABLE IF NOT EXISTS report_dispatches (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id   UUID        NOT NULL REFERENCES entities(id),
    title       TEXT        NOT NULL,
    recipient   TEXT        NOT NULL,
    sent_date   DATE        NOT NULL,
    note        TEXT,
    blob_path   TEXT,                        -- Azure Blob path of the generated PDF
    created_by  UUID        NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Line items: which transactions were included in a dispatch.
-- transaction_id is INTEGER because transactions.id is a serial integer.
CREATE TABLE IF NOT EXISTS report_dispatch_items (
    dispatch_id     UUID    NOT NULL REFERENCES report_dispatches(id) ON DELETE CASCADE,
    transaction_id  INTEGER NOT NULL REFERENCES transactions(id),
    PRIMARY KEY (dispatch_id, transaction_id)
);

-- Indexes: badge lookups by transaction, archive listing by entity + date.
CREATE INDEX IF NOT EXISTS ix_rdi_tx ON report_dispatch_items(transaction_id);
CREATE INDEX IF NOT EXISTS ix_rd_ent ON report_dispatches(entity_id, sent_date DESC);

COMMIT;
