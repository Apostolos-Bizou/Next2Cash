-- ============================================================================
-- S105 — Report Builder Dispatches — ROLLBACK
-- Drops in FK-safe order: child (report_dispatch_items) BEFORE parent
-- (report_dispatches). Indexes are dropped automatically with their tables.
-- Safe to re-run: DROP TABLE IF EXISTS.
-- WARNING: destructive — removes all dispatch history and line items.
-- ============================================================================

BEGIN;

-- Child first: report_dispatch_items has FK -> report_dispatches(id).
DROP TABLE IF EXISTS report_dispatch_items;

-- Parent second.
DROP TABLE IF EXISTS report_dispatches;

COMMIT;
