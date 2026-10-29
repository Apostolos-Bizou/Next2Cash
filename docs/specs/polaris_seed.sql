-- Polaris P&L — canonical seed of the Polaris entity (corrected 30 Oct 2026).
--
-- This is the FIXED version of the 28 Oct 2026 seed that went into production from a
-- scratch file. The original had two defects that were later patched live
-- (polaris_assumption_keys_commit.sql, polaris_payroll_restore_commit.sql):
--   1. forecast_assumptions keys did not match the app (members_base -> members,
--      net_fee -> network_fee, audit_rate -> audit_fee_rate, net_pay -> network_payable,
--      audit_pay -> audit_payable_rate, dental_pay -> dental_payable,
--      php_per_usd -> fx_php_per_usd, usd_per_eur -> fx_usd_per_eur);
--   2. payroll_people.office held the Greek label ('Αθήνα' / 'Μανίλα') instead of the
--      code the app stores and the FX rule keys on ('ATHENS' / 'MANILA').
-- Both are corrected below. Values: Polaris xlsx model (28 Oct 2026 review).
--
-- Idempotent: every INSERT is skipped when the entity already has rows in that table,
-- so re-running against production is a no-op. Migration policy: manual psql, no Flyway.
-- Run as a dry-run first (ROLLBACK), then with COMMIT, after a pg_dump backup
-- (see memory: prod DB access protocol).

\set ON_ERROR_STOP on
BEGIN;

-- Payroll — Αθήνα (7) + Μανίλα (7). Athens salaries are USD, Manila are PHP.
WITH p AS (SELECT id FROM entities WHERE code = 'polaris')
INSERT INTO payroll_people (entity_id, office, name, role_title, salary, raise_pct, effective_from_year, is_office_cost)
SELECT p.id, v.office, v.name, v.role_title, v.salary, v.raise_pct, v.eff_year, v.is_office_cost
FROM p, (VALUES
  ('ATHENS','Γραφείο Αθηνών','Ενοίκιο & λειτουργικά',10000,0,2027,true),
  ('ATHENS','General Manager','Διοίκηση',62400,0,2027,false),
  ('ATHENS','Business Director','Διοίκηση',62400,0,2027,false),
  ('ATHENS','Accounting Manager','Λογιστήριο',46800,0,2027,false),
  ('ATHENS','Marketing Manager','Marketing',23400,0,2027,false),
  ('ATHENS','IT & Developer','IT',46800,0,2027,false),
  ('ATHENS','Legal & Compliance Mgr','Νομικό',20000,0,2027,false),
  ('MANILA','Γραφείο Μανίλα','Ενοίκιο & λειτουργικά',1130000,0,2027,true),
  ('MANILA','Pia Guirao','COO',1130000,5,2027,false),
  ('MANILA','Carmen Soriano','Network Co-ordinator',2034000,0,2027,false),
  ('MANILA','Margilyn Nalupano','Clerk 1',678000,41.7,2027,false),
  ('MANILA','Victoria Lawless','Clerk 2',678000,41.7,2027,false),
  ('MANILA','Stella Ducay','Clerk 3',678000,0,2027,false),
  ('MANILA','Jinky','Clerk 3',678000,0,2027,false)
) AS v(office, name, role_title, salary, raise_pct, eff_year, is_office_cost)
WHERE NOT EXISTS (SELECT 1 FROM payroll_people x WHERE x.entity_id = p.id);

-- Member milestones
WITH p AS (SELECT id FROM entities WHERE code = 'polaris')
INSERT INTO payroll_milestones (entity_id, target_month, members_target)
SELECT p.id, v.m::date, v.members
FROM p, (VALUES ('2027-06-01', 20000), ('2028-03-01', 30000)) AS v(m, members)
WHERE NOT EXISTS (SELECT 1 FROM payroll_milestones x WHERE x.entity_id = p.id);

