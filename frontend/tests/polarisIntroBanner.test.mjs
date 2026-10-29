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
    'Στις Προβλέψεις, προστέθηκε η ενότητα «Πρόβλεψη Λειτουργικών Εξόδων» για 4 κατηγορίες: Consultants/Audit/Legal, IT/Computer, Marketing/Διαφήμιση, Ταξίδια/PR/λοιπά/σύνδεσμοι.',
    'κάθε κατηγορία ξεκινάει στα $0 μέχρι να προσθέσεις τον πρώτο στόχο. Αν θες να αντανακλάται και το ΣΗΜΕΡΙΝΟ πραγματικό κόστος (όχι μόνο το μέλλον), πρόσθεσε πρώτα έναν στόχο κοντά στο σήμερα με το τρέχον ετήσιο run-rate της κατηγορίας, και μετά τους μελλοντικούς στόχους σου πάνω σε αυτόν. Χωρίς αυτό το πρώτο βήμα, η κατηγορία θα παραμείνει $0 στο EBITDA μέχρι τον πρώτο μελλοντικό στόχο.',
    'Το Ενοίκιο και το Medical/3rd-party δεν έχουν ξεχωριστή πρόβλεψη — είναι ήδη μέσα στη Μισθοδοσία και στο κόστος ανά μέλος αντίστοιχα, ώστε να μην μετρηθούν διπλά.',
  ]
  for (const line of must) assert.ok(banner.includes(line), `missing text: ${line.slice(0, 60)}…`)
})

test('dismiss is persisted server-side under one key and the banner is hidden until the server answers', () => {
  assert.match(banner, /const NOTICE_KEY = 'polaris-opex-raises-intro'/)
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

test('OPEX baseline is $0 on the client (matches the banner and the server)', () => {
  assert.match(store, /return interpolateMilestones\(0, \(y - 1\) \* 12, pts\)/)
  assert.match(forecast, /κάθε κατηγορία ξεκινάει στα \$0 μέχρι τον πρώτο στόχο/)
})
