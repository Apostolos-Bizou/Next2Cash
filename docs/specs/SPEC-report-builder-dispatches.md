# Report Builder — Επανασχεδίαση & Αρχείο Αποστολών
### Mini-spec / Handoff · 10 Αυγούστου 2026

**Project:** Next2Cash 🟢 LIVE PRODUCTION
**Κατηγορία αλλαγής:** 🟢 Γ — Καινούργιο χαρακτηριστικό (change-management)
**Ζήτησε:** Απόστολος (CEO)
**Κατάσταση:** ⏳ Εγκεκριμένο UX · Εκκρεμεί υλοποίηση
**Πηγή προδιαγραφής:** `next2cash-report-builder-v4.html` (λειτουργικό mockup)

---

## 1. Πραγματική ανάγκη

Το Report Builder δεν είναι καρτέλα προμηθευτή. Είναι **ελεύθερη λίστα**: ο χρήστης
βάζει δικό του τίτλο, διαλέγει ελεύθερα κινήσεις (έσοδα και έξοδα), βγαίνουν σύνολα,
και στέλνει PDF στο λογιστήριο για να αποζημιωθεί για δαπάνες που πλήρωσε προσωπικά.

Τρία προβλήματα στην υπάρχουσα έκδοση:

| # | Πρόβλημα | Επίπτωση |
|---|---|---|
| 1 | Στενό πλαϊνό panel επιλογής, κομμένες περιγραφές | Δεν διακρίνεις τι διαλέγεις |
| 2 | Καμία ένδειξη τι έχει ήδη μπει στο report | Διπλοεγγραφές, σύγχυση |
| 3 | Καμία δυνατότητα αφαίρεσης μετά την προσθήκη | Λάθος = ξεκινάς από την αρχή |
| 4 | Δεν υπάρχει ιστορικό τι στάλθηκε, πότε, σε ποιον | Δεν ξέρεις τι εκκρεμεί προς απόδοση |

---

## 2. Αποφάσεις που κλείδωσαν

### 2.1 Αρχιτεκτονική αποστολών

**Επιλέχθηκε πίνακας αποστολών, όχι σημαία στην κίνηση.**

Δεν στέλνεις *κινήσεις*, στέλνεις *ένα report*. Η ίδια κίνηση μπορεί να σταλεί σε
δεύτερο παραλήπτη χωρίς να χαθεί η πρώτη αποστολή. Απαραίτητο για τεκμηρίωση όταν
κάποιος αμφισβητήσει ότι το έλαβε.

### 2.2 Ορολογία — δύο ανεξάρτητοι άξονες

| Άξονας | Τιμές | Σημασία |
|---|---|---|
| **Πληρωμή** (υπάρχων) | Εξοφλημένη · Εκκρεμής | Αν έχει πληρωθεί |
| **Αποστολή** (νέος) | Απεσταλμένο · Μη απεσταλμένο | Αν έχει σταλεί σε κάποιον |

⚠️ Καμία κοινή λέξη ανάμεσά τους. Η λέξη «εκκρεμής» ανήκει **αποκλειστικά** στην
πληρωμή. Ένα έξοδο μπορεί να είναι πληρωμένο και μη απεσταλμένο, ή απλήρωτο και
απεσταλμένο.

### 2.3 Δρομολόγηση κινήσεων

Η ενότητα προορισμού καθορίζεται **αποκλειστικά από το πρόσημο του ποσού**, ποτέ από
το κουμπί που άνοιξε το παράθυρο επιλογής. Θετικό → Εισπράξεις, αρνητικό → Έξοδα.

⚠️ Πρέπει να επιβληθεί **και στο backend**, όχι μόνο στο UI.

### 2.4 Δομή PDF

Πρότυπο = **το υπάρχον production CashControl PDF**, αυτούσιο. Τρεις διαφορές μόνο:

1. Πλαίσιο τίτλου λέει `ΤΙΤΛΟΣ ΑΝΑΦΟΡΑΣ` αντί `ΠΡΟΜΗΘΕΥΤΗΣ`, με παραλήπτη και
   ημερομηνία αποστολής από κάτω
2. **Μία ενιαία χρονολογική ροή** — έσοδα και έξοδα ανακατεμένα, νεότερο πρώτο.
   Όχι χωριστές ενότητες
