-- Polaris P&L — cost_milestones category split (30 Oct 2026).
--
-- The forecast categories went from 4 to 9: "Consultants / Audit / Legal" became
-- Consultants, Audit, Legal and "Ταξίδια / PR / λοιπά / σύνδεσμοι" became Ταξίδια, PR,
-- Λοιπά, Σύνδεσμοι. No schema change (category is free text), but the read-only count on
-- 29 Oct 2026 21:22 found ONE production row still carrying the old combined label:
--
--   category                      | count
--   ------------------------------+------
--   Consultants / Audit / Legal   |    1
--
-- Until it is renamed, the new code ignores it (it is not one of the 9 categories): it
-- disappears from the Forecast page and from EBITDA. Astrolavos has no split for this
-- amount, so the TARGET category below is an owner decision, NOT a derivation --
-- replace :target before running. Dry-run first (ROLLBACK), then COMMIT.
--
--   psql ... -v target=Consultants -f docs/specs/polaris_cost_milestones_split.sql

\set ON_ERROR_STOP on
BEGIN;

-- Pre-state
SELECT id, category, target_date, target_value FROM cost_milestones
  WHERE entity_id = (SELECT id FROM entities WHERE code = 'polaris') ORDER BY category, target_date;

UPDATE cost_milestones
   SET category = :'target', updated_at = now()
 WHERE entity_id = (SELECT id FROM entities WHERE code = 'polaris')
   AND category = 'Consultants / Audit / Legal';

-- Post-state: every row must be one of the 9 categories (expect 0 unknown)
SELECT count(*) AS unknown_category_rows FROM cost_milestones
  WHERE entity_id = (SELECT id FROM entities WHERE code = 'polaris')
    AND category NOT IN ('Consultants','Audit','Legal','IT / Computer','Marketing / Διαφήμιση','Ταξίδια','PR','Λοιπά','Σύνδεσμοι');
SELECT id, category, target_date, target_value FROM cost_milestones
  WHERE entity_id = (SELECT id FROM entities WHERE code = 'polaris') ORDER BY category, target_date;

-- Dry-run: leave ROLLBACK. Real run: replace with COMMIT.
ROLLBACK;
