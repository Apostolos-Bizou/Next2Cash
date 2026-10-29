# CLAUDE.md — Next2Cash

## Quick context
- **Status:** 🟢 LIVE PRODUCTION (Phase 4 cutover done 01 Jun 2026) — system of record για το Group cash flow
- **Frontend:** https://www.next2cash.com
- **Backend:** https://next2cash-api.azurewebsites.net
- **Repo:** Apostolos-Bizou/Next2Cash (PRIVATE)
- **Users:** 4 — Απόστολος Βίζου (admin, all entities), Sissy (user, data entry), Λεωνίδας (accountant, read-only + ZIP export), Σίμος Βαρίας (viewer, **ONLY Next2Me Group**, investor)
- **Stack:** Vue 3.5.13 + Vite 6.0.3 + Pinia 2.2.6 + Spring Boot 3.2.4 (Java 17) + PostgreSQL 15 + Azure

## Entities (4)
| Code | Name | Currency | Transactions |
|---|---|---|---|
| `next2me` | Next2Me | EUR | 4.764 |
| `house` | House | EUR | 187 |
| `next2megroup` | Next2Me Group | EUR | 41 |
| `polaris` | Polaris (P&L module, Oct 2026) | USD | — (no cash-flow transactions; payroll/forecast/actuals tables only) |
Total: 4.992 live transactions · 19 bank accounts · 25 recurrence patterns

## Polaris P&L module (Oct 2026)
- Views: `/polaris/dashboard` (any Polaris role incl. global VIEWER), `/polaris/payroll`, `/polaris/forecast` (admin/user/accountant). Access = `entity_roles` (admin / editor / viewer) checked per endpoint via `UserAccessService.assertPolaris*`.
- Tables (manual SQL, no Flyway): `payroll_people`, `payroll_person_raises`, `payroll_milestones`, `forecast_assumptions`, `cost_milestones`, `actuals_rows`, `entity_roles`, `user_notices`.
- Math parity: `PolarisForecastMath.java` ↔ `frontend/src/stores/polarisMath.js` (tests on both sides, hand-verified fixtures). Rules: members interpolate; payroll raises = compounding STEPS; **OPEX = STEP from $0** (latest target dated ≤ the point, 30 Oct 2026); employer contributions = `employer_contrib_pct` % of STAFF salaries (rent rows excluded).
- OPEX categories (9): Consultants, Audit, Legal, IT / Computer, Marketing / Διαφήμιση, Ταξίδια, PR, Λοιπά, Σύνδεσμοι — Astrolavos actuals stay combined (`OPEX_ACTUALS_LABEL`), run-rate hint shown as the combined figure.
- Excel export: `GET /api/polaris/forecast/export?years=1|3|5` (admin+editor, never VIEWER) → `PolarisForecastExportService` (Apache POI, same pattern as `CardExportService`). Export only — no import path.
- Seed / migrations: `docs/specs/polaris_seed.sql` (canonical, idempotent). `cost_milestones` was emptied on 29 Oct 2026 (the one legacy combined-category test row deleted, backup `next2cash-db_pre-cost-milestone-delete-COMMIT_20261029_220038.dump`), so all 9 categories start at $0.

## Local setup
- Path: `C:\Users\akage\Documents\Next2Cash`
- Frontend dev: `cd frontend && npm run dev -- --mode azure` (points to production API — NO local backend/DB)
- Backend build: `C:\maven\bin\mvn.cmd clean package -DskipTests`
- Backups: `C:\Users\akage\Documents\Next2Cash_Backups\`

## Deploy flow
- Frontend: push to main → GitHub Actions → Azure Static Web Apps (~90 sec)
- Backend: manual ZIP deploy `az webapp deploy --resource-group next2cash-rg --name next2cash-api --src-path target\next2cash-1.0.0.jar --type jar` (~7 min, cold boot ~90-170 sec)
- Migration policy: manual idempotent SQL via `psql` — NO Flyway (deliberate)

## Critical invariants (NEVER touch χωρίς ρητή έγκριση)
1. **VIEWER ΜΟΝΟ σε GET methods** — `+VIEWER` στο @PreAuthorize ποτέ σε POST/PUT/DELETE (Σίμος = επενδυτής, write access σπάει τη σχέση)
2. **PLANNED transactions ΟΧΙ σε operational views** — Burn/Runway/Bank Balance χρησιμοποιούν ACTUAL only (εξαίρεση: ObligationsView S102)
3. **`UserAccessService.getCurrentUser()`** σε κάθε entity-scoped endpoint (S77 guard — bypass = cross-entity leak)
4. **`filterApiEntities()`** στο frontend (S100 hardening — μόνη defense, backend ακόμα επιστρέφει όλες τις entities)
5. **`vite.config.js secure:true`** (×2 — server + preview proxy)

## Recent context
- Last session: **30 Oct 2026 — Polaris backlog close-out** (OPEX step function + 9 categories, `employer_contrib_pct`, Excel export, branch/firewall/password cleanup). Before that: Polaris P&L Phases 0–4 (PRs #1–#6, 27–29 Oct 2026); S103 (Projects board lifecycle sort) was the last pre-Polaris session.
- Last stable tags: `after-polaris-opex-raises` (29 Oct 2026) · `after-status-edit-fix-20260604_154346` (pre-Polaris)
- Branch hygiene (30 Oct 2026): every merged `feat/*`, `feature/*`, `fix/*` branch deleted locally and on origin; only `main`, `docs/add-claude-md`, `feat/phase-b-entity-filtering`, `feature/pdf-download-filename` (unmerged) remain.
- Tests: 25 frontend (node --test, 4 files) + 485 backend JUnit (39 test files; Polaris classes: Math, Summary, Export, OpexAndRaises, Payroll, Forecast, PreAuthorizeInvariant, UserAccessServicePolaris) · Backend: 30 controllers / 23 services / 25 repos / 27 entities · Frontend: 24 views

## Pending / Backlog
- 🔧 Security debt (HIGH): App Service `httpsOnly = FALSE` → flip true · server-side entity filter · rotate JWT secret (still hardcoded in `application.properties`, overridden by App Service `NEXT2CASH_JWT_SECRET`) · migrate secrets to Key Vault. DONE 30 Oct 2026: stale PostgreSQL password removed from `application.properties` (`${SPRING_DATASOURCE_PASSWORD:}`), 4 orphaned `akage-*` DB firewall rules deleted (only `AllowAzureServices` remains; temp rules per session, deleted in `finally`).
- Polaris: set `employer_contrib_pct` in Παραδοχές (defaults to 0) · add the first OPEX targets per category (table is empty) · per-investor reports (Simos-facing PDF/Excel)
- S89.2 white-theme NewEntryView (locally patched, ready to deploy)
- multi-currency `fxRate` live API · Azure region Central US → North Europe

## Project-specific risks
- **Σίμος Βαρίας (investor)** = single most dangerous user — verify σε incognito μετά από κάθε security-touching change
- **Mojibake (S87.12):** Greek text σε Vue files = ΠΟΤΕ PowerShell direct rewrite
- **2-deploys limit:** μετά από 2 αποτυχημένα deploys → STOP, διάγνωση πρώτα

## Related skills (auto-loaded)
- project-router · project-filing · group-stack-conventions · change-management · next2cash-dev · cli-coding-workflow
