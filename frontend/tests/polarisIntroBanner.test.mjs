// node --test — the one-time OPEX / raises intro banner (source-level).
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const read = p => readFileSync(join(here, p), 'utf8')
const banner = read('../src/components/PolarisIntroBanner.vue')
const notices = read('../src/api/notices.js')
const forecast = read('../src/views/PolarisForecastView.vue')
const payroll = read('../src/views/PolarisPayrollView.vue')
const store = read('../src/stores/polarisPl.js')

test('banner carries the exact agreed text (not paraphrased)', () => {
  const must = [
    'Νέο: Πρόβλεψη Λειτουργικών Εξόδων &amp; πολλαπλές αυξήσεις μισθού',
    'Στη Μισθοδοσία, κάθε άτομο μπορεί τώρα να έχει πάνω από μία μελλοντική αύξηση — άνοιξε τη γραμμή του για να προσθέσεις ημερομηνία + ποσοστό.',
    'Στις Προβλέψεις, η ενότητα «Πρόβλεψη Λειτουργικών Εξόδων» έχει 9 κατηγορίες: Consultants, Audit, Legal, IT/Computer, Marketing/Διαφήμιση, Ταξίδια, PR, Λοιπά, Σύνδεσμοι.',
    'Ο Αστρολάβος δίνει ένα συνδυασμένο ποσό για Consultants+Audit+Legal και ένα για Ταξίδια+PR+Λοιπά+Σύνδεσμοι — το ίδιο ποσό εμφανίζεται ως αναφορά δίπλα σε κάθε υποκατηγορία· τον επιμερισμό τον κάνεις εσύ.',
    'κάθε κατηγορία ξεκινάει στα $0 μέχρι να προσθέσεις τον πρώτο στόχο, και κάθε στόχος ισχύει ΒΗΜΑΤΙΚΑ από τον μήνα του και μετά, όπως οι αυξήσεις μισθών (όχι γραμμική παρεμβολή ανάμεσα σε στόχους).',
    'πρόσθεσε πρώτα έναν στόχο κοντά στο σήμερα με το τρέχον ετήσιο run-rate της κατηγορίας, και μετά τους μελλοντικούς στόχους σου πάνω σε αυτόν. Χωρίς αυτό το πρώτο βήμα, η κατηγορία θα παραμείνει $0 στο EBITDA μέχρι τον πρώτο μελλοντικό στόχο.',
    'Στις Παραδοχές προστέθηκαν οι «Εργοδοτικές εισφορές» (% επί των μισθών προσωπικού, όχι επί ενοικίων)',
    'Με το κουμπί «Εξαγωγή σε Excel» κατεβάζεις το P&L του επιλεγμένου ορίζοντα σε .xlsx.',
    'Το Ενοίκιο και το Medical/3rd-party δεν έχουν ξεχωριστή πρόβλεψη — είναι ήδη μέσα στη Μισθοδοσία και στο κόστος ανά μέλος αντίστοιχα, ώστε να μην μετρηθούν διπλά.',
  ]
  for (const line of must) assert.ok(banner.includes(line), `missing text: ${line.slice(0, 60)}…`)
  assert.ok(!banner.includes('για 4 κατηγορίες'), 'old 4-category wording must be gone')
  assert.ok(!banner.includes('Consultants/Audit/Legal,'), 'old combined category label must be gone')
})

test('dismiss is persisted server-side under one key (v2 after the step/9-category rewording) and the banner is hidden until the server answers', () => {
  assert.match(banner, /const NOTICE_KEY = 'polaris-opex-raises-intro-v2'/)
  assert.match(banner, /const visible = ref\(false\)/)
  assert.match(banner, /visible\.value = !\(await fetchNoticeDismissed\(NOTICE_KEY\)\)/)
  assert.match(banner, /await dismissNotice\(NOTICE_KEY\); visible\.value = false/)
  assert.match(notices, /api\.get\(`\/api\/notices\/\$\{encodeURIComponent\(key\)\}`\)/)
  assert.match(notices, /api\.post\(`\/api\/notices\/\$\{encodeURIComponent\(key\)\}\/dismiss`\)/)
  assert.ok(!banner.includes('localStorage'), 'dismissal must not rely on localStorage')
})

test('banner is mounted on Forecast and Payroll only for roles that can write there', () => {
  for (const [name, src] of [['Forecast', forecast], ['Payroll', payroll]]) {
    assert.match(src, /import PolarisIntroBanner from '@\/components\/PolarisIntroBanner\.vue'/, name)
    assert.match(src, /<PolarisIntroBanner v-if="pl\.canEdit" \/>/, name)
  }
})

test('OPEX baseline is $0 and the rule is a STEP on the client (matches the banner and the server)', () => {
  assert.match(store, /return stepMilestones\(0, \(y - 1\) \* 12, pts\)/)
  assert.ok(!/interpolateMilestones\(0, \(y - 1\) \* 12/.test(store), 'OPEX must not interpolate any more')
  assert.match(forecast, /κάθε κατηγορία ξεκινάει στα \$0 μέχρι τον πρώτο στόχο/)
  assert.match(forecast, /ΒΗΜΑΤΙΚΑ/)
  assert.match(forecast, /9 κατηγορίες/)
})

test('Forecast page: employer contributions assumption field and the Excel export button are wired', () => {
  assert.match(store, /employerContribPct: 'employer_contrib_pct'/)
  assert.match(store, /employerContribUsdInYear\(people\.value, calendarYear\(FORECAST_BASE_YEAR, y\)/)
  assert.match(store, /const ebitda = contribution - payroll - employerContrib - opex/)
  assert.match(store, /\(c\.payroll \+ c\.employerContrib \+ c\.opex\) \/ contribPerMember/)
  assert.match(forecast, /v-model="pl\.inputs\.employerContribPct"[^>]*@change="pl\.saveAssumption\('employerContribPct'\)"/)
  assert.match(forecast, /import \{ fetchForecastExcel \} from '@\/api\/polaris'/)
  assert.match(forecast, /fetchForecastExcel\(pl\.horizon\)/)
  assert.match(forecast, /Εξαγωγή σε Excel/)
  // the export button lives inside the admin-only horizon switch, never in the viewer/editor branch
  assert.match(forecast, /<div class="horizon-switch" v-if="pl\.isAdmin">[\s\S]*?@click="exportExcel"[\s\S]*?<\/div>/)
})

test('OPEX run-rate hint: shared Astrolavos figures are labelled as combined, 1:1 ones are not', () => {
  assert.match(forecast, /συνδυασμένο run-rate Αστρολάβος/)
  assert.match(forecast, /runRateHint\(cat\)/)
  // a new target for a combined category starts at 0 (never pre-fills the combined amount 3-4 times)
  assert.match(store, /opexRunRateIsCombined\(category\) \? 0 : Math\.round\(opexRunRates\.value\[category\] \|\| 0\)/)
})
