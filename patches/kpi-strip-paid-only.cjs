// patches/kpi-strip-paid-only.cjs
// Replaces 8 mixed P&L/duplicate KPI cards with 6 clean Hybrid cards:
//   Row 1 (paid-only):  Εισπράχθηκαν / Πληρώθηκαν / Καθαρή Ροή
//   Row 2 (forecast):   Επείγουσες / Υποχρεώσεις / Καθαρή Πρόβλεψη

const fs = require('fs');
const path = require('path');

const FILE = path.join(__dirname, '..', 'frontend', 'src', 'views', 'DashboardView.vue');

console.log('📄 Reading:', FILE);
const original = fs.readFileSync(FILE, 'utf8');
const originalSize = original.length;
console.log(`   Original size: ${originalSize} bytes`);

// ─────────────────────────────────────────────────────
// PATCH 1: Add cashFlowNet computed (after line 160)
// ─────────────────────────────────────────────────────
const oldComputed = `const totalUnpaid   = computed(() => Number(kpis.value.unpaidTotal) || 0)`;
const newComputed = `const totalUnpaid   = computed(() => Number(kpis.value.unpaidTotal) || 0)
  // Session #43 — paid-only cash flow (used by KPI strip Row 1)
  const cashFlowNet = computed(() =>
    (Number(reconciliation.value.paidIncome) || 0) -
    (Number(reconciliation.value.paidExpense) || 0)
  )`;

