<script setup>
/* eslint-disable */
// Pricing Calculator View — S86.6
//
// Reverse-pricing tool: computes required revenue from target margin, with
// CFO metrics, 3 scenarios, billing-mix cash flow, and path-to-target.
//
// Modes:
//   GROUP   : consolidated view across all LIVE projects
//   PROJECT : single project with OpEx allocation %
//
// Data source: GET /api/pricing-calculator[?{projectId}][?targetMargin=...]
// Backend service: PricingCalculatorService (S86.5)

import { ref, computed, onMounted, watch, onUnmounted } from 'vue'
import api from '@/api'
import { filterEntityMap, isRestrictedToSingleEntity, isViewer, defaultEntityKey } from '@/stores/entityScope'

/* ----------------------------------------------------------------
   Entity mapping (matches ForecastView pattern)
   ---------------------------------------------------------------- */
const ENTITY_MAP = {
  next2me:      { id: '58202b71-4ddb-45c9-8e3c-39e816bde972', label: 'Next2Me' },
  house:        { id: 'dea1f32c-7b30-4981-b625-633da9dbe71e', label: 'House' },
  next2megroup: { id: '50317f44-9961-4fb4-add0-7a118e32dc14', label: 'Next2Me Group' },
}

const entityKey = ref(defaultEntityKey('next2megroup'))
watch(entityKey, (v) => {
  localStorage.setItem('n2c_entity', v)
  loadProjects()
  load()
})

const currentEntityId = computed(() => ENTITY_MAP[entityKey.value]?.id)

// S87: entity scoping + read-only viewer
const visibleEntityMap = computed(() => filterEntityMap(ENTITY_MAP))
const showEntityDropdown = computed(() => !isRestrictedToSingleEntity())
const isViewerRO = computed(() => isViewer())

/* ----------------------------------------------------------------
   Pricing-specific state
   ---------------------------------------------------------------- */
// 'ALL' = GROUP mode; otherwise specific project UUID
const selectedProjectId = ref(localStorage.getItem('n2c_pricing_project') || 'ALL')
watch(selectedProjectId, (v) => {
  localStorage.setItem('n2c_pricing_project', v)
  load()
})

// Target margin slider: 0.05 - 0.50 (5% - 50%)
const targetMarginPct = ref(Number(localStorage.getItem('n2c_pricing_margin')) || 15)
watch(targetMarginPct, (v) => {
  localStorage.setItem('n2c_pricing_margin', String(v))
  // Debounced reload on slider change
  if (reloadTimer) clearTimeout(reloadTimer)
  reloadTimer = setTimeout(load, 300)
})

let reloadTimer = null

const loading  = ref(false)
const error    = ref(null)
const response = ref(null)
const projects = ref([])  // list of LIVE projects for dropdown

// S86.9: AI CFO Advisor state
const aiLoading = ref(false)
const aiError   = ref(null)
const aiAdvice  = ref(null)

/* ----------------------------------------------------------------
   Computed
   ---------------------------------------------------------------- */
const targetMarginRatio = computed(() => targetMarginPct.value / 100)

const mode = computed(() => response.value?.mode || 'GROUP')
const isGroupMode = computed(() => mode.value === 'GROUP')

const directBurn       = computed(() => num(response.value?.directBurn))
const allocatedOpex    = computed(() => num(response.value?.allocatedOpex))
const totalCost        = computed(() => num(response.value?.totalCost))
const requiredRevenue  = computed(() => num(response.value?.requiredRevenue))
const profitAtMargin   = computed(() => num(response.value?.profitAtMargin))
const currentMrr       = computed(() => num(response.value?.currentMrr))
const gap              = computed(() => num(response.value?.gap))
const currentCustomers = computed(() => Number(response.value?.currentCustomers ?? 0))
const targetCustomers  = computed(() => Number(response.value?.targetCustomers ?? 0))

// CFO metrics
const arpu              = computed(() => num(response.value?.arpu))
const cacPaybackMonths  = computed(() => num(response.value?.cacPaybackMonths))
const ltv               = computed(() => num(response.value?.ltv))
const ltvCacRatio       = computed(() => num(response.value?.ltvCacRatio))
const ruleOf40          = computed(() => num(response.value?.ruleOf40))
const naiveTarget       = computed(() => Number(response.value?.naiveTargetCustomers ?? 0))
const churnAdjustedTarget = computed(() => Number(response.value?.churnAdjustedTargetCustomers ?? 0))

// Billing mix
const annualBillingPct  = computed(() => num(response.value?.annualBillingPct))
const annualDiscountPct = computed(() => num(response.value?.annualDiscountPct))
const stickerAdjustment = computed(() => num(response.value?.stickerAdjustment))
const adjustedTarget    = computed(() => num(response.value?.adjustedTarget))

