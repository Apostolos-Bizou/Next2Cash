// patches/reorder-top-row-v7.cjs
// Reorder top row: Ταμειακά Διαθέσιμα → Υποχρεώσεις → Τράπεζες → Ισοσκελισμός

const fs = require('fs');
const path = require('path');

const FILE = path.join(__dirname, '..', 'frontend', 'src', 'views', 'DashboardView.vue');

console.log('📄 Reading:', FILE);
const original = fs.readFileSync(FILE, 'utf8');
const originalSize = original.length;
console.log(`   Original: ${originalSize} chars`);

// Strategy: Use unique markers to extract each panel, then reorder.
// The 4 panels live inside a parent div (likely dash-2equal or similar).

// Find the wrapper div opening tag and each panel's marker
const markers = {
  obligations: '<!-- Υποχρεώσεις',
  banks: '<!-- Τράπεζες',  // careful: there's also "Ταμειακά Διαθέσιμα — 3 Σενάρια" pattern
  reconciliation: '<!-- Ισοσκελισμός',
  cash: '<!-- Ταμειακά Διαθέσιμα — 3 Σενάρια',
};

// Find each panel's start position
const positions = {};
for (const [key, marker] of Object.entries(markers)) {
  const idx = original.indexOf(marker);
  if (idx === -1) {
    console.error(`❌ Marker not found: ${marker}`);
    process.exit(1);
  }
  positions[key] = idx;
}

console.log('✅ All 4 panel markers found');
console.log('   Current order (by position):');
const sortedByPos = Object.entries(positions).sort((a, b) => a[1] - b[1]);
sortedByPos.forEach(([k, p]) => console.log(`     ${k}: pos ${p}`));

// Extract each panel: from its marker to before the next panel's marker (or end of wrapper)
// Find end of wrapper: the </div> that closes the row containing these 4 panels
// Strategy: find first panel start, last panel end (next "</div>\n      </div>")

const firstPanelStart = Math.min(...Object.values(positions));
console.log(`\nFirst panel starts at pos: ${firstPanelStart}`);

// Find end of last panel: search for "        </div>\n      </div>" after the last panel start
const lastPanelStart = Math.max(...Object.values(positions));
const wrapperEndSearch = original.indexOf('      </div>', lastPanelStart);
if (wrapperEndSearch === -1) {
  console.error('❌ Wrapper end not found');
  process.exit(1);
}
console.log(`Wrapper end found at pos: ${wrapperEndSearch}`);

// Extract each panel as: from its marker, to just before the next marker
// Sort panels by current position to extract them
const sorted = Object.entries(positions).sort((a, b) => a[1] - b[1]);
const panels = {};
for (let i = 0; i < sorted.length; i++) {
  const [key, start] = sorted[i];
  const end = (i < sorted.length - 1) ? sorted[i + 1][1] : wrapperEndSearch;
  panels[key] = original.substring(start, end);
  console.log(`   Extracted ${key}: ${panels[key].length} chars`);
}

// Verify all 4 extracted
if (Object.keys(panels).length !== 4) {
  console.error('❌ Did not extract 4 panels');
  process.exit(1);
}

// New desired order: cash, obligations, banks, reconciliation
const newOrderKeys = ['cash', 'obligations', 'banks', 'reconciliation'];
const newOrderText = newOrderKeys.map(k => panels[k]).join('');

// Replace from firstPanelStart to wrapperEndSearch with newOrderText
const before = original.substring(0, firstPanelStart);
const after = original.substring(wrapperEndSearch);
const patched = before + newOrderText + after;

console.log(`\n✅ Reordered: cash → obligations → banks → reconciliation`);

// Sanity checks
const newSize = patched.length;
console.log(`📊 Size: ${originalSize} → ${newSize} (${newSize === originalSize ? 'same (good)' : 'different (warn)'})`);

const checks = {
  'All 4 panel markers still present': Object.values(markers).every(m => patched.includes(m)),
  'Cash panel comes before Obligations': patched.indexOf(markers.cash) < patched.indexOf(markers.obligations),
  'Obligations comes before Banks': patched.indexOf(markers.obligations) < patched.indexOf(markers.banks),
  'Banks comes before Reconciliation': patched.indexOf(markers.banks) < patched.indexOf(markers.reconciliation),
  'Hero "Ταμείο σήμερα" present': /Ταμείο σήμερα/.test(patched),
  '6 panel-cards': (patched.match(/<div class="panel-card">/g) || []).length === 6,
  'cashFlowAnalysis still there': /const cashFlowAnalysis = computed/.test(patched),
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
console.log('🎉 v7 reorder complete.');