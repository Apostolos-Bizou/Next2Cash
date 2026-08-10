-- ============================================================================
-- S105 — Report Builder Dispatches — ROLLBACK
-- Drops in FK-safe order: child (report_dispatch_items) BEFORE parent
-- (report_dispatches). Indexes are dropped automatically with their tables.
-- Safe to re-run: DROP TABLE IF EXISTS.
-- WARNING: destructive — removes all dispatch history and line items.
-- ============================================================================

BEGIN;

-- Level 4.5 column (redundant once the table is dropped below, but explicit so
-- a full rollback is self-documenting; for a column-only rollback use
-- s105_docs_rollback.sql).
ALTER TABLE IF EXISTS report_dispatches DROP COLUMN IF EXISTS docs_blob_path;

-- Child first: report_dispatch_items has FK -> report_dispatches(id).
DROP TABLE IF EXISTS report_dispatch_items;

-- Parent second.
DROP TABLE IF EXISTS report_dispatches;

COMMIT;
