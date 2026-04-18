# Next2Cash Session #14 Handoff — QuickStart

**From CEO:** Απόστολος Βίζου
**Project:** Next2Cash (ACashControl → Next2Cash migration)
**Last session:** #13 Phase I Exports — COMPLETE ✅
**Current HEAD:** phase-i-exports-complete-20260419
**Frontend live:** https://www.next2cash.com
**Backend live:** https://next2cash-api.azurewebsites.net

═══════════════════════════════════════════════════════════════
0. ΑΠΟΛΥΤΟΙ ΚΑΝΟΝΕΣ (ΑΠΑΡΑΒΑΤΟΙ)
═══════════════════════════════════════════════════════════════
⛔ ΜΗΝ τρέξεις conversation_search, recent_chats, ή project_knowledge_search.
⛔ ΜΗΝ ζητήσεις επιβεβαίωση για stack, workflow, ή scope — είναι κλειδωμένα.
⛔ ΜΗΝ πεις "πριν ξεκινήσω χρειάζομαι Χ" — έχεις τα πάντα εδώ.
⛔ ΜΗΝ κάνεις ψάξιμο αρχείων — ότι χρειάζεσαι σου το στέλνω με PowerShell.
⛔ ΜΗΝ με ρωτήσεις τι είναι το project, το stack, οι εταιρείες — γράφει ΠΑΡΑΚΑΤΩ.
✅ ΞΕΚΙΝΑ ΑΜΕΣΩΣ με το PowerShell block της Section 20.

═══════════════════════════════════════════════════════════════
1. ΠΟΙΟΣ ΕΙΜΑΙ & ΠΩΣ ΔΟΥΛΕΥΟΥΜΕ
═══════════════════════════════════════════════════════════════
- Είμαι ο CEO. ΔΕΝ γράφω κώδικα. ΔΕΝ ανοίγω IDE/editor/Azure Portal.
- Εσύ μου δίνεις PowerShell commands σε ΕΝΑ block, εγώ τα τρέχω,
  σου στέλνω output, προχωράς.
- ΠΑΝΤΑ ΟΛΑ ΜΑΖΙ σε ένα copy-paste block — ΠΟΤΕ γραμμή-γραμμή.
- Επικοινωνία: Ελληνικά στα μηνύματα, Αγγλικά στον κώδικα + comments.
- Best default αν δεν ζητήσω κάτι συγκεκριμένο. Εξήγηση max 1 παράγραφος.
- Options με αριθμούς (1/2/3) — απαντάω με αριθμό.
- Για long-running commands: βάλε elapsed timer ή spinner στο PowerShell block.

═══════════════════════════════════════════════════════════════
2. ΠΟΥ ΒΡΙΣΚΟΜΑΣΤΕ (commit-accurate, 19 Apr 2026)
═══════════════════════════════════════════════════════════════
Repo:    C:\Users\akage\Documents\Next2Cash (Windows, PowerShell, Git)
GitHub:  https://github.com/Apostolos-Bizou/Next2Cash
HEAD:    phase-i-exports-complete-20260419
Tests:   71/71 green (~25s με mvn test)
Tree:    CLEAN
Frontend live: https://www.next2cash.com
Backend live:  https://next2cash-api.azurewebsites.net

Ολοκληρωμένες sessions:
  #3-#8 Phase A-F: Security tests → 51 tests
  #9  Phase G: Edit/Delete/Mark Paid — LIVE
  #10 Phase H v1: Counterparty groupings (DEPLOYED αλλά product mismatch, dead code)
  #11 Phase H v2 Backend: Full CRUD + rule engine + 11 tests — LIVE
  #12 Phase H v2 Frontend: KartelesView + CardFormModal + Delete — LIVE
  #13 Phase I Exports: Excel + PDF + modal + print preview — LIVE ✅