-- Assumptions (12) — keys = frontend ASSUMPTION_KEYS / backend PolarisForecastSummaryService
--   members_mode: 1 = χειροκίνητοι στόχοι (the mode the admin chose in production)
--   employer_contrib_pct: added 30 Oct 2026, 0 until the admin sets it
WITH p AS (SELECT id FROM entities WHERE code = 'polaris')
INSERT INTO forecast_assumptions (entity_id, key, value)
SELECT p.id, v.k, v.val
FROM p, (VALUES
  ('members', 16800),
  ('member_growth_pct', 0),
  ('network_fee', 24.5),
  ('monthly_fee', 0.625),
  ('audit_fee_rate', 0.15),
  ('dental_fee', 9.75),
  ('network_payable', 12),
  ('audit_payable_rate', 0.09),
  ('dental_payable', 5),
  ('fx_php_per_usd', 56.5),
  ('fx_usd_per_eur', 1.08),
  ('members_mode', 1),
  ('employer_contrib_pct', 0)
) AS v(k, val)
WHERE NOT EXISTS (SELECT 1 FROM forecast_assumptions x WHERE x.entity_id = p.id AND x.key = v.k);

-- Actuals (7 groups, Astrolavos trial balance, period 01/01/2026 – 27/10/2026)
WITH p AS (SELECT id FROM entities WHERE code = 'polaris')
INSERT INTO actuals_rows (entity_id, group_key, label, begin_amount, change_amount, period_start, period_end)
SELECT p.id, v.gk, v.label, v.begin_amt, v.change_amt, '2026-01-01'::date, '2026-10-27'::date
FROM p, (VALUES
  ('revenue','Membership fees (όλα τα policy-years)',401606,364913),
  ('revenue','Admin fees AMAPHIL',44278,38773),
  ('revenue','Plan management fees',50219,63360),
  ('revenue','Λοιπά έσοδα (τόκοι κ.ά.)',32,14),
  ('opsCosts','Κόστη membership / admin / claims',208259,179041),
  ('opsCosts','Τραπεζικά, ΦΠΑ, λοιπά λειτουργικά',33012,31820),
  ('payroll','Μισθοί & bonus (Κύπρος + Φιλιππίνες)',209868,169235),
  ('contributions','Εργοδοτικές εισφορές',28301,16139),
  ('taxes','Φόροι / τέλη',7106,59),
  ('adminOpexOther','Ενοίκιο / utilities',3263,3040),
  ('adminOpexOther','IT / Computer',30868,1571),
  ('adminOpexOther','Marketing / Διαφήμιση',1273,5654),
  ('adminOpexOther','Consultants / Audit / Legal',11634,8857),
  ('adminOpexOther','Ταξίδια / PR / λοιπά / σύνδεσμοι',19047,1554),
  ('adminOpexOther','Medical / 3rd-party (Φιλιππίνες)',6956,21928),
  ('other','Αναπροσαρμογές / συναλλαγματικές / rounding',3733,37210)
) AS v(gk, label, begin_amt, change_amt)
WHERE NOT EXISTS (SELECT 1 FROM actuals_rows x WHERE x.entity_id = p.id);

-- Verification
SELECT office, count(*), sum(salary) FROM payroll_people
  WHERE entity_id=(SELECT id FROM entities WHERE code='polaris') GROUP BY office;
SELECT target_month, members_target FROM payroll_milestones
  WHERE entity_id=(SELECT id FROM entities WHERE code='polaris') ORDER BY target_month;
SELECT key, value FROM forecast_assumptions
  WHERE entity_id=(SELECT id FROM entities WHERE code='polaris') ORDER BY key;
SELECT group_key, count(*), sum(begin_amount), sum(change_amount) FROM actuals_rows
  WHERE entity_id=(SELECT id FROM entities WHERE code='polaris') GROUP BY group_key;

-- Dry-run: leave ROLLBACK. Real run: replace with COMMIT.
ROLLBACK;