// Cash flow
const cashUpfront       = computed(() => num(response.value?.cashUpfront))
const monthlyRecurring  = computed(() => num(response.value?.monthlyRecurring))
const totalAnnualized   = computed(() => num(response.value?.totalAnnualized))
const runwayBoostMonths = computed(() => num(response.value?.runwayBoostMonths))

// Path to target
const monthsToBreakEven = computed(() => num(response.value?.monthsToBreakEven))
const monthsAllMonthly  = computed(() => num(response.value?.monthsToBreakEvenAllMonthly))

// Scenarios
const scenarioA = computed(() => response.value?.scenarioA || null)
const scenarioB = computed(() => response.value?.scenarioB || null)
const scenarioC = computed(() => response.value?.scenarioC || null)

// Group breakdown
const projectBreakdown = computed(() => response.value?.projectBreakdown || [])

// CFO health indicators (color coding)
const cacPaybackHealth = computed(() => {
  const m = cacPaybackMonths.value
  if (m === 0) return 'neutral'
  if (m <= 12) return 'green'
  if (m <= 18) return 'amber'
  return 'red'
})

const ltvCacHealth = computed(() => {
  const r = ltvCacRatio.value
  if (r === 0) return 'neutral'
  if (r >= 3) return 'green'
  if (r >= 2) return 'amber'
  return 'red'
})

const ruleOf40Health = computed(() => {
  const r = ruleOf40.value
  if (r === 0) return 'neutral'
  if (r >= 40) return 'green'
  if (r >= 20) return 'amber'
  return 'red'
})

/* ----------------------------------------------------------------
   API calls
   ---------------------------------------------------------------- */
async function loadProjects() {
  if (!currentEntityId.value) return
  try {
    const res = await api.get('/api/projects', {
      params: { entityId: currentEntityId.value }
    })
    const list = res.data?.data || res.data || []
    // Show only LIVE projects in the dropdown
    projects.value = list.filter(p => p.status === 'LIVE')
  } catch (e) {
    console.warn('Failed to load projects', e)
    projects.value = []
  }
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const margin = targetMarginRatio.value
    let res
    if (selectedProjectId.value === 'ALL') {
      res = await api.get('/api/pricing-calculator', {
        params: { targetMargin: margin, entityId: currentEntityId.value }
      })
    } else {
      res = await api.get(`/api/pricing-calculator/${selectedProjectId.value}`, {
        params: { targetMargin: margin }
      })
    }
    response.value = res.data
  } catch (e) {
    error.value = e.response?.data?.error || e.message || 'Failed to load pricing data'
    response.value = null
  } finally {
    loading.value = false
  }
}

// S86.9: request AI CFO advice for the current project/group + margin.
async function loadAiAdvice() {
  aiLoading.value = true
  aiError.value = null
  try {
    const margin = targetMarginRatio.value
    let res
    if (selectedProjectId.value === 'ALL') {
      res = await api.post('/api/pricing-calculator/group/ai-advice', null, {
        params: { targetMargin: margin, entityId: currentEntityId.value }
      })
    } else {
      res = await api.post(`/api/pricing-calculator/${selectedProjectId.value}/ai-advice`, null, {
        params: { targetMargin: margin }
      })
    }
    aiAdvice.value = res.data
  } catch (e) {
    if (e.response?.status === 503) {
      aiError.value = 'Η υπηρεσία AI δεν είναι διαθέσιμη αυτή τη στιγμή. Δοκιμάστε αργότερα.'
    } else {
      aiError.value = e.response?.data?.error || e.message || 'Αποτυχία λήψης συμβουλής AI.'
    }
    aiAdvice.value = null
  } finally {
    aiLoading.value = false
  }
}

// Clear stale advice when the underlying pricing context changes.
watch([selectedProjectId, targetMarginPct, entityKey], () => {
  aiAdvice.value = null
  aiError.value = null
})

onMounted(async () => {
  await loadProjects()
  await load()
})

/* ----------------------------------------------------------------
   Formatters
   ---------------------------------------------------------------- */
function num(v) { return Number(v ?? 0) }