3. Μπλοκ `ΣΥΝΟΨΗ` στο τέλος: Σύνολο Εισπράξεων · Σύνολο Εξόδων · Εκκρεμείς πληρωμές
   (μόνο αν υπάρχουν) · Καθαρό Υπόλοιπο

**Χρωματικός κανόνας:** έσοδα πράσινα, έξοδα κόκκινα — στις στήλες ΠΟΣΟ και
ΠΛΗΡΩΜΕΝΟ και στα KPI. Το πλακίδιο ΠΛΗΡΩΜΕΝΟ σπάει σε `έξοδα / έσοδα`.

**Προσανατολισμός:** Οριζόντιο Α4 εξ ορισμού (δέκα στήλες). Κατακόρυφο ως επιλογή.

### 2.5 Λοιπά

- Παραλήπτης = **ελεύθερο κείμενο** με autocomplete από προηγούμενες αποστολές.
  Δεν χρειάζεται μητρώο επαφών
- Το PDF **παράγεται και αποθηκεύεται αυτόματα** — δεν το ανεβάζει ο χρήστης
- Θέμα **ανοιχτό/σκούρο** σε όλες τις οθόνες, με προεπιλογή από το λειτουργικό
- Το χαρτί του PDF μένει λευκό και στα δύο θέματα

---

## 3. Τεχνικό σχέδιο

### 3.1 Database

```sql
CREATE TABLE IF NOT EXISTS report_dispatches (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  entity_id       UUID NOT NULL REFERENCES entities(id),
  title           TEXT NOT NULL,
  recipient       TEXT NOT NULL,
  sent_date       DATE NOT NULL,
  note            TEXT,
  blob_path       TEXT,              -- Azure Blob: το παραχθέν PDF
  created_by      UUID NOT NULL REFERENCES users(id),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS report_dispatch_items (
  dispatch_id     UUID NOT NULL REFERENCES report_dispatches(id) ON DELETE CASCADE,
  transaction_id  INTEGER NOT NULL REFERENCES transactions(id),  -- transactions.id = serial INTEGER, ΟΧΙ UUID (codebase audit S105)
  PRIMARY KEY (dispatch_id, transaction_id)
);

CREATE INDEX IF NOT EXISTS ix_rdi_tx  ON report_dispatch_items(transaction_id);
CREATE INDEX IF NOT EXISTS ix_rd_ent  ON report_dispatches(entity_id, sent_date DESC);
```

Migration policy Next2Cash: **manual idempotent SQL μέσω psql**, όχι Flyway.

### 3.2 Backend

| Στοιχείο | Αρχείο |
|---|---|
| Entity | `ReportDispatch.java`, `ReportDispatchItem.java` |
| Repository | `ReportDispatchRepository.java` |
| Service | `ReportDispatchService.java` |
| Controller | `ReportDispatchController.java` |
| PDF | **νέο** `ReportDispatchPdfService`, modelled στο `CardExportService` (OpenPDF + embedded DejaVuSans). ΟΧΙ επέκταση του `ReportExportService` — εκείνο είναι AI-analysis exporter (iText7, ADMIN-only, δέχεται `AiQueryHistory`), λάθος domain (codebase audit S105) |

**Endpoints:**

```
GET    /api/report-dispatches?entity_id=&from=&to=&q=
GET    /api/report-dispatches/{id}
GET    /api/report-dispatches/{id}/pdf        → stream από Blob
POST   /api/report-dispatches                 → παράγει PDF + αρχειοθετεί
POST   /api/report-dispatches/preview         → stream PDF, ΚΑΜΙΑ εγγραφή (no writes, no Blob)
DELETE /api/report-dispatches/{id}            → ADMIN μόνο
GET    /api/transactions/dispatch-status?ids= → batch για badges (ids = INTEGER csv)
```

### 3.3 Frontend

| Στοιχείο | Αρχείο |
|---|---|
| Κύρια οθόνη | `ReportBuilderView.vue` (τροποποίηση) |
| Νέα οθόνη | `DispatchArchiveView.vue` |
| Components | `TransactionPickerModal.vue`, `DispatchDialog.vue`, `PdfPreviewModal.vue` |
| Θέμα | `assets/theme.css` — 48 μεταβλητές, `:root` + `[data-theme="light"]` |

