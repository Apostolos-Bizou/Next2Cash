-- ============================================================================
-- S105 Level 4.5 — ROLLBACK (granular: undo just the docs column)
-- Drops docs_blob_path without touching the dispatch tables. Use this to roll
-- back Level 4.5 alone. Safe to re-run: DROP COLUMN IF EXISTS.
-- NOTE: this does NOT delete any docs ZIPs already in Blob storage.
-- ============================================================================

BEGIN;

ALTER TABLE report_dispatches
    DROP COLUMN IF EXISTS docs_blob_path;

COMMIT;
