// patches/full-redesign-v6.cjs
// FULL REDESIGN: from v1 (HEAD) to final state in single atomic patch
// 1. Replace 8 KPI cards with 2 panel-cards + analyses
// 2. Replace old "Ταμειακά Διαθέσιμα" panel with 3-scenarios
// 3. All computed properties
// 4. CSS for .cash-analysis

const fs = require('fs');
const path = require('path');

const FILE = path.join(__dirname, '..', 'frontend', 'src', 'views', 'DashboardView.vue');

console.log('📄 Reading:', FILE);
const original = fs.readFileSync(FILE, 'utf8');
const originalSize = original.length;
console.log(`   Original: ${originalSize} chars`);

// =====================================================
// STEP 1: Add ALL computed properties after totalUnpaid
// =====================================================
const oldComp = `const totalUnpaid   = computed(() => Number(kpis.value.unpaidTotal) || 0)`;
const newComp = `const totalUnpaid   = computed(() => Number(kpis.value.unpaidTotal) || 0)

  // Session #43 — Paid-only cash flow
  const cashFlowNet = computed(() =>
    (Number(reconciliation.value.paidIncome) || 0) -
    (Number(reconciliation.value.paidExpense) || 0)
  )
  const forecastNet = computed(() => Number(kpis.value.netBalance) || 0)

  // Session #43 — Cash flow analysis
  const cashFlowAnalysis = computed(() => {
    const inc = Number(reconciliation.value.paidIncome) || 0
    const exp = Number(reconciliation.value.paidExpense) || 0
    const net = cashFlowNet.value
    if (net > 0) return \`Από τα \${fmt(inc)} που εισπράχθηκαν αφαιρέθηκαν \${fmt(exp)} σε πληρωμές. Έμεινε καθαρό υπόλοιπο \${fmt(net)} από τις πραγματικές κινήσεις. 💡 Διατηρήστε αυτή τη ροή για ομαλή λειτουργία.\`
    if (net < 0) return \`Πληρώθηκαν περισσότερα (\${fmt(exp)}) από όσα εισπράχθηκαν (\${fmt(inc)}). Προέκυψε έλλειμμα \${fmt(Math.abs(net))} από τις πραγματικές κινήσεις. ⚠️ Ελέγξτε τις δαπάνες ή επιταχύνετε τις εισπράξεις.\`
    return \`Εισπράξεις και πληρωμές ισοσκελίζονται σε \${fmt(inc)}. Καμία καθαρή κίνηση στην περίοδο. 💡 Παρακολουθήστε αν αυτή η σταθερότητα συνεχιστεί.\`
  })

  // Session #43 — Forecast analysis
  const forecastAnalysis = computed(() => {
    const unpaid = Number(kpis.value.unpaidTotal) || 0
    const urgent = Number(kpis.value.urgentTotal) || 0
    const net = forecastNet.value
    if (net > 0) return \`Αν εισπραχθούν τα αναμενόμενα και πληρωθούν οι \${fmt(unpaid)} σε υποχρεώσεις, θα προκύψει πλεόνασμα \${fmt(net)}. Η οικονομική θέση παραμένει υγιής. 💡 Καλή στιγμή για στρατηγικές επενδύσεις.\`
    if (net < 0) {
      const um = urgent > 0 ? \` με \${fmt(urgent)} επείγουσες\` : ''
      return \`Με τα τρέχοντα δεδομένα, αν πληρωθούν όλες οι \${fmt(unpaid)} υποχρεώσεις\${um}, θα προκύψει έλλειμμα \${fmt(Math.abs(net))}. Η περίοδος κλείνει αρνητικά. ⚠️ Απαιτείται είτε επιπλέον εισπράξεις είτε αναδιάταξη πληρωμών.\`
    }
    return \`Έσοδα και υποχρεώσεις ισοσκελίζονται ακριβώς. Δεν υπάρχει περιθώριο για απρόοπτα. 💡 Ασφαλέστερο να δημιουργηθεί αποθεματικό.\`
  })

  // Session #43 — Cash position scenarios
  const cashAfterUrgent = computed(() => bankTotal.value - (Number(kpis.value.urgentTotal) || 0))
  const cashAfterAll    = computed(() => bankTotal.value - (Number(kpis.value.unpaidTotal) || 0))
  const cashShortfall   = computed(() => {
    const a = cashAfterAll.value
    return a < 0 ? Math.abs(a) : 0
  })
  const cashScenarioAnalysis = computed(() => {
    const bank = bankTotal.value
    const unpaid = Number(kpis.value.unpaidTotal) || 0
    const sf = cashShortfall.value
    if (sf > 0) return \`Με \${fmt(bank)} στο ταμείο και \${fmt(unpaid)} σε συνολικές υποχρεώσεις, λείπουν \${fmt(sf)} για να καλυφθούν όλα. ⚠️ Απαιτείται είσπραξη ή αναδιάταξη πληρωμών.\`
    if (unpaid > 0) {
      const surplus = bank - unpaid
      return \`Με \${fmt(bank)} στο ταμείο μπορούν να καλυφθούν οι \${fmt(unpaid)} σε υποχρεώσεις και να μείνουν \${fmt(surplus)} αποθεματικό. 💡 Η ταμειακή θέση είναι υγιής.\`
    }
    return \`Δεν υπάρχουν εκκρεμείς υποχρεώσεις. Διαθέσιμο ταμείο \${fmt(bank)}. 💡 Καλή στιγμή για στρατηγικό σχεδιασμό.\`
  })`;