const computedMatches = (original.match(new RegExp(oldComputed.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g')) || []).length;
if (computedMatches !== 1) {
  console.error(`❌ Expected 1 match for totalUnpaid computed, found ${computedMatches}. Aborting.`);
  process.exit(1);
}
let patched = original.replace(oldComputed, newComputed);
console.log('✅ Patch 1: cashFlowNet computed added');

// ─────────────────────────────────────────────────────
// PATCH 2: Replace entire KPI grid block (lines 530-587)
// Old: 8 cards with duplicates and mixed P&L/cash semantics
// New: 6 cards in 2 logical sections
// ─────────────────────────────────────────────────────

// Anchor: <!-- ═══ KPI CARDS ... --> ... </div> (closing kpi-grid)
// We match from "      <!-- ═══ KPI CARDS" to the closing of kpi-grid
const oldKpiBlockRegex = /(\s*)<!-- ═══ KPI CARDS[^>]*-->\s*\n\s*<div class="kpi-grid">[\s\S]*?\n\s*<\/div>\s*\n(\s*<!-- ═══ CHARTS)/;

if (!oldKpiBlockRegex.test(patched)) {
  console.error('❌ KPI grid block not found with expected anchors. Aborting.');
  process.exit(1);
}

const newKpiBlock = `$1<!-- ═══ KPI STRIP — Hybrid (Session #43, paid + forecast) ═══════════════ -->
      <!-- Row 1: Ταμειακή Κίνηση Περιόδου (paid-only) -->
      <div class="kpi-section-hdr">
        <i class="fas fa-coins"></i> Ταμειακή Κίνηση Περιόδου
        <span class="kpi-section-sub">πραγματικά πληρωμένα</span>
      </div>
      <div class="kpi-grid kpi-grid-3">
        <div class="kpi-card">
          <div class="kpi-hdr"><div class="kpi-ico" style="background:var(--success-bg);color:var(--success)"><i class="fas fa-arrow-down"></i></div></div>
          <div class="kpi-lbl">Εισπράχθηκαν</div>
          <div class="kpi-val" style="color:var(--success)">{{ fmt(reconciliation.paidIncome) }}</div>
          <div class="kpi-sub">paid · received</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-hdr"><div class="kpi-ico" style="background:var(--danger-bg);color:var(--danger)"><i class="fas fa-arrow-up"></i></div></div>
          <div class="kpi-lbl">Πληρώθηκαν</div>
          <div class="kpi-val" style="color:var(--danger)">{{ fmt(reconciliation.paidExpense) }}</div>
          <div class="kpi-sub">paid</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-hdr">
            <div class="kpi-ico" :style="{background:cashFlowNet>=0?'var(--success-bg)':'var(--danger-bg)',color:cashFlowNet>=0?'var(--success)':'var(--danger)'}"><i class="fas fa-balance-scale"></i></div>
            <span class="kpi-chg down" v-if="cashFlowNet<0">Αρνητικό</span>
          </div>
          <div class="kpi-lbl">Καθαρή Ροή</div>
          <div class="kpi-val" :style="{color:cashFlowNet>=0?'var(--success)':'var(--danger)'}">{{ fmt(cashFlowNet) }}</div>
          <div class="kpi-sub">Εισπράχθηκαν − Πληρώθηκαν</div>
        </div>
      </div>

      <!-- Row 2: Πρόβλεψη & Υποχρεώσεις -->
      <div class="kpi-section-hdr">
        <i class="fas fa-clock"></i> Πρόβλεψη & Υποχρεώσεις
        <span class="kpi-section-sub">τι αναμένουμε</span>
      </div>
      <div class="kpi-grid kpi-grid-3">
        <div class="kpi-card">
          <div class="kpi-hdr"><div class="kpi-ico" style="background:rgba(255,100,0,.12);color:#ff6400"><i class="fas fa-bolt"></i></div></div>
          <div class="kpi-lbl">⚡ Επείγουσες</div>
          <div class="kpi-val" style="color:#ff6400">{{ fmt(kpis.urgentTotal) }}</div>
          <div class="kpi-sub">urgent unpaid</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-hdr"><div class="kpi-ico" style="background:var(--warning-bg);color:var(--warning)"><i class="fas fa-clock"></i></div></div>
          <div class="kpi-lbl">Σύνολο Υποχρεώσεων</div>
          <div class="kpi-val" style="color:var(--warning)">{{ fmt(kpis.unpaidTotal) }}</div>
          <div class="kpi-sub">unpaid + urgent</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-hdr">
            <div class="kpi-ico" :style="{background:Number(kpis.netBalance)>=0?'var(--success-bg)':'var(--danger-bg)',color:Number(kpis.netBalance)>=0?'var(--success)':'var(--danger)'}"><i class="fas fa-crystal-ball"></i></div>
            <span class="kpi-chg down" v-if="Number(kpis.netBalance)<0">Αρνητικό</span>
          </div>
          <div class="kpi-lbl">Καθαρή Πρόβλεψη</div>
          <div class="kpi-val" :style="{color:Number(kpis.netBalance)>=0?'var(--success)':'var(--danger)'}">{{ fmt(kpis.netBalance) }}</div>
          <div class="kpi-sub">αν όλα κλείσουν</div>
        </div>
      </div>

$2`;

patched = patched.replace(oldKpiBlockRegex, newKpiBlock);
console.log('✅ Patch 2: KPI grid block replaced (8 cards → 6 cards in 2 sections)');

// ─────────────────────────────────────────────────────
// Sanity checks
// ─────────────────────────────────────────────────────
const newSize = patched.length;
const sizeDiff = newSize - originalSize;
console.log(`\n📊 Size: ${originalSize} → ${newSize} bytes (${sizeDiff > 0 ? '+' : ''}${sizeDiff})`);

const checks = {
  'cashFlowNet computed': /const cashFlowNet = computed/.test(patched),
  'Εισπράχθηκαν card': /kpi-lbl">Εισπράχθηκαν</.test(patched),
  'Πληρώθηκαν card': /kpi-lbl">Πληρώθηκαν</.test(patched),
  'Καθαρή Ροή card': /kpi-lbl">Καθαρή Ροή</.test(patched),
  'Επείγουσες card': /kpi-lbl">⚡ Επείγουσες</.test(patched),
  'Σύνολο Υποχρεώσεων card': /kpi-lbl">Σύνολο Υποχρεώσεων</.test(patched),
  'Καθαρή Πρόβλεψη card': /kpi-lbl">Καθαρή Πρόβλεψη</.test(patched),
  'No old "Τρέχον Υπόλοιπο"': !/kpi-lbl">Τρέχον Υπόλοιπο</.test(patched),
  'No old "Καθαρό" (alone)': !/kpi-lbl">Καθαρό<\/div>\s*\n\s*<div class="kpi-val"/.test(patched),
  'kpi-grid count = 2': (patched.match(/<div class="kpi-grid/g) || []).length === 2,
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

// ─────────────────────────────────────────────────────
// Write
// ─────────────────────────────────────────────────────
fs.writeFileSync(FILE, patched, 'utf8');
console.log(`\n✅ Written ${newSize} bytes to ${FILE}`);
console.log('\n🎉 Patch complete. Run: cd frontend && npm run build to verify.');