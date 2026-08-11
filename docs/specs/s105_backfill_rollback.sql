-- ============================================================================
-- S105 backfill — ROLLBACK. Deletes ONLY the two backfilled dispatches by their
-- fixed UUIDs (child items first, then headers). Safe to re-run.
-- No other dispatch is touched.
-- ============================================================================

BEGIN;

DELETE FROM report_dispatch_items
WHERE dispatch_id IN (
  '51050000-0000-4000-8000-0000000000a1',
  '51050000-0000-4000-8000-0000000000b2'
);

DELETE FROM report_dispatches
WHERE id IN (
  '51050000-0000-4000-8000-0000000000a1',
  '51050000-0000-4000-8000-0000000000b2'
);

COMMIT;