if (!original.includes(oldComp)) {
  console.error('❌ totalUnpaid computed not found.');
  process.exit(1);
}
let patched = original.replace(oldComp, newComp);
console.log('✅ Step 1: All computed properties added (8 new)');

// =====================================================
// STEP 2: Replace old "Ταμειακά Διαθέσιμα" panel with 3-scenarios
// =====================================================
const oldTamPanel = `<!-- Ταμειακά Διαθέσιμα -->
        <div class="panel-card">
          <div class="panel-hdr">
            <span class="ptitle succ"><i class="fas fa-coins"></i> Ταμειακά Διαθέσιμα</span>
          </div>
          <div class="cash-hero">
            <div class="cash-tot" :style="{color: cashAvailable>=0?'var(--success)':'#ff6400'}">{{ fmt(cashAvailable) }}</div>
            <div class="cash-lbl">Τράπεζες μείον Εκκρεμείς</div>
          </div>
          <div class="cash-bk">
            <div class="cash-row">
              <div class="cr-ico" style="background:var(--accent-glow);color:var(--accent)"><i class="fas fa-university"></i></div>
              <span class="cr-lbl">Τράπεζες</span>
              <span class="cr-val">{{ fmt(bankTotal) }}</span>
            </div>
            <div class="cash-row urg" v-if="Number(kpis.urgentTotal)>0">
              <div class="cr-ico" style="background:rgba(255,100,0,.15);color:#ff6400"><i class="fas fa-bolt"></i></div>
              <span class="cr-lbl" style="color:#ff6400;font-weight:700">⚡ Εκκρεμείς</span>
              <span class="cr-val" style="color:#ff6400;font-weight:800">-{{ fmt(kpis.urgentTotal) }}</span>
            </div>
            <div class="cash-row">
              <div class="cr-ico" style="background:var(--warning-bg);color:var(--warning)"><i class="fas fa-clock"></i></div>
              <span class="cr-lbl">Σύνολο Υποχρεώσεων</span>
              <span class="cr-val" style="color:var(--danger)">-{{ fmt(totalUnpaid) }}</span>
            </div>
            <div style="height:1px;background:var(--border);margin:4px 0"></div>
            <div class="cash-row avail" :style="{background: cashAvailable>=0?'rgba(16,185,129,.08)':'rgba(239,68,68,.08)'}">
              <div class="cr-ico" :style="{background:cashAvailable>=0?'var(--success-bg)':'var(--danger-bg)',color:cashAvailable>=0?'var(--success)':'var(--danger)'}"><i class="fas fa-coins"></i></div>
              <span class="cr-lbl" style="font-weight:700">Καθαρά Διαθέσιμα</span>
              <span class="cr-val" :style="{color:cashAvailable>=0?'var(--success)':'var(--danger)',fontWeight:'800',fontSize:'1.05rem'}">{{ fmt(cashAvailable) }}</span>
            </div>
          </div>
        </div>`;

