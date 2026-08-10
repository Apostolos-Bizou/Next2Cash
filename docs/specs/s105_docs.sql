-- ============================================================================
-- S105 Level 4.5 — attachments in dispatch — forward migration
-- Next2Cash · manual idempotent SQL via psql (NO Flyway — deliberate policy)
-- Adds docs_blob_path: Azure Blob path of the ZIP of the dispatched
-- transactions' attachments (separate from blob_path = the report PDF).
-- Safe to re-run: ADD COLUMN IF NOT EXISTS.
-- ============================================================================

BEGIN;

ALTER TABLE report_dispatches
    ADD COLUMN IF NOT EXISTS docs_blob_path TEXT;

COMMIT;
