// patches/kpi-strip-v3-analysis.cjs
// V3: Adds human-readable analysis under each panel hero
//     2 sentences explaining the number + 1 actionable insight

const fs = require('fs');
const path = require('path');

const FILE = path.join(__dirname, '..', 'frontend', 'src', 'views', 'DashboardView.vue');

console.log('📄 Reading:', FILE);
const original = fs.readFileSync(FILE, 'utf8');
const originalSize = original.length;
console.log(`   Original size: ${originalSize} bytes`);

// ─────────────────────────────────────────────────────
// PATCH 1: Add cashFlowAnalysis + forecastAnalysis computed
// ─────────────────────────────────────────────────────
const oldComputed = `const forecastNet = computed(() => Number(kpis.value.netBalance) || 0)`;
const newComputed = `const forecastNet = computed(() => Number(kpis.value.netBalance) || 0)

  // Session #43 — Cash flow analysis (paid-only insight)
  const cashFlowAnalysis = computed(() => {
    const inc = Number(reconciliation.value.paidIncome) || 0
    const exp = Number(reconciliation.value.paidExpense) || 0
    const net = cashFlowNet.value
    if (net > 0) {
      return \`Από τα \${fmt(inc)} που εισπράχθηκαν αφαιρέθηκαν \${fmt(exp)} σε πληρωμές. Έμεινε καθαρό υπόλοιπο \${fmt(net)} από τις πραγματικές κινήσεις. 💡 Διατηρήστε αυτή τη ροή για ομαλή λειτουργία.\`
    }
    if (net < 0) {
      return \`Πληρώθηκαν περισσότερα (\${fmt(exp)}) από όσα εισπράχθηκαν (\${fmt(inc)}). Προέκυψε έλλειμμα \${fmt(Math.abs(net))} από τις πραγματικές κινήσεις. ⚠️ Ελέγξτε τις δαπάνες ή επιταχύνετε τις εισπράξεις.\`
    }
    return \`Εισπράξεις και πληρωμές ισοσκελίζονται σε \${fmt(inc)}. Καμία καθαρή κίνηση στην περίοδο. 💡 Παρακολουθήστε αν αυτή η σταθερότητα συνεχιστεί.\`
  })

  // Session #43 — Forecast analysis (commitments insight)
  const forecastAnalysis = computed(() => {
    const unpaid = Number(kpis.value.unpaidTotal) || 0
    const urgent = Number(kpis.value.urgentTotal) || 0
    const net = forecastNet.value
    if (net > 0) {
      return \`Αν εισπραχθούν τα αναμενόμενα και πληρωθούν οι \${fmt(unpaid)} σε υποχρεώσεις, θα προκύψει πλεόνασμα \${fmt(net)}. Η οικονομική θέση παραμένει υγιής. 💡 Καλή στιγμή για στρατηγικές επενδύσεις.\`
    }
    if (net < 0) {
      const urgentMsg = urgent > 0 ? \` με \${fmt(urgent)} επείγουσες\` : ''
      return \`Με τα τρέχοντα δεδομένα, αν πληρωθούν όλες οι \${fmt(unpaid)} υποχρεώσεις\${urgentMsg}, θα προκύψει έλλειμμα \${fmt(Math.abs(net))}. Η περίοδος κλείνει αρνητικά. ⚠️ Απαιτείται είτε επιπλέον εισπράξεις είτε αναδιάταξη πληρωμών.\`
    }
    return \`Έσοδα και υποχρεώσεις ισοσκελίζονται ακριβώς. Δεν υπάρχει περιθώριο για απρόοπτα. 💡 Ασφαλέστερο να δημιουργηθεί αποθεματικό.\`
  })`;

if (!original.includes(oldComputed)) {
  console.error('❌ forecastNet computed not found. Run patch v2 first.');
  process.exit(1);
}
let patched = original.replace(oldComputed, newComputed);
console.log('✅ Patch 1: cashFlowAnalysis + forecastAnalysis computed added');

// ─────────────────────────────────────────────────────
// PATCH 2: Add analysis text under Panel 1 hero
// ─────────────────────────────────────────────────────
const oldPanel1Hero = `<div class="cash-lbl">Καθαρή Ροή · πραγματικά πληρωμένα</div>
          </div>
          <div class="cash-bk">
            <div class="cash-row">
              <div class="cr-ico" style="background:var(--success-bg);color:var(--success)"><i class="fas fa-arrow-down"></i></div>
              <span class="cr-lbl">Εισπράχθηκαν</span>`;