═══════════════════════════════════════════════════════════════
3. ΤΙ ΕΙΝΑΙ ΗΔΗ LIVE (όλα δουλεύουν σε production)
═══════════════════════════════════════════════════════════════
Backend endpoints (auth=Bearer JWT):
  GET    /api/config/cards?entityId=X
  GET    /api/config/cards/{id}/transactions?entityId=X&limit=2000&offset=0
  GET    /api/config/cards/{id}/summary?entityId=X
  POST   /api/config/cards?entityId=X
  PUT    /api/config/cards/{id}?entityId=X
  DELETE /api/config/cards/{id}?entityId=X
  GET    /api/config/cards/{id}/export/excel?entityId=X&filename=Y  [NEW in #13]
  GET    /api/config/cards/{id}/export/pdf?entityId=X&filename=Y    [NEW in #13]

Roles:
  GET:             ADMIN, USER, VIEWER
  POST/PUT/DELETE: ADMIN, USER (viewer 403)
  Exports:         ADMIN, USER, VIEWER (read-only op)

Frontend (όλα working):
  KartelesView.vue       — sidebar + 5 KPI cards + transactions table + 4 filters
  CardFormModal.vue      — create/edit με 4-type rule builder
  ExportFilenameModal.vue [NEW]  — Excel/PDF download modal με smart filename
  Delete confirmation    — με 404 recovery handling

Rule grammar (parent_key column):
  search:ΜΑΛΑΜΙΤΣΗΣ         — μια λέξη στη description
  search:ΕΣΟΔΑ Β            — AND (όλες οι λέξεις)
  search:ΔΕΗ,ΔΕΔΔΗΕ         — OR (comma-separated)
  category:ΛΕΙΤΟΥΡΓΙΚΑ      — exact match
  subcategory:ΕΝΟΙΚΙΟ       — exact match
  counterparty:ΟΝΟΜΑ        — exact match
  Case-insensitive + accent-insensitive (Greek NFD normalization)

Export UX:
  Excel → click button → modal → confirm filename → direct download .xlsx
  PDF   → click button → modal → confirm filename → opens new tab
                                                   → auto-triggers print dialog
                                                   → user can "Save as PDF"

═══════════════════════════════════════════════════════════════
4. PHASE I EXPORTS — ΤΙ ΠΑΡΑΔΟΘΗΚΕ
═══════════════════════════════════════════════════════════════
Files touched σε session #13:
  backend/pom.xml
    → Apache POI 5.2.5 + OpenPDF 1.3.30 added
  backend/src/main/resources/fonts/DejaVuSans.ttf (NEW, 741 KB)
    → Embedded Unicode font for PDF Greek support
  backend/src/main/java/com/next2me/next2cash/service/CardExportService.java (NEW, ~700 lines)
    → generateExcel() + generatePdf() + sanitizeForFilename() + nonEmpty()
    → 1:1 legacy colors: #162B40 navy, #27ae60 green, #e74c3c red, #ff6400 orange
    → Uses Transaction.paymentMethod + Transaction.paymentDate (populated)
  backend/src/main/java/com/next2me/next2cash/controller/ConfigController.java
    → +2 endpoints (export/excel, export/pdf)
    → DTO enriched με paymentMethod, paymentDate, recordStatus
    → resolveFilename() helper με fallback to Kartela_[NAME]_DD-MM-YYYY
  backend/src/test/java/com/next2me/next2cash/service/CardExportServiceTest.java (NEW)
    → 5 tests: Excel non-empty, Excel empty, PDF non-empty, PDF empty, sanitization
    → Extends BaseIntegrationTest, uses TestDataBuilder + reflection pattern
  frontend/src/components/ExportFilenameModal.vue (NEW, ~250 lines)
    → Pre-filled smart default "Kartela_<NAME>_DD-MM-YYYY"
    → Keyboard shortcuts (Enter/Esc)
    → PDF vs Excel label adaptive
  frontend/src/views/KartelesView.vue
    → 2 header buttons (Excel, PDF) with :disabled="!selectedCard"
    → Axios blob download + Accept header override (bypass api.js JSON default)
    → openPdfPreview() helper: new tab + window.print() auto-trigger
    → Blob error response parsing (decode blob → text → JSON for readable errors)

Tests: 66 → 71 green ✅
Commits in session #13:
  654795a  feat(exports): Phase I — Excel + PDF export for cards
  ef2b466  feat(karteles): Phase I frontend — Excel/PDF export buttons + filename modal
  f726a4e  fix(exports): Phase I-fix — paymentMethod + paymentDate now populated
  [pending Phase I-preview — needs push if separate commit]
Tags pushed:
  phase-i-exports-complete-20260419 (CURRENT HEAD)
  pre-phase-i-exports-20260419       (pre-session safety tag)

═══════════════════════════════════════════════════════════════
5. STACK (ΚΛΕΙΔΩΜΕΝΟ — μην προτείνεις εναλλακτικές)
═══════════════════════════════════════════════════════════════
Frontend: Vue 3 (Vite) → Azure Static Web Apps (auto-deploy on push)
Backend:  Spring Boot 3.2.4 + Java 17 + Maven → Azure App Service (manual)
DB:       PostgreSQL 15 (Azure) / H2 (tests)
Auth:     Azure AD + JWT + role-based (@PreAuthorize)
SCM:      GitHub → Apostolos-Bizou/Next2Cash

Dependencies added in #13:
  Apache POI 5.2.5 (org.apache.poi:poi-ooxml)
  OpenPDF 1.3.30  (com.github.librepdf:openpdf)
  DejaVuSans.ttf  (FOSS Unicode font, embedded Greek support)

═══════════════════════════════════════════════════════════════
6. KNOWN GAPS & PENDING (για μετά)
═══════════════════════════════════════════════════════════════
🟡 **Phase K — Payments table JOIN** (highest priority)
   Το legacy σύστημα δείχνει 40 rows για ΜΑΛΑΜΙΤΣΗΣ (34 expenses + 6 payments
   με negative IDs -26..-31). Το νέο σύστημα δείχνει μόνο τα 34 expenses.
   Τα payment records είναι σε ξεχωριστό table (schema inspection pending).
   Scope: schema analysis + JOIN logic σε CardService + rule engine extension
          + KPI reconciliation (avoid double-counting) + new tests.
   Εκτίμηση: 3-4 ώρες.

🟡 PDF header wrapping: "ΠΛΗΡΩΜΕΝΟ" σπάει σε "ΠΛΗΡΩΜΕ/ΝΟ" (cosmetic).
   Fix: tune column widths {5,8,22,12,11,8,10,8,11,10} ή παρόμοιο.

🟡 amountPaid bug σε Mark Paid (TransactionController, from #9)
🟡 Backend CI/CD με GitHub Actions (manual deploy now)
🟡 Backend search bug fix (pending Testcontainers)
🟡 AdminView hardcoded entity UUIDs
🟡 DocumentController security audit
🟡 BankAccountController full CRUD
🔴 Password rotation — DEFERRED per CEO
⛔ Legacy σύστημα (GAS+Sheets+GitHub Pages) — ΠΟΤΕ δεν αγγίζεται
⛔ Phase H v1 endpoints (/counterparties, /by-counterparty) — dead code

═══════════════════════════════════════════════════════════════
7. POWERSHELL — ΚΑΝΟΝΕΣ (ΑΠΑΡΑΒΑΤΟ)
═══════════════════════════════════════════════════════════════
⛔ ΠΟΤΕ Out-File -Encoding UTF8 (βάζει BOM → σπάει Java compile)
⛔ ΠΟΤΕ Set-Content χωρίς explicit encoding
⛔ ΠΟΤΕ $str.Replace() απευθείας στο PowerShell — silent fails
⛔ ΠΟΤΕ git add . — μόνο specific files
⛔ ΠΟΤΕ return μέσα σε if/else στο interactive shell
✅ ΠΑΝΤΑ Node.js safe-replace script για Java/Vue edits
✅ ΠΑΝΤΑ regex anchors αντί για exact strings (mojibake resistance)
✅ ΠΑΝΤΑ absolute paths σε WebClient/Invoke-WebRequest downloads
✅ ΠΑΝΤΑ Push-Location / Pop-Location αντί για cd ..
✅ ΠΑΝΤΑ explicit file existence check πριν από deploy

═══════════════════════════════════════════════════════════════
8. GIT WORKFLOW
═══════════════════════════════════════════════════════════════
Πριν:
    git tag pre-<phase>-<date>
    git push origin pre-<phase>-<date>

Staging:
    git add <specific files>      ← ΠΟΤΕ git add .
    git status --short             ← verify staged
    git commit -m "feat(scope): ..."
    git push origin main

Μετά:
    git tag <phase>-complete-<date>
    git push origin <phase>-complete-<date>

═══════════════════════════════════════════════════════════════
9. AZURE DEPLOY (backend, manual)
═══════════════════════════════════════════════════════════════
Resources (ΠΟΤΕ μη ρωτήσεις):
  RG:            next2cash-rg (Central US)
  App Service:   next2cash-api
  PostgreSQL:    next2cash-db (v15, B1ms)
  Blob Storage:  next2cashdocs
  Static Web:    next2cash-frontend → next2cash.com

Build + Deploy (~6 min total):
    Push-Locati+R για cache)

═══════════════════════════════════════════════════════════════
11. ROLLBACK TAGS (origin already has them)
═══════════════════════════════════════════════════════════════
phase-i-exports-complete-20260419       ← CURRENT HEAD
pre-phase-i-exports-20260419             ← πριν τη #13
phase-h-v2-frontend-complete-20260419    ← τέλος #12
phase-h-v2-backend-complete-20260419     ← τέλος #11
phase-h-complete-20260418                ← τέλος #10 (v1)
phase-g-complete-20260418                ← #9

Rollback:  git reset --hard <tag>

═══════════════════════════════════════════════════════════════
12. ΓΙΑ ΤΗΝ SESSION #14 — ΠΙΘΑΝΕΣ ΚΑΤΕΥΘΥΝΣΕΙΣ
═══════════════════════════════════════════════════════════════
Ο CEO θα αποφασίσει ποιο pick-up task. Options:

A) Phase K — Payments table JOIN (40 vs 34 MALAMITSIS issue)
   Priority: HIGH, εκτίμηση 3-4h

B) Phase J — Mark Paid + amountPaid bug fix (from #9 pending)
   Priority: MEDIUM, εκτίμηση 1-2h

C) PDF column width tuning (cosmetic, ΠΛΗΡΩΜΕΝΟ wrapping)
   Priority: LOW, εκτίμηση 30 min

D) Backend CI/CD με GitHub Actions (remove manual deploy step)
   Priority: MEDIUM, εκτίμηση 2h

Default μου αν δεν σπάσει σε options: A (Phase K — payments JOIN).
Είναι data-integrity issue που αφορά end-user (λογιστή), οπότε
πιο σημαντικό από infra cleanup.

═══════════════════════════════════════════════════════════════
13. ΠΡΩΤΗ ΣΟΥ ΕΝΕΡΓΕΙΑ ΣΕ SESSION #14
═══════════════════════════════════════════════════════════════
Δώσε μου ΚΑΤΕΥΘΕΙΑΝ ΜΙΑ PowerShell εντολή που κάνει ΟΛΑ μαζί:

  1. cd C:\Users\akage\Documents\Next2Cash
  2. git log -1 --oneline
  3. git status
  4. mvn test (expect 71/71)
  5. Invoke-WebRequest https://next2cash-api.azurewebsites.net/api/health
  6. Invoke-WebRequest https://www.next2cash.com
  7. Ερώτηση με options: ποιο task από την Section 12 να κάνουμε;

Στόχος: confirm infrastructure σταθερό + scope decision σε 1 block.

ΠΗΓΑΙΝΕ.
═══════════════════════════════════════════════════════════════