const newTamPanel = `<!-- Ταμειακά Διαθέσιμα — 3 Σενάρια (Session #43) -->
        <div class="panel-card">
          <div class="panel-hdr">
            <span class="ptitle succ"><i class="fas fa-coins"></i> Ταμειακά Διαθέσιμα</span>
          </div>
          <div class="cash-hero">
            <div class="cash-tot" :style="{color: bankTotal>=0?'var(--success)':'var(--danger)'}">{{ fmt(bankTotal) }}</div>
            <div class="cash-lbl">Ταμείο σήμερα</div>
          </div>
          <div class="cash-bk">
            <div class="cash-row">
              <div class="cr-ico" style="background:var(--accent-glow);color:var(--accent)"><i class="fas fa-university"></i></div>
              <span class="cr-lbl">1️⃣ Σήμερα (ταμείο)</span>
              <span class="cr-val">{{ fmt(bankTotal) }}</span>
            </div>
            <div class="cash-row" v-if="Number(kpis.urgentTotal)>0">
              <div class="cr-ico" style="background:rgba(255,100,0,.15);color:#ff6400"><i class="fas fa-bolt"></i></div>
              <span class="cr-lbl">2️⃣ Μετά τις επείγουσες</span>
              <span class="cr-val" :style="{color:cashAfterUrgent>=0?'var(--success)':'var(--danger)'}">{{ fmt(cashAfterUrgent) }}</span>
            </div>
            <div class="cash-row">
              <div class="cr-ico" style="background:var(--warning-bg);color:var(--warning)"><i class="fas fa-clock"></i></div>
              <span class="cr-lbl">3️⃣ Μετά από όλα</span>
              <span class="cr-val" :style="{color:cashAfterAll>=0?'var(--success)':'var(--danger)',fontWeight:'700'}">{{ fmt(cashAfterAll) }}</span>
            </div>
            <div style="height:1px;background:var(--border);margin:4px 0"></div>
            <div class="cash-row avail" v-if="cashShortfall>0" style="background:rgba(239,68,68,.08)">
              <div class="cr-ico" style="background:var(--danger-bg);color:var(--danger)"><i class="fas fa-hand-holding-usd"></i></div>
              <span class="cr-lbl" style="font-weight:700;color:var(--danger)">💸 Λείπουν</span>
              <span class="cr-val" style="color:var(--danger);font-weight:800;font-size:1.05rem">{{ fmt(cashShortfall) }}</span>
            </div>
            <div class="cash-row avail" v-else style="background:rgba(16,185,129,.08)">
              <div class="cr-ico" style="background:var(--success-bg);color:var(--success)"><i class="fas fa-check"></i></div>
              <span class="cr-lbl" style="font-weight:700;color:var(--success)">✅ Επαρκή</span>
              <span class="cr-val" style="color:var(--success);font-weight:800;font-size:1.05rem">{{ fmt(cashAfterAll) }}</span>
            </div>
          </div>
          <div class="cash-analysis">{{ cashScenarioAnalysis }}</div>
        </div>`;

if (!patched.includes(oldTamPanel)) {
  console.error('❌ Old Ταμειακά Διαθέσιμα panel not found.');
  process.exit(1);
}
patched = patched.replace(oldTamPanel, newTamPanel);
console.log('✅ Step 2: Ταμειακά Διαθέσιμα panel replaced (3 scenarios)');

// =====================================================
// STEP 3: Replace 8 KPI cards with 2 panel-cards + analyses
// =====================================================
const oldKpiBlockRegex = /(\s*)<!-- ═══ KPI CARDS[^>]*-->\s*\n\s*<div class="kpi-grid">[\s\S]*?\n\s*<\/div>\s*\n(\s*<!-- ═══ CHARTS)/;