const newPanel1Hero = `<div class="cash-lbl">Καθαρή Ροή · πραγματικά πληρωμένα</div>
          </div>
          <div class="cash-analysis">{{ cashFlowAnalysis }}</div>
          <div class="cash-bk">
            <div class="cash-row">
              <div class="cr-ico" style="background:var(--success-bg);color:var(--success)"><i class="fas fa-arrow-down"></i></div>
              <span class="cr-lbl">Εισπράχθηκαν</span>`;

const p1Matches = (patched.match(new RegExp(oldPanel1Hero.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g')) || []).length;
if (p1Matches !== 1) {
  console.error(`❌ Panel 1 anchor: expected 1 match, found ${p1Matches}. Aborting.`);
  process.exit(1);
}
patched = patched.replace(oldPanel1Hero, newPanel1Hero);
console.log('✅ Patch 2: Analysis text added to Panel 1');

// ─────────────────────────────────────────────────────
// PATCH 3: Add analysis text under Panel 2 hero
// ─────────────────────────────────────────────────────
const oldPanel2Hero = `<div class="cash-lbl">Καθαρή Πρόβλεψη · αν όλα κλείσουν</div>
          </div>
          <div class="cash-bk">
            <div class="cash-row" v-if="Number(kpis.urgentTotal)>0">`;

const newPanel2Hero = `<div class="cash-lbl">Καθαρή Πρόβλεψη · αν όλα κλείσουν</div>
          </div>
          <div class="cash-analysis">{{ forecastAnalysis }}</div>
          <div class="cash-bk">
            <div class="cash-row" v-if="Number(kpis.urgentTotal)>0">`;

const p2Matches = (patched.match(new RegExp(oldPanel2Hero.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g')) || []).length;
if (p2Matches !== 1) {
  console.error(`❌ Panel 2 anchor: expected 1 match, found ${p2Matches}. Aborting.`);
  process.exit(1);
}
patched = patched.replace(oldPanel2Hero, newPanel2Hero);
console.log('✅ Patch 3: Analysis text added to Panel 2');

// ─────────────────────────────────────────────────────
// PATCH 4: Add CSS for .cash-analysis
// ─────────────────────────────────────────────────────
// Find an existing CSS rule to anchor near. We'll add after .cash-lbl rule.
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
  console.error('❌ CSS anchor /* Reconciliation */ not found. Aborting.');
  process.exit(1);
}
patched = patched.replace(oldCss, newCss);
console.log('✅ Patch 4: CSS .cash-analysis added');

// ─────────────────────────────────────────────────────
// Sanity checks
// ─────────────────────────────────────────────────────
const newSize = patched.length;
const sizeDiff = newSize - originalSize;
console.log(`\n📊 Size: ${originalSize} → ${newSize} bytes (${sizeDiff > 0 ? '+' : ''}${sizeDiff})`);

const checks = {
  'cashFlowAnalysis computed': /const cashFlowAnalysis = computed/.test(patched),
  'forecastAnalysis computed': /const forecastAnalysis = computed/.test(patched),
  'Panel 1 cash-analysis div': /\{\{ cashFlowAnalysis \}\}/.test(patched),
  'Panel 2 cash-analysis div': /\{\{ forecastAnalysis \}\}/.test(patched),
  'CSS .cash-analysis rule': /\.cash-analysis \{/.test(patched),
  'Greek tip emoji 💡': /💡/.test(patched),
  'Greek warning emoji ⚠️': /⚠️/.test(patched),
  'Two cash-analysis instances in template': (patched.match(/<div class="cash-analysis">/g) || []).length === 2,
};

console.log('\n🔍 Sanity checks:');
let allPassed = true;
for (const [name, passed] of Object.entries(checks)) {
  console.log(`   ${passed ? '✅' : '❌'} ${name}`);
  if (!passed) allPassed = false;
}

if (!allPassed) {
  console.error('\n❌ Sanity checks failed. NOT writing file.');
  process.exit(1);
}

fs.writeFileSync(FILE, patched, 'utf8');
console.log(`\n✅ Written ${newSize} bytes to ${FILE}`);
console.log('\n🎉 Patch v3 complete.');