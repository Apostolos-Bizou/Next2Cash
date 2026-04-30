// patches/kpi-strip-paid-only-v2.cjs
// V2: Replaces 8 KPI cards with 2 panel-cards in dash-2equal layout
//     Same visual pattern as "Ταμειακά Διαθέσιμα" — hero + rows
//
// Panel 1 (paid-only):     Ταμειακή Κίνηση Περιόδου
// Panel 2 (forecast):      Πρόβλεψη & Υποχρεώσεις

const fs = require('fs');
const path = require('path');

const FILE = path.join(__dirname, '..', 'frontend', 'src', 'views', 'DashboardView.vue');

console.log('📄 Reading:', FILE);
const original = fs.readFileSync(FILE, 'utf8');
const originalSize = original.length;
console.log(`   Original size: ${originalSize} bytes`);

// ─────────────────────────────────────────────────────
// PATCH 1: Add cashFlowNet computed
// ─────────────────────────────────────────────────────
const oldComputed = `const totalUnpaid   = computed(() => Number(kpis.value.unpaidTotal) || 0)`;
const newComputed = `const totalUnpaid   = computed(() => Number(kpis.value.unpaidTotal) || 0)
  // Session #43 — paid-only cash flow (used by KPI panel 1)
  const cashFlowNet = computed(() =>
    (Number(reconciliation.value.paidIncome) || 0) -
    (Number(reconciliation.value.paidExpense) || 0)
  )
  // Session #43 — forecast net (rename of old netBalance for clarity)
  const forecastNet = computed(() => Number(kpis.value.netBalance) || 0)`;

const computedMatches = (original.match(new RegExp(oldComputed.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g')) || []).length;
if (computedMatches !== 1) {
  console.error(`❌ Expected 1 match for totalUnpaid computed, found ${computedMatches}. Aborting.`);
  process.exit(1);
}
let patched = original.replace(oldComputed, newComputed);
console.log('✅ Patch 1: cashFlowNet + forecastNet computed added');

// ─────────────────────────────────────────────────────
// PATCH 2: Replace KPI grid block with 2 panel-cards
// ─────────────────────────────────────────────────────
const oldKpiBlockRegex = /(\s*)<!-- ═══ KPI CARDS[^>]*-->\s*\n\s*<div class="kpi-grid">[\s\S]*?\n\s*<\/div>\s*\n(\s*<!-- ═══ CHARTS)/;

if (!oldKpiBlockRegex.test(patched)) {
  console.error('❌ KPI grid block not found. Aborting.');
  process.exit(1);
}

const newKpiBlock = `$1<!-- ═══ KPI PANELS — Hybrid (Session #43) ════════════════════════════ -->
      <div class="dash-2equal mb">

        <!-- Panel 1: Ταμειακή Κίνηση Περιόδου (paid-only) -->
        <div class="panel-card">
          <div class="panel-hdr">
            <span class="ptitle succ"><i class="fas fa-coins"></i> Ταμειακή Κίνηση Περιόδου</span>
          </div>
          <div class="cash-hero">
            <div class="cash-tot" :style="{color: cashFlowNet>=0?'var(--success)':'#ff6400'}">{{ fmt(cashFlowNet) }}</div>
            <div class="cash-lbl">Καθαρή Ροή · πραγματικά πληρωμένα</div>
          </div>
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

        <!-- Panel 2: Πρόβλεψη & Υποχρεώσεις -->
        <div class="panel-card">
          <div class="panel-hdr">
            <span class="ptitle warn"><i class="fas fa-clock"></i> Πρόβλεψη & Υποχρεώσεις</span>
          </div>
          <div class="cash-hero">
            <div class="cash-tot" :style="{color: forecastNet>=0?'var(--success)':'var(--danger)'}">{{ fmt(forecastNet) }}</div>
            <div class="cash-lbl">Καθαρή Πρόβλεψη · αν όλα κλείσουν</div>
          </div>
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
console.log('✅ Patch 2: KPI grid → 2 panel-cards (dash-2equal layout)');

// ─────────────────────────────────────────────────────
// Sanity checks
// ─────────────────────────────────────────────────────
const newSize = patched.length;
const sizeDiff = newSize - originalSize;
console.log(`\n📊 Size: ${originalSize} → ${newSize} bytes (${sizeDiff > 0 ? '+' : ''}${sizeDiff})`);

const checks = {
  'cashFlowNet computed': /const cashFlowNet = computed/.test(patched),
  'forecastNet computed': /const forecastNet = computed/.test(patched),
  'Panel 1 title': /Ταμειακή Κίνηση Περιόδου/.test(patched),
  'Panel 2 title': /Πρόβλεψη & Υποχρεώσεις/.test(patched),
  'Εισπράχθηκαν row': /cr-lbl">Εισπράχθηκαν</.test(patched),
  'Πληρώθηκαν row': /cr-lbl">Πληρώθηκαν</.test(patched),
  'Καθαρή Ροή hero': /cr-lbl"\s+style="font-weight:700">Καθαρή Ροή</.test(patched),
  'Καθαρή Πρόβλεψη hero': /cr-lbl"\s+style="font-weight:700">Καθαρή Πρόβλεψη</.test(patched),
  'Σύνολο Υποχρεώσεων row': /cr-lbl">Σύνολο Υποχρεώσεων</.test(patched),
  'No old "Τρέχον Υπόλοιπο"': !/kpi-lbl">Τρέχον Υπόλοιπο</.test(patched),
  'No old kpi-grid': !/<div class="kpi-grid"/.test(patched),
  'dash-2equal added': (patched.match(/dash-2equal/g) || []).length >= 2,
  'panel-card count = 6': (patched.match(/<div class="panel-card">/g) || []).length === 6,
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
console.log('\n🎉 Patch v2 complete. Run: cd frontend && npm run build');