function fmtMoney(v) {
  const n = num(v)
  return new Intl.NumberFormat('el-GR', {
    style: 'decimal',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(Math.round(n)) + ' €'
}

function fmtMoneyExact(v) {
  const n = num(v)
  return new Intl.NumberFormat('el-GR', {
    style: 'decimal',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(n) + ' €'
}

function fmtPct(v) {
  return num(v).toFixed(1) + '%'
}

function fmtRatio(v) {
  return num(v).toFixed(1) + 'x'
}

function fmtMonths(v) {
  const n = num(v)
  if (n === 0 || n >= 999) return '— μήνες'
  return Math.round(n) + ' μήνες'
}

function fmtNumber(v) {
  return new Intl.NumberFormat('el-GR').format(Math.round(num(v)))
}

function fmtTier(t) {
  return `${t.name} (${fmtMoney(t.monthlyPrice)}/μήνα)`
}

// S87.14: react to sidebar entity switch (App.vue dispatches 'entity-changed').
// Copy n2c_entity into the local entityKey ref; the existing watch(entityKey)
// then reloads automatically. Cleaned up on unmount.
function __syncEntityFromSidebar() {
  try {
    const k = localStorage.getItem('n2c_entity')
    if (k && k !== entityKey.value) entityKey.value = k
  } catch (e) { /* ignore */ }
}
onMounted(() => { window.addEventListener('entity-changed', __syncEntityFromSidebar) })
onUnmounted(() => { window.removeEventListener('entity-changed', __syncEntityFromSidebar) })
</script>

<template>
  <div class="pricing-view">
    <!-- ============ HEADER ============ -->
    <header class="header">
      <div class="header-titles">
        <h1>💰 Pricing Calculator</h1>
        <p class="subtitle"><em>αντίστροφος υπολογισμός τιμολόγησης για στόχο κέρδους</em></p>
      </div>

      <div class="header-controls">
        <!-- Entity dropdown (S87: hidden for single-entity users) -->
        <div class="control-group" v-if="showEntityDropdown">
          <label>Entity</label>
          <select v-model="entityKey">
            <option v-for="(meta, key) in visibleEntityMap" :key="key" :value="key">{{ meta.label }}</option>
          </select>
        </div>

        <!-- Project dropdown -->
        <div class="control-group">
          <label>Project</label>
          <select v-model="selectedProjectId">
            <option value="ALL">Όλος ο Όμιλος (Group)</option>
            <option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }}</option>
          </select>
        </div>

        <!-- Target margin slider -->
        <div class="control-group slider-group">
          <label>Target Margin: <strong>{{ targetMarginPct }}%</strong></label>
          <input
            type="range"
            min="5"
            max="50"
            step="1"
            v-model.number="targetMarginPct"
            :disabled="isViewerRO"
          />
        </div>
      </div>
    </header>

    <!-- Loading / Error -->
    <div v-if="loading" class="loading-banner">⏳ Loading pricing data...</div>
    <div v-if="error" class="error-banner">❌ {{ error }}</div>

    <!-- ============ COST/REVENUE BIG CARDS ============ -->
    <section v-if="response" class="big-cards">
      <div class="big-card cost-card">
        <div class="card-label">MONTHLY COST <em>(συνολικό κόστος / μήνα)</em></div>
        <div class="card-value">{{ fmtMoney(totalCost) }}</div>
        <div class="card-breakdown">
          <div>Direct Burn: {{ fmtMoney(directBurn) }}</div>
          <div>Allocated OpEx: {{ fmtMoney(allocatedOpex) }}</div>
        </div>
      </div>

      <div class="big-card revenue-card">
        <div class="card-label">REQUIRED REVENUE <em>(απαιτούμενα έσοδα / μήνα)</em></div>
        <div class="card-value">{{ fmtMoney(requiredRevenue) }}</div>
        <div class="card-breakdown">
          <div>Profit at {{ targetMarginPct }}% margin: {{ fmtMoney(profitAtMargin) }}</div>
          <div v-if="annualBillingPct > 0">
            Sticker (annual disc.): {{ fmtMoney(adjustedTarget) }}
          </div>
        </div>
      </div>
    </section>

    <!-- ============ GAP CARD ============ -->
    <section v-if="response" class="gap-card-wrap">
      <div class="gap-card">
        <div class="gap-label">GAP TO CLOSE <em>(διαφορά τρέχοντος MRR με στόχο)</em></div>
        <div class="gap-value">{{ fmtMoney(gap) }}</div>
        <div class="gap-meta">
          Current MRR: {{ fmtMoney(currentMrr) }} →
          Target: {{ fmtMoney(requiredRevenue) }}
        </div>
      </div>
    </section>

    <!-- ============ CFO METRICS ROW ============ -->
    <section v-if="response" class="cfo-row">
      <div class="cfo-card" :class="cacPaybackHealth">
        <div class="cfo-label">CAC PAYBACK <em>(μήνες αποπληρωμής CAC)</em></div>
        <div class="cfo-value">{{ fmtMonths(cacPaybackMonths) }}</div>
        <div class="cfo-bench">
          <span class="bench-good">≤12 mo healthy</span>
        </div>
      </div>

      <div class="cfo-card" :class="ltvCacHealth">
        <div class="cfo-label">LTV:CAC <em>(αναλογία αξίας/κόστους πελάτη)</em></div>
        <div class="cfo-value">{{ fmtRatio(ltvCacRatio) }}</div>
        <div class="cfo-bench">
          <span class="bench-good">≥3.0x ideal</span>
          <span class="bench-muted"> · LTV {{ fmtMoney(ltv) }}</span>
        </div>
      </div>

      <div class="cfo-card" :class="ruleOf40Health">
        <div class="cfo-label">RULE OF 40 <em>(growth + margin = ≥40)</em></div>
        <div class="cfo-value">{{ fmtPct(ruleOf40) }}</div>
        <div class="cfo-bench">
          <span class="bench-good">≥40% target</span>
        </div>
      </div>

      <div class="cfo-card neutral">
        <div class="cfo-label">CHURN-ADJ TARGET <em>(πελάτες με churn factor)</em></div>
        <div class="cfo-value">{{ fmtNumber(churnAdjustedTarget) }}</div>
        <div class="cfo-bench">
          <span class="bench-muted">naive: {{ fmtNumber(naiveTarget) }}</span>
        </div>
      </div>
    </section>

    <!-- ============ PROJECT BREAKDOWN (GROUP MODE ONLY) ============ -->
    <section v-if="response && isGroupMode && projectBreakdown.length > 0" class="breakdown-section">
      <h2>Project Breakdown <em>(ανάλυση ανά project)</em></h2>
      <table class="breakdown-table">
        <thead>
          <tr>
            <th>Project</th>
            <th>Status</th>
            <th class="num">Direct Burn</th>
            <th class="num">% of Group</th>
            <th class="num">Current MRR</th>
            <th class="num">Customers</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in projectBreakdown" :key="row.projectId">
            <td><strong>{{ row.projectName }}</strong></td>
            <td><span class="pill pill-live">{{ row.status }}</span></td>
            <td class="num">{{ fmtMoney(row.directBurn) }}</td>
            <td class="num">{{ fmtPct(row.pctOfGroup) }}</td>
            <td class="num">{{ fmtMoney(row.currentMrr) }}</td>
            <td class="num">{{ fmtNumber(row.currentCustomers) }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <!-- ============ 3 PRICING SCENARIOS ============ -->
    <section v-if="response" class="scenarios">
      <h2>Pricing Scenarios <em>(3 εναλλακτικές τιμολόγησης)</em></h2>
      <div class="scenarios-grid">

        <!-- Scenario A: Flat €99 -->
        <div v-if="scenarioA" class="scenario-card">
          <div class="scenario-title">A. Flat Pricing</div>
          <div class="scenario-price">{{ fmtMoney(scenarioA.monthlyPrice) }}/μήνα</div>
          <div v-if="scenarioA.annualPrice > 0" class="scenario-annual">
            ή {{ fmtMoney(scenarioA.annualPrice) }}/έτος ({{ fmtMoney(scenarioA.effectiveMonthlyFromAnnual) }}/μήνα)
          </div>
          <div class="scenario-meta">
            <strong>{{ fmtNumber(scenarioA.customersNeeded) }}</strong> πελάτες χρειάζονται
          </div>
        </div>

        <!-- Scenario B: Fixed customers -->
        <div v-if="scenarioB" class="scenario-card">
          <div class="scenario-title">B. Fixed Customers</div>
          <div class="scenario-price">{{ fmtMoneyExact(scenarioB.pricePerCustomerMonthly) }}/μήνα</div>
          <div v-if="scenarioB.pricePerCustomerAnnual > 0" class="scenario-annual">
            ή {{ fmtMoneyExact(scenarioB.pricePerCustomerAnnual) }}/έτος
          </div>
          <div class="scenario-meta">
            Για <strong>{{ scenarioB.fixedCustomers }}</strong> πελάτες
          </div>
        </div>

        <!-- Scenario C: Tiered -->
        <div v-if="scenarioC" class="scenario-card scenario-c">
          <div class="scenario-title">C. {{ scenarioC.label }}</div>
          <div class="scenario-price">Blended: {{ fmtMoney(scenarioC.blendedPrice) }}/μήνα</div>
          <div class="scenario-tiers">
            <div v-for="t in scenarioC.tiers" :key="t.name" class="tier-row">
              <span class="tier-name">{{ t.name }}</span>
              <span class="tier-price">{{ fmtMoney(t.monthlyPrice) }}/mo</span>
              <span class="tier-count">{{ fmtNumber(t.customersNeeded) }} cust</span>
            </div>
          </div>
          <div class="scenario-meta">
            Total: <strong>{{ fmtNumber(scenarioC.customersNeeded) }}</strong> πελάτες
          </div>
        </div>
      </div>
    </section>

    <!-- ============ CASH FLOW IMPACT ============ -->
    <section v-if="response && annualBillingPct > 0" class="cashflow">
      <h2>💵 Cash Flow Impact <em>(προπληρωμένη χρηματορροή & runway boost)</em></h2>
      <div class="cashflow-grid">
        <div class="cashflow-item">
          <div class="cf-label">Annual Prepay</div>
          <div class="cf-value">{{ fmtMoney(cashUpfront) }}</div>
          <div class="cf-meta">{{ fmtPct(annualBillingPct) }} customers prepay annually</div>
        </div>
        <div class="cashflow-item">
          <div class="cf-label">Monthly Recurring</div>
          <div class="cf-value">{{ fmtMoney(monthlyRecurring) }}</div>
          <div class="cf-meta">Rest pays monthly</div>
        </div>
        <div class="cashflow-item">
          <div class="cf-label">Total Annualized</div>
          <div class="cf-value">{{ fmtMoney(totalAnnualized) }}</div>
          <div class="cf-meta">Combined annual revenue</div>
        </div>
        <div class="cashflow-item highlight">
          <div class="cf-label">🚀 Runway Boost</div>
          <div class="cf-value">{{ fmtMonths(runwayBoostMonths) }}</div>
          <div class="cf-meta">Cash bank extension from prepays</div>
        </div>
      </div>
    </section>

    <!-- ============ PATH TO TARGET ============ -->
    <section v-if="response" class="path-section">
      <h2>📈 Path to Target <em>(ορίζοντας επίτευξης break-even)</em></h2>
      <div class="path-grid">
        <div class="path-item">
          <div class="path-label">Current Customers</div>
          <div class="path-value">{{ fmtNumber(currentCustomers) }}</div>
        </div>
        <div class="path-arrow">→</div>
        <div class="path-item">
          <div class="path-label">Target (Churn-Adjusted)</div>
          <div class="path-value">{{ fmtNumber(targetCustomers) }}</div>
        </div>
        <div class="path-arrow">≈</div>
        <div class="path-item highlight">
          <div class="path-label">Months to Break-Even</div>
          <div class="path-value">{{ fmtMonths(monthsToBreakEven) }}</div>
          <div v-if="annualBillingPct > 0" class="path-meta">
            All-monthly would be: {{ fmtMonths(monthsAllMonthly) }}
          </div>
        </div>
      </div>
      <p class="path-note">
        Υπολογισμός με 10 νέους πελάτες/μήνα ως heuristic, μείον churn loss των τρεχόντων πελατών.
      </p>
    </section>

    <!-- ============ AI CFO ADVISOR (S86.9) ============ -->
    <section v-if="response" class="ai-advice-section">
      <div class="ai-advice-head">
        <h2>🤖 AI CFO Advisor</h2>
        <button
          class="ai-advice-btn"
          :disabled="aiLoading"
          @click="loadAiAdvice">
          <span v-if="aiLoading">Ανάλυση σε εξέλιξη…</span>
          <span v-else-if="aiAdvice">Νέα ανάλυση</span>
          <span v-else>Ζήτησε συμβουλή από AI CFO</span>
        </button>
      </div>
      <p class="ai-advice-intro">
        Συγκριτική ανάλυση 3 στρατηγικών τιμολόγησης (Conservative / Balanced / Aggressive)
        με benchmarks του κλάδου και πρακτικές συστάσεις, βασισμένη στα τρέχοντα δεδομένα.
      </p>

      <div v-if="aiError" class="ai-advice-error">{{ aiError }}</div>

      <div v-if="aiLoading" class="ai-advice-loading">
        <div class="ai-spinner"></div>
        <span>Ο AI CFO αναλύει τα οικονομικά σου… (10-20 δευτ.)</span>
      </div>

      <div v-if="aiAdvice && !aiLoading" class="ai-advice-body">
        <p v-if="aiAdvice.fromCache" class="ai-advice-cache">
          (αποθηκευμένη ανάλυση — ανανεώνεται κάθε 24 ώρες)
        </p>

        <p v-if="aiAdvice.summary" class="ai-advice-summary">{{ aiAdvice.summary }}</p>

        <!-- 3 strategy cards -->
        <div class="ai-strategies" v-if="aiAdvice.strategies && aiAdvice.strategies.length">
          <div
            v-for="(s, i) in aiAdvice.strategies"
            :key="i"
            class="ai-strat-card"
            :class="'strat-' + i">
            <div class="ai-strat-name">{{ s.name }}</div>
            <div class="ai-strat-price">{{ s.monthlyPrice }}</div>
            <div class="ai-strat-row" v-if="s.positioning">
              <span class="ai-strat-k">Τοποθέτηση</span>
              <span class="ai-strat-v">{{ s.positioning }}</span>
            </div>
            <div class="ai-strat-row" v-if="s.tradeoff">
              <span class="ai-strat-k">Trade-off</span>
              <span class="ai-strat-v">{{ s.tradeoff }}</span>
            </div>
            <div class="ai-strat-row" v-if="s.bestWhen">
              <span class="ai-strat-k">Ιδανικό όταν</span>
              <span class="ai-strat-v">{{ s.bestWhen }}</span>
            </div>
          </div>
        </div>

        <!-- benchmarks -->
        <div class="ai-bench-wrap" v-if="aiAdvice.benchmarks && aiAdvice.benchmarks.length">
          <h3 class="ai-sub">Benchmarks κλάδου</h3>
          <table class="ai-bench-table">
            <thead>
              <tr>
                <th>Δείκτης</th>
                <th>Εσύ</th>
                <th>Κλάδος</th>
                <th>Κρίση</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(b, i) in aiAdvice.benchmarks" :key="i">
                <td>{{ b.metric }}</td>
                <td>{{ b.yours }}</td>
                <td>{{ b.industry }}</td>
                <td>
                  <span class="ai-verdict" :class="'v-' + (b.verdict || '').toLowerCase()">
                    {{ b.verdict }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- recommendations -->
        <div class="ai-recs-wrap" v-if="aiAdvice.recommendations && aiAdvice.recommendations.length">
          <h3 class="ai-sub">Συστάσεις</h3>
          <ul class="ai-recs">
            <li v-for="(r, i) in aiAdvice.recommendations" :key="i">{{ r }}</li>
          </ul>
        </div>

        <p class="ai-advice-foot" v-if="aiAdvice.modelUsed">
          Παράχθηκε από {{ aiAdvice.modelUsed }}. Συμβουλευτικό — όχι υποκατάστατο επαγγελματικής οικονομικής γνώμης.
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.pricing-view {
  padding: 24px;
  color: #E5E7EB;
  max-width: 1400px;
  margin: 0 auto;
}

/* ============ HEADER ============ */
.header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: flex-end;
  gap: 24px;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #334155;
}
.header-titles h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 600;
  color: #fff;
}
.subtitle {
  margin: 4px 0 0 0;
  color: #94A3B8;
  font-size: 14px;
}
.header-controls {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: flex-end;
}
.control-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 180px;
}
.control-group label {
  font-size: 12px;
  color: #94A3B8;
  font-weight: 500;
}
.control-group select {
  padding: 8px 12px;
  background: #1E293B;
  color: #E5E7EB;
  border: 1px solid #334155;
  border-radius: 6px;
  font-size: 14px;
}
.slider-group input[type="range"] {
  width: 200px;
  accent-color: #8B5CF6;
}

/* ============ BIG CARDS ============ */
.big-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}
.big-card {
  background: #1E293B;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #334155;
}
.big-card.cost-card {
  border-left: 4px solid #F97316;
}
.big-card.revenue-card {
  border-left: 4px solid #10B981;
}
.card-label {
  font-size: 13px;
  color: #94A3B8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-weight: 600;
}
.card-label em {
  display: block;
  margin-top: 2px;
  color: #64748B;
  font-size: 12px;
  font-style: italic;
  text-transform: none;
  letter-spacing: normal;
  font-weight: 400;
}
.card-value {
  font-size: 38px;
  font-weight: 700;
  color: #fff;
  margin: 12px 0 8px 0;
  line-height: 1;
}
.card-breakdown {
  font-size: 13px;
  color: #94A3B8;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* ============ GAP CARD ============ */
.gap-card-wrap {
  margin-bottom: 16px;
}
.gap-card {
  background: linear-gradient(135deg, #1E293B 0%, #292B3B 100%);
  border: 1px solid #F97316;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
}
.gap-label {
  font-size: 13px;
  color: #FED7AA;
  text-transform: uppercase;
  font-weight: 600;
}
.gap-label em {
  display: block;
  color: #94A3B8;
  font-size: 12px;
  font-style: italic;
  text-transform: none;
  font-weight: 400;
  margin-top: 2px;
}
.gap-value {
  font-size: 32px;
  font-weight: 700;
  color: #F97316;
}
.gap-meta {
  color: #94A3B8;
  font-size: 13px;
}

/* ============ CFO METRICS ============ */
.cfo-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}
.cfo-card {
  background: #1E293B;
  border-radius: 10px;
  padding: 16px;
  border-top: 3px solid #475569;
}
.cfo-card.green { border-top-color: #10B981; }
.cfo-card.amber { border-top-color: #F59E0B; }
.cfo-card.red   { border-top-color: #EF4444; }
.cfo-card.neutral { border-top-color: #6366F1; }

.cfo-label {
  font-size: 12px;
  color: #94A3B8;
  text-transform: uppercase;
  font-weight: 600;
}
.cfo-label em {
  display: block;
  color: #64748B;
  font-size: 11px;
  font-style: italic;
  text-transform: none;
  font-weight: 400;
  margin-top: 2px;
}
.cfo-value {
  font-size: 28px;
  font-weight: 700;
  color: #fff;
  margin: 8px 0 4px 0;
}
.cfo-bench {
  font-size: 12px;
}
.bench-good   { color: #10B981; font-weight: 600; }
.bench-muted  { color: #64748B; }

/* ============ BREAKDOWN TABLE ============ */
.breakdown-section {
  background: #1E293B;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 24px;
}
.breakdown-section h2 {
  margin: 0 0 16px 0;
  font-size: 18px;
  color: #fff;
}
.breakdown-section h2 em {
  display: inline;
  color: #94A3B8;
  font-size: 14px;
  font-style: italic;
  font-weight: 400;
  margin-left: 8px;
}
.breakdown-table {
  width: 100%;
  border-collapse: collapse;
}
.breakdown-table th {
  text-align: left;
  padding: 10px 8px;
  border-bottom: 1px solid #334155;
  color: #94A3B8;
  font-weight: 600;
  font-size: 13px;
}
.breakdown-table th.num,
.breakdown-table td.num {
  text-align: right;
}
.breakdown-table td {
  padding: 10px 8px;
  border-bottom: 1px solid #292B3B;
  color: #E5E7EB;
  font-size: 14px;
}
.pill {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}
.pill-live {
  background: #064E3B;
  color: #6EE7B7;
}

/* ============ SCENARIOS ============ */
.scenarios {
  margin-bottom: 24px;
}
.scenarios h2 {
  color: #fff;
  font-size: 18px;
  margin: 0 0 16px 0;
}
.scenarios h2 em {
  display: inline;
  color: #94A3B8;
  font-size: 14px;
  font-style: italic;
  font-weight: 400;
  margin-left: 8px;
}
.scenarios-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.scenario-card {
  background: #1E293B;
  border-radius: 10px;
  padding: 20px;
  border: 1px solid #334155;
}
.scenario-c {
  border-color: #8B5CF6;
}
.scenario-title {
  font-size: 13px;
  color: #94A3B8;
  text-transform: uppercase;
  font-weight: 600;
  margin-bottom: 8px;
}
.scenario-price {
  font-size: 24px;
  font-weight: 700;
  color: #fff;
  margin-bottom: 4px;
}
.scenario-annual {
  font-size: 13px;
  color: #94A3B8;
  margin-bottom: 12px;
}
.scenario-meta {
  font-size: 14px;
  color: #CBD5E1;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #334155;
}
.scenario-tiers {
  margin: 12px 0;
}
.tier-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  font-size: 12px;
  padding: 4px 0;
  border-bottom: 1px solid #292B3B;
}
.tier-name { color: #CBD5E1; font-weight: 500; }
.tier-price { color: #94A3B8; text-align: center; }
.tier-count { color: #94A3B8; text-align: right; }

/* ============ CASH FLOW ============ */
.cashflow {
  background: linear-gradient(135deg, #064E3B 0%, #1E293B 100%);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 24px;
  border: 1px solid #10B981;
}
.cashflow h2 {
  color: #fff;
  font-size: 18px;
  margin: 0 0 16px 0;
}
.cashflow h2 em {
  display: inline;
  color: #94A3B8;
  font-size: 14px;
  font-style: italic;
  font-weight: 400;
  margin-left: 8px;
}
.cashflow-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.cashflow-item {
  background: rgba(0, 0, 0, 0.25);
  border-radius: 8px;
  padding: 14px;
}
.cashflow-item.highlight {
  background: rgba(16, 185, 129, 0.15);
  border: 1px solid #10B981;
}
.cf-label {
  font-size: 12px;
  color: #94A3B8;
  font-weight: 600;
  text-transform: uppercase;
}
.cf-value {
  font-size: 22px;
  font-weight: 700;
  color: #fff;
  margin: 6px 0;
}
.cf-meta {
  font-size: 11px;
  color: #94A3B8;
}

/* ============ PATH TO TARGET ============ */
.path-section {
  background: #1E293B;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 24px;
}
.path-section h2 {
  color: #fff;
  font-size: 18px;
  margin: 0 0 16px 0;
}
.path-section h2 em {
  display: inline;
  color: #94A3B8;
  font-size: 14px;
  font-style: italic;
  font-weight: 400;
  margin-left: 8px;
}
.path-grid {
  display: flex;
  align-items: center;
  justify-content: space-around;
  flex-wrap: wrap;
  gap: 16px;
}
.path-item {
  background: rgba(0,0,0,0.25);
  border-radius: 8px;
  padding: 14px 20px;
  text-align: center;
  min-width: 200px;
}
.path-item.highlight {
  background: rgba(139, 92, 246, 0.15);
  border: 1px solid #8B5CF6;
}
.path-label {
  font-size: 12px;
  color: #94A3B8;
  text-transform: uppercase;
  font-weight: 600;
}
.path-value {
  font-size: 28px;
  font-weight: 700;
  color: #fff;
  margin: 6px 0;
}
.path-meta {
  font-size: 11px;
  color: #94A3B8;
}
.path-arrow {
  font-size: 24px;
  color: #6366F1;
  font-weight: 700;
}
.path-note {
  margin-top: 12px;
  font-size: 12px;
  color: #64748B;
  font-style: italic;
}

/* ============ AI CFO ADVISOR (S86.9) ============ */
.ai-advice-section {
  background: linear-gradient(135deg, #1E1B4B 0%, #1E293B 100%);
  border: 1px solid #312E81;
  border-radius: 14px;
  padding: 24px;
  margin-top: 24px;
}
.ai-advice-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.ai-advice-head h2 {
  color: #fff;
  margin: 0;
  font-size: 20px;
}
.ai-advice-btn {
  background: #6366F1;
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 10px 18px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease;
}
.ai-advice-btn:hover:not(:disabled) { background: #4F46E5; }
.ai-advice-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.ai-advice-intro { color: #C7D2FE; font-size: 13px; margin: 10px 0 18px; }
.ai-advice-error {
  background: rgba(239, 68, 68, 0.12);
  border: 1px solid #EF4444;
  color: #FCA5A5;
  padding: 12px 14px;
  border-radius: 8px;
  font-size: 14px;
}
.ai-advice-loading {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #C7D2FE;
  font-size: 14px;
  padding: 20px 0;
}
.ai-spinner {
  width: 22px;
  height: 22px;
  border: 3px solid rgba(199, 210, 254, 0.3);
  border-top-color: #C7D2FE;
  border-radius: 50%;
  animation: ai-spin 0.8s linear infinite;
}
@keyframes ai-spin { to { transform: rotate(360deg); } }
.ai-advice-cache { color: #94A3B8; font-size: 12px; font-style: italic; margin: 0 0 8px; }
.ai-advice-summary {
  color: #E2E8F0;
  font-size: 15px;
  line-height: 1.6;
  margin: 0 0 20px;
}
.ai-strategies {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-bottom: 24px;
}
.ai-strat-card {
  background: #0F172A;
  border-radius: 10px;
  padding: 16px;
  border-top: 3px solid #6366F1;
}
.ai-strat-card.strat-0 { border-top-color: #10B981; }
.ai-strat-card.strat-1 { border-top-color: #6366F1; }
.ai-strat-card.strat-2 { border-top-color: #F59E0B; }
.ai-strat-name {
  font-size: 13px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: #94A3B8;
}
.ai-strat-price {
  font-size: 24px;
  font-weight: 700;
  color: #fff;
  margin: 6px 0 12px;
}
.ai-strat-row { margin-bottom: 10px; }
.ai-strat-k {
  display: block;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  color: #64748B;
  margin-bottom: 2px;
}
.ai-strat-v { display: block; font-size: 13px; color: #CBD5E1; line-height: 1.4; }
.ai-sub {
  color: #fff;
  font-size: 15px;
  margin: 0 0 12px;
}
.ai-bench-wrap { margin-bottom: 24px; }
.ai-bench-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.ai-bench-table th {
  text-align: left;
  color: #94A3B8;
  font-weight: 600;
  padding: 8px 10px;
  border-bottom: 1px solid #334155;
}
.ai-bench-table td {
  color: #E2E8F0;
  padding: 8px 10px;
  border-bottom: 1px solid #1E293B;
}
.ai-verdict {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
}
.ai-verdict.v-good  { background: rgba(16, 185, 129, 0.15); color: #6EE7B7; }
.ai-verdict.v-watch { background: rgba(245, 158, 11, 0.15); color: #FCD34D; }
.ai-verdict.v-risk  { background: rgba(239, 68, 68, 0.15);  color: #FCA5A5; }
.ai-recs-wrap { margin-bottom: 8px; }
.ai-recs {
  margin: 0;
  padding-left: 20px;
}
.ai-recs li {
  color: #E2E8F0;
  font-size: 14px;
  line-height: 1.6;
  margin-bottom: 8px;
}
.ai-advice-foot {
  color: #64748B;
  font-size: 11px;
  font-style: italic;
  margin: 16px 0 0;
}

/* ============ AI PLACEHOLDER ============ */
.ai-placeholder {
  background: linear-gradient(135deg, #312E81 0%, #1E293B 100%);
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #6366F1;
}
.ai-placeholder h2 {
  color: #fff;
  font-size: 18px;
  margin: 0 0 8px 0;
}
.ai-placeholder p {
  color: #C7D2FE;
  margin: 0 0 12px 0;
  font-size: 14px;
}
.btn-disabled {
  background: #475569;
  color: #94A3B8;
  border: none;
  padding: 10px 20px;
  border-radius: 6px;
  cursor: not-allowed;
  font-size: 14px;
}

/* ============ BANNERS ============ */
.loading-banner {
  background: #1E40AF;
  color: #fff;
  padding: 10px 14px;
  border-radius: 6px;
  margin-bottom: 16px;
}
.error-banner {
  background: #7F1D1D;
  color: #fff;
  padding: 10px 14px;
  border-radius: 6px;
  margin-bottom: 16px;
}

/* ============ RESPONSIVE ============ */
@media (max-width: 900px) {
  .big-cards,
  .cfo-row,
  .scenarios-grid,
  .cashflow-grid {
    grid-template-columns: 1fr;
  }
  .header-controls {
    width: 100%;
  }
}
</style>