⚠️ Το `theme.css` προορίζεται για **όλο το Group** (Next2View, POLARIS-Vue,
Logosynthesi). Να μπει σε κοινό σημείο, όχι μόνο στο Next2Cash.

---

## 4. Invariants — δεν παραβιάζονται ποτέ

| # | Κανόνας | Έλεγχος |
|---|---|---|
| 1 | `VIEWER` **εκτός ΟΛΩΝ** των `/api/report-dispatches/*` — και από τα GET. Ο Σίμος Βαρίας δεν βλέπει καθόλου το αρχείο αποστολών. Όλα τα endpoints `@PreAuthorize("hasAnyRole('ADMIN','USER')")`, κανένα με `VIEWER` | `Select-String -Path "src\main\java\**\ReportDispatchController.java" -Pattern "VIEWER"` → **0 hits** |
| 2 | `PLANNED` κινήσεις **δεν** αρχειοθετούνται ως απεσταλμένες | Φίλτρο στο service, όχι στο UI |
| 3 | `UserAccessService.getCurrentUser()` + `canAccessEntity()` σε κάθε νέο endpoint | Code review |
| 4 | `filterApiEntities()` σε κάθε frontend κλήση entities | Code review |
| 5 | `vite.config.js secure:true` ×2 πριν από merge σε main | `Select-String -Path "vite.config.js" -Pattern "secure:\s*true"` |
| 6 | Πρόσημο καθορίζει ενότητα — επιβολή στο backend | 400 σε αρνητικό ποσό προς `INCOME` |

---

## 5. Σειρά εκτέλεσης

Από κάτω προς τα πάνω, **ξεχωριστό commit ανά επίπεδο** ώστε rollback ενός
επιπέδου να μη χάνει τα υπόλοιπα.

1. **Database** — migration + entities + repositories
2. **Service** — `ReportDispatchService` + unit tests (6-10)
3. **Controller** — endpoints + integration tests
4. **PDF** — πρότυπο ΑΝΑΦΟΡΑ στο `ReportExportService`, με pagination
5. **Frontend** — picker, dialog, archive, preview
6. **Theme** — `theme.css` και εφαρμογή

---

## 6. Ανοιχτά σημεία

| Θέμα | Κατάσταση |
|---|---|
| Κίνηση με ποσό ακριβώς `0,00 €` — πού πάει; | ❓ Αναμένεται απόφαση |
| Pagination PDF (>1 σελίδα, επανάληψη κεφαλίδας, «σελίδα 1/3») | ⚠️ Να δοκιμαστεί νωρίς με 40+ κινήσεις |
| Στήλες ΠΛΗΡΩΜΕΝΟ/ΥΠΟΛΟΙΠΟ σε γραμμές εσόδων | Κρατήθηκαν όπως στο production |
| Αποθήκευση προτίμησης θέματος | Προτείνεται στο προφίλ χρήστη (DB), όχι τοπικά |

---

## 7. Κριτήρια αποδοχής

- [ ] Επιλογή 3 εσόδων + 4 εξόδων από tab «Όλες» τα δρομολογεί σωστά
- [ ] Κίνηση ήδη στο report δεν ξαναμπαίνει· εμφανίζει πού βρίσκεται
- [ ] Αφαίρεση γραμμής και καθαρισμός ενότητας με αναίρεση
- [ ] Αποστολή παράγει PDF, το αποθηκεύει, εμφανίζεται στο αρχείο
- [ ] Αναζήτηση στο αρχείο με ID κίνησης βρίσκει την αποστολή
- [ ] Διαγραφή αποστολής επαναφέρει τις κινήσεις σε «μη απεσταλμένο»
- [ ] Ο Σίμος Βαρίας (VIEWER, incognito) βλέπει το αρχείο, **δεν** καταχωρίζει
- [ ] Ο Σίμος βλέπει μόνο Next2Me Group — καμία διαρροή entity
- [ ] Ανοιχτό/σκούρο σε όλες τις οθόνες και τα modals
- [ ] Ελληνικά χωρίς mojibake σε UI και PDF
