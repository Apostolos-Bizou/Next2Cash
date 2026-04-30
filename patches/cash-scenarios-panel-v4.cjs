// patches/cash-scenarios-panel-v4.cjs
// V4: Replaces "Ταμειακά Διαθέσιμα" panel with new "Ταμείο" 3-scenario view
//     Hero: Bank balance today (Ταμείο σήμερα)
//     Rows: 3 scenarios (today / after urgent / after all obligations)
//     Highlight: Λείπουν (deficit)

const fs = require('fs');
const path = require('path');

const FILE = path.join(__dirname, '..', 'frontend', 'src', 'views', 'DashboardView.vue');

console.log('📄 Reading:', FILE);
const original = fs.readFileSync(FILE, 'utf8');
const originalSize = original.length;
console.log(`   Original size: ${originalSize} bytes`);

// ─────────────────────────────────────────────────────
// PATCH 1: Add 3 new computed properties
// ─────────────────────────────────────────────────────
const oldComputed = `const cashAvailable = computed(() => bankTotal.value - (Number(kpis.value.urgentTotal) || 0))`;
const newComputed = `const cashAvailable = computed(() => bankTotal.value - (Number(kpis.value.urgentTotal) || 0))
  // Session #43 — Cash position scenarios (3-step view)
  const cashAfterUrgent = computed(() => bankTotal.value - (Number(kpis.value.urgentTotal) || 0))
  const cashAfterAll    = computed(() => bankTotal.value - (Number(kpis.value.unpaidTotal) || 0))
  const cashShortfall   = computed(() => {
    const after = cashAfterAll.value
    return after < 0 ? Math.abs(after) : 0
  })`;

if (!original.includes(oldComputed)) {
  console.error('❌ cashAvailable computed not found.');
  process.exit(1);
}
let patched = original.replace(oldComputed, newComputed);
console.log('✅ Patch 1: cashAfterUrgent + cashAfterAll + cashShortfall computed added');