if (!oldKpiBlockRegex.test(patched)) {
  console.error('❌ KPI grid block not found.');
  process.exit(1);
}

const newKpiBlock = `$1<!-- ═══ KPI PANELS — Hybrid (Session #43) ════════════════════════════ -->
      <div class="dash-2equal mb">

        <!-- Panel 1: Ταμειακή Κίνηση (paid-only) -->
        <div class="panel-card">
          <div class="panel-hdr">
            <span class="ptitle succ"><i class="fas fa-coins"></i> Ταμειακή Κίνηση Περιόδου</span>
          </div>
          <div class="cash-hero">
            <div class="cash-tot" :style="{color: cashFlowNet>=0?'var(--success)':'#ff6400'}">{{ fmt(cashFlowNet) }}</div>
            <div class="cash-lbl">Καθαρή Ροή · πραγματικά πληρωμένα</div>
          </div>
          <div class="cash-analysis">{{ cashFlowAnalysis }}</div>
          <div class="cash-bk">
            <div class="cash-row">
              <div class="cr-ico" style="background:var(--success-bg);color:var(--success)"><i class="fas fa-arrow-down"></i></div>
              <span class="cr-lbl">Εισπράχθηκαν</span>
              <span class="cr-val" style="color:var(--success)">{{ fmt(reconciliation.paidIncome) }}</span>
            </div>
            <div class="cash-row">
              <div class="cr-ico" style="background:var(--danger-bg);color:var(--danger)"><i class="fas fa-arrow-up"></i></div>
              <span class="cr-lbl">Πληρώθηκαν</span>
              <span class="cr-val" style="color:var(--danger)">-{{ fmt(reconciliation.paidExpense) }}</span>
            </div>
            <div style="height:1px;background:var(--border);margin:4px 0"></div>
            <div class="cash-row avail" :style="{background: cashFlowNet>=0?'rgba(16,185,129,.08)':'rgba(239,68,68,.08)'}">
              <div class="cr-ico" :style="{background:cashFlowNet>=0?'var(--success-bg)':'var(--danger-bg)',color:cashFlowNet>=0?'var(--success)':'var(--danger)'}"><i class="fas fa-balance-scale"></i></div>
              <span class="cr-lbl" style="font-weight:700">Καθαρή Ροή</span>
              <span class="cr-val" :style="{color:cashFlowNet>=0?'var(--success)':'var(--danger)',fontWeight:'800',fontSize:'1.05rem'}">{{ fmt(cashFlowNet) }}</span>
            </div>
          </div>
        </div>

        <!-- Panel 2: Πρόβλεψη -->
        <div class="panel-card">
          <div class="panel-hdr">
            <span class="ptitle warn"><i class="fas fa-clock"></i> Πρόβλεψη & Υποχρεώσεις</span>
          </div>
          <div class="cash-hero">
            <div class="cash-tot" :style="{color: forecastNet>=0?'var(--success)':'var(--danger)'}">{{ fmt(forecastNet) }}</div>
            <div class="cash-lbl">Καθαρή Πρόβλεψη · αν όλα κλείσουν</div>
          </div>
          <div class="cash-analysis">{{ forecastAnalysis }}</div>
          <div class="cash-bk">
            <div class="cash-row" v-if="Number(kpis.urgentTotal)>0">
              <div class="cr-ico" style="background:rgba(255,100,0,.15);color:#ff6400"><i class="fas fa-bolt"></i></div>
              <span class="cr-lbl" style="color:#ff6400;font-weight:700">⚡ Επείγουσες</span>
              <span class="cr-val" style="color:#ff6400;font-weight:800">-{{ fmt(kpis.urgentTotal) }}</span>
            </div>
            <div class="cash-row">
              <div class="cr-ico" style="background:var(--warning-bg);color:var(--warning)"><i class="fas fa-clock"></i></div>
              <span class="cr-lbl">Σύνολο Υποχρεώσεων</span>
              <span class="cr-val" style="color:var(--danger)">-{{ fmt(kpis.unpaidTotal) }}</span>
            </div>
            <div style="height:1px;background:var(--border);margin:4px 0"></div>
            <div class="cash-row avail" :style="{background: forecastNet>=0?'rgba(16,185,129,.08)':'rgba(239,68,68,.08)'}">
              <div class="cr-ico" :style="{background:forecastNet>=0?'var(--success-bg)':'var(--danger-bg)',color:forecastNet>=0?'var(--success)':'var(--danger)'}"><i class="fas fa-chart-line"></i></div>
              <span class="cr-lbl" style="font-weight:700">Καθαρή Πρόβλεψη</span>
              <span class="cr-val" :style="{color:forecastNet>=0?'var(--success)':'var(--danger)',fontWeight:'800',fontSize:'1.05rem'}">{{ fmt(forecastNet) }}</span>
            </div>
          </div>
        </div>

      </div>

$2`;

