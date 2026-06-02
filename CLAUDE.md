# CLAUDE.md — Next2Cash

## Quick context
- **Status:** 🟢 LIVE PRODUCTION (Phase 4 cutover done 01 Jun 2026) — system of record για το Group cash flow
- **Frontend:** https://www.next2cash.com
- **Backend:** https://next2cash-api.azurewebsites.net
- **Repo:** Apostolos-Bizou/Next2Cash (PRIVATE)
- **Users:** 4 — Απόστολος Βίζου (admin, all entities), Sissy (user, data entry), Λεωνίδας (accountant, read-only + ZIP export), Σίμος Βαρίας (viewer, **ONLY Next2Me Group**, investor)
- **Stack:** Vue 3.5.13 + Vite 6.0.3 + Pinia 2.2.6 + Spring Boot 3.2.4 (Java 17) + PostgreSQL 15 + Azure

## Entities (3)
| Code | Name | Currency | Transactions |
|---|---|---|---|
| `next2me` | Next2Me | EUR | 4.764 |
| `house` | House | EUR | 187 |
| `next2megroup` | Next2Me Group | EUR | 41 |
Total: 4.992 live transactions · 19 bank accounts · 25 recurrence patterns

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
- Last session: **S102.5** (ObligationsView grid 3-col layout for action buttons)
- HEAD: `f7b6303` (main, clean tree) · Last stable tag: `after-s102.5-grid-cols-20260531_231629`
- Tests: 211 passing across 28 files · Backend: 22 controllers / 18 services / 14 repos / 15 entities · Frontend: 20 views

## Pending / Backlog
- 🔧 Security debt (HIGH): App Service `httpsOnly = FALSE` → flip true · server-side entity filter · rotate PostgreSQL password + JWT secret · migrate secrets to Key Vault
- S89.2 white-theme NewEntryView (locally patched, ready to deploy)
- Per-investor reports (Simos-facing PDF/Excel) · multi-currency `fxRate` live API · Azure region Central US → North Europe

## Project-specific risks
- **Σίμος Βαρίας (investor)** = single most dangerous user — verify σε incognito μετά από κάθε security-touching change
- **Mojibake (S87.12):** Greek text σε Vue files = ΠΟΤΕ PowerShell direct rewrite
- **2-deploys limit:** μετά από 2 αποτυχημένα deploys → STOP, διάγνωση πρώτα

## Related skills (auto-loaded)
- project-router · project-filing · group-stack-conventions · change-management · next2cash-dev · cli-coding-workflow