// ─────────────────────────────────────────────────────
// PATCH 2: Replace entire "Ταμειακά Διαθέσιμα" panel block
// ─────────────────────────────────────────────────────
const oldPanel = `<!-- Ταμειακά Διαθέσιμα -->
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

const newPanel = `<!-- Ταμειακά Διαθέσιμα — 3 Σενάρια (Session #43) -->
        <div class="panel-card">
          <div class="panel-hdr">
            <span class="ptitle succ"><i class="fas fa-coins"></i> Ταμειακά Διαθέσιμα</span>
          </div>
          <div class="cash-hero">
            <div class="cash-tot" :style="{color: bankTotal>=0?'var(--success)':'var(--danger)'}">{{ fmt(bankTotal) }}</div>
            <div class="cash-lbl">Ταμείο σήμερα</div>
          </div>
          <div class="cash-bk">
            <!-- 1️⃣ Σήμερα (ταμείο) -->
            <div class="cash-row">
              <div class="cr-ico" style="background:var(--accent-glow);color:var(--accent)"><i class="fas fa-university"></i></div>
              <span class="cr-lbl">1️⃣ Σήμερα (ταμείο)</span>
              <span class="cr-val">{{ fmt(bankTotal) }}</span>
            </div>
            <!-- 2️⃣ Μετά τις επείγουσες -->
            <div class="cash-row" v-if="Number(kpis.urgentTotal)>0">
              <div class="cr-ico" style="background:rgba(255,100,0,.15);color:#ff6400"><i class="fas fa-bolt"></i></div>
              <span class="cr-lbl">2️⃣ Μετά τις επείγουσες</span>
              <span class="cr-val" :style="{color:cashAfterUrgent>=0?'var(--success)':'var(--danger)'}">{{ fmt(cashAfterUrgent) }}</span>
            </div>
            <!-- 3️⃣ Μετά από όλες τις υποχρεώσεις -->
            <div class="cash-row">
              <div class="cr-ico" style="background:var(--warning-bg);color:var(--warning)"><i class="fas fa-clock"></i></div>
              <span class="cr-lbl">3️⃣ Μετά από όλα</span>
              <span class="cr-val" :style="{color:cashAfterAll>=0?'var(--success)':'var(--danger)',fontWeight:'700'}">{{ fmt(cashAfterAll) }}</span>
            </div>
            <div style="height:1px;background:var(--border);margin:4px 0"></div>
            <!-- Highlight: Λείπουν -->
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

const matches = (patched.match(new RegExp(oldPanel.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g')) || []).length;
if (matches !== 1) {
  console.error(`❌ Old "Ταμειακά Διαθέσιμα" panel: expected 1 match, found ${matches}.`);
  process.exit(1);
}
patched = patched.replace(oldPanel, newPanel);
console.log('✅ Patch 2: "Ταμειακά Διαθέσιμα" panel replaced (3 scenarios + shortfall)');

// ─────────────────────────────────────────────────────
// PATCH 3: Add cashScenarioAnalysis computed (after cashShortfall)
// ─────────────────────────────────────────────────────
const oldComp2 = `const cashShortfall   = computed(() => {
    const after = cashAfterAll.value
    return after < 0 ? Math.abs(after) : 0
  })`;
const newComp2 = `const cashShortfall   = computed(() => {
    const after = cashAfterAll.value
    return after < 0 ? Math.abs(after) : 0
  })
  // Session #43 — Cash position narrative
  const cashScenarioAnalysis = computed(() => {
    const bank = bankTotal.value
    const unpaid = Number(kpis.value.unpaidTotal) || 0
    const shortfall = cashShortfall.value
    if (shortfall > 0) {
      return \`Με \${fmt(bank)} στο ταμείο και \${fmt(unpaid)} σε συνολικές υποχρεώσεις, λείπουν \${fmt(shortfall)} για να καλυφθούν όλα. ⚠️ Απαιτείται είσπραξη ή αναδιάταξη πληρωμών.\`
    }
    if (unpaid > 0) {
      const surplus = bank - unpaid
      return \`Με \${fmt(bank)} στο ταμείο μπορούν να καλυφθούν οι \${fmt(unpaid)} σε υποχρεώσεις και να μείνουν \${fmt(surplus)} αποθεματικό. 💡 Η ταμειακή θέση είναι υγιής.\`
    }
    return \`Δεν υπάρχουν εκκρεμείς υποχρεώσεις. Διαθέσιμο ταμείο \${fmt(bank)}. 💡 Καλή στιγμή για στρατηγικό σχεδιασμό.\`
  })`;

if (!patched.includes(oldComp2)) {
  console.error('❌ cashShortfall computed not found post-patch.');
  process.exit(1);
}
patched = patched.replace(oldComp2, newComp2);
console.log('✅ Patch 3: cashScenarioAnalysis computed added');

// ─────────────────────────────────────────────────────
// Sanity checks
// ─────────────────────────────────────────────────────
const newSize = patched.length;
const sizeDiff = newSize - originalSize;
console.log(`\n📊 Size: ${originalSize} → ${newSize} bytes (${sizeDiff > 0 ? '+' : ''}${sizeDiff})`);

const checks = {
  'cashAfterUrgent computed': /const cashAfterUrgent\s+= computed/.test(patched),
  'cashAfterAll computed': /const cashAfterAll\s+= computed/.test(patched),
  'cashShortfall computed': /const cashShortfall\s+= computed/.test(patched),
  'cashScenarioAnalysis computed': /const cashScenarioAnalysis = computed/.test(patched),
  'Hero shows bankTotal': /cash-tot[\s\S]{0,300}fmt\(bankTotal\)/.test(patched),
  'Label "Ταμείο σήμερα"': /Ταμείο σήμερα/.test(patched),
  'Row 1: Σήμερα (ταμείο)': /1️⃣ Σήμερα/.test(patched),
  'Row 2: Μετά τις επείγουσες': /2️⃣ Μετά τις επείγουσες/.test(patched),
  'Row 3: Μετά από όλα': /3️⃣ Μετά από όλα/.test(patched),
  'Highlight: Λείπουν': /💸 Λείπουν/.test(patched),
  'Highlight: Επαρκή': /✅ Επαρκή/.test(patched),
  'Analysis div present': /\{\{ cashScenarioAnalysis \}\}/.test(patched),
  'No old "Καθαρά Διαθέσιμα" row': !/Καθαρά Διαθέσιμα/.test(patched),
  'No old "Τράπεζες μείον Εκκρεμείς"': !/Τράπεζες μείον Εκκρεμείς/.test(patched),
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
console.log('\n🎉 Patch v4 complete.');