patched = patched.replace(oldKpiBlockRegex, newKpiBlock);
console.log('✅ Step 3: KPI cards → 2 panel-cards + analyses');

// =====================================================
// STEP 4: Add CSS for .cash-analysis
// =====================================================
const oldCss = `/* Reconciliation */`;
const newCss = `/* Cash Analysis (Session #43) */
.cash-analysis {
  margin: 4px 16px 12px;
  padding: 10px 12px;
  background: rgba(99, 102, 241, 0.06);
  border-left: 3px solid var(--accent);
  border-radius: 6px;
  font-size: 0.82rem;
  line-height: 1.5;
  color: var(--text-secondary);
  font-style: italic;
}
/* Reconciliation */`;

if (!patched.includes(oldCss)) {
  console.error('❌ CSS anchor not found.');
  process.exit(1);
}
patched = patched.replace(oldCss, newCss);
console.log('✅ Step 4: CSS added');

// =====================================================
// Sanity checks
// =====================================================
const newSize = patched.length;
console.log(`\n📊 Size: ${originalSize} → ${newSize} (+${newSize - originalSize})`);

const checks = {
  'cashFlowNet': /const cashFlowNet = computed/.test(patched),
  'forecastNet': /const forecastNet = computed/.test(patched),
  'cashFlowAnalysis': /const cashFlowAnalysis = computed/.test(patched),
  'forecastAnalysis': /const forecastAnalysis = computed/.test(patched),
  'cashAfterUrgent': /const cashAfterUrgent\s+= computed/.test(patched),
  'cashAfterAll': /const cashAfterAll\s+= computed/.test(patched),
  'cashShortfall': /const cashShortfall\s+= computed/.test(patched),
  'cashScenarioAnalysis': /const cashScenarioAnalysis = computed/.test(patched),
  'Ταμείο σήμερα': /Ταμείο σήμερα/.test(patched),
  '1️⃣ Σήμερα': /1️⃣ Σήμερα/.test(patched),
  '💸 Λείπουν': /💸 Λείπουν/.test(patched),
  'Ταμειακή Κίνηση Περιόδου': /Ταμειακή Κίνηση Περιόδου/.test(patched),
  'Πρόβλεψη & Υποχρεώσεις': /Πρόβλεψη & Υποχρεώσεις/.test(patched),
  'No old kpi-grid': !/<div class="kpi-grid"/.test(patched),
  'No old "Καθαρά Διαθέσιμα"': !/Καθαρά Διαθέσιμα/.test(patched),
  '3 cash-analysis': (patched.match(/<div class="cash-analysis">/g) || []).length === 3,
  'CSS rule': /\.cash-analysis \{/.test(patched),
  '6 panel-cards': (patched.match(/<div class="panel-card">/g) || []).length === 6,
};

console.log('\n🔍 Checks:');
let pass = true;
for (const [k, v] of Object.entries(checks)) {
  console.log(`   ${v ? '✅' : '❌'} ${k}`);
  if (!v) pass = false;
}

if (!pass) {
  console.error('\n❌ FAIL — not writing.');
  process.exit(1);
}

fs.writeFileSync(FILE, patched, 'utf8');
console.log(`\n✅ Written ${newSize} chars`);
console.log('🎉 v6 complete.');