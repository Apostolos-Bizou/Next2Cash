-- ============================================================================
-- S105 backfill — two historical dispatches that were sent to the accountant
-- BEFORE the dispatch system existed. DIRECT SQL (bypasses service validations,
-- so PLANNED/void transactions in the identified set are recorded as sent).
-- Read-only identification done first (all 23 matched uniquely on id+date+amount).
-- Fixed UUIDs (not gen_random_uuid) so the rollback is exact.
-- Idempotent: ON CONFLICT DO NOTHING. Wrapped in BEGIN/COMMIT.
--   Dispatch A id = 51050000-0000-4000-8000-0000000000a1  (Next2Me Group)
--   Dispatch B id = 51050000-0000-4000-8000-0000000000b2  (Next2Me)
-- ============================================================================

BEGIN;

INSERT INTO report_dispatches (id, entity_id, title, recipient, sent_date, note, blob_path, created_by)
VALUES
  ('51050000-0000-4000-8000-0000000000a1',
   '50317f44-9961-4fb4-add0-7a118e32dc14',
   'Καρτέλα Next2Me Group Εξοδα',
   'Λογιστήριο',
   DATE '2026-06-23',
   'Ιστορική αποστολή πριν το dispatch system — backfill S105',
   NULL,
   'd4f832d6-08e0-4de7-862d-04c08193de43'),
  ('51050000-0000-4000-8000-0000000000b2',
   '58202b71-4ddb-45c9-8e3c-39e816bde972',
   'Καρτέλα ΕΞΟΔΑ Next2Me',
   'Λογιστήριο',
   DATE '2026-04-30',
   'Ιστορική αποστολή πριν το dispatch system — backfill S105',
   NULL,
   'd4f832d6-08e0-4de7-862d-04c08193de43')
ON CONFLICT (id) DO NOTHING;

-- Group A — 8 items (Next2Me Group), real transactions.id from the identification
INSERT INTO report_dispatch_items (dispatch_id, transaction_id)
VALUES
  ('51050000-0000-4000-8000-0000000000a1', 90237),
  ('51050000-0000-4000-8000-0000000000a1', 90236),
  ('51050000-0000-4000-8000-0000000000a1', 90401),
  ('51050000-0000-4000-8000-0000000000a1', 90238),
  ('51050000-0000-4000-8000-0000000000a1', 90347),
  ('51050000-0000-4000-8000-0000000000a1', 90346),
  ('51050000-0000-4000-8000-0000000000a1', 90397),
  ('51050000-0000-4000-8000-0000000000a1', 90343),
  -- Group B — 15 items (Next2Me), real transactions.id from the identification
  ('51050000-0000-4000-8000-0000000000b2', 90124),
  ('51050000-0000-4000-8000-0000000000b2', 90125),
  ('51050000-0000-4000-8000-0000000000b2', 90129),
  ('51050000-0000-4000-8000-0000000000b2', 90115),
  ('51050000-0000-4000-8000-0000000000b2', 4775),
  ('51050000-0000-4000-8000-0000000000b2', 4776),
  ('51050000-0000-4000-8000-0000000000b2', 4732),
  ('51050000-0000-4000-8000-0000000000b2', 4738),
  ('51050000-0000-4000-8000-0000000000b2', 4733),
  ('51050000-0000-4000-8000-0000000000b2', 4693),
  ('51050000-0000-4000-8000-0000000000b2', 4691),
  ('51050000-0000-4000-8000-0000000000b2', 4692),
  ('51050000-0000-4000-8000-0000000000b2', 4694),
  ('51050000-0000-4000-8000-0000000000b2', 4742),
  ('51050000-0000-4000-8000-0000000000b2', 4684)
ON CONFLICT (dispatch_id, transaction_id) DO NOTHING;

COMMIT;

-- Verification (read-only): expect 8 and 15.
SELECT rd.title, rd.sent_date, COUNT(rdi.transaction_id) AS items
FROM report_dispatches rd
LEFT JOIN report_dispatch_items rdi ON rdi.dispatch_id = rd.id
GROUP BY rd.id, rd.title, rd.sent_date
ORDER BY rd.sent_date;
