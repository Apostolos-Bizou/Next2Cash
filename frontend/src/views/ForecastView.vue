<script setup>
// Investor Reports / Forecast View - S85 Step C.2 stub
// Real UI lands in Step C.3 (chart, KPIs, horizon selector, table).
// This stub validates the C.2 plumbing: routing, allowedSections,
// navigation guard, sidebar menu entry, AdminView toggle.

import { onMounted, ref } from 'vue'
import api from '@/api'

const apiHealth = ref({ checked: false, ok: false, message: '' })

async function pingForecastEndpoint() {
  apiHealth.value = { checked: false, ok: false, message: 'Checking...' }
  try {
    // Use Next2Me Group as default ping target (entity is always required)
    const NEXT2ME_GROUP = '50317f44-9961-4fb4-add0-7a118e32dc14'
    const res = await api.get('/api/forecast', {
      params: { entityId: NEXT2ME_GROUP, horizonMonths: 12 }
    })
    apiHealth.value = {
      checked: true,
      ok: true,
      message: 'OK - ' + (res.data?.entryCount ?? 0) + ' entries, '
        + (res.data?.totalIncome ?? 0) + ' total income'
    }
  } catch (e) {
    const status = e?.response?.status
    apiHealth.value = {
      checked: true,
      ok: false,
      message: status
        ? 'API not yet deployed (HTTP ' + status + ')'
        : 'Network error: ' + (e?.message || 'unknown')
    }
  }
}

onMounted(() => {
  // Don't auto-ping yet -- backend endpoint not deployed in production.
  // User can click the button to test once backend is live.
})
</script>

<template>
  <div class="forecast-stub">
    <div class="forecast-stub__card">
      <div class="forecast-stub__icon">&#x1F48E;</div>
      <h1 class="forecast-stub__title">Investor Reports</h1>
      <p class="forecast-stub__subtitle">Forecast Engine &middot; Cash Planning &middot; Runway Analysis</p>

      <div class="forecast-stub__status">
        <span class="forecast-stub__pill">S85 Step C.2 stub</span>
        <span class="forecast-stub__pill forecast-stub__pill--planned">Real UI: Step C.3</span>
      </div>

      <div class="forecast-stub__roadmap">
        <h3>Roadmap</h3>
        <ul>
          <li>&#x2705; Step A &mdash; Backend Forecast Engine scaffolding (local)</li>
          <li>&#x2705; Step B &mdash; LIVE project income (local, 190/190 tests)</li>
          <li>&#x2705; Step C.2 &mdash; Frontend plumbing (this stub)</li>
          <li>&#x23F3; Step C.3 &mdash; Horizon selector, Burn vs MRR chart, Runway KPI</li>
          <li>&#x23F3; Step D &mdash; Deploy backend + frontend together</li>
        </ul>
      </div>

      <div class="forecast-stub__health">
        <h3>Backend health check</h3>
        <p class="forecast-stub__hint">
          The <code>/api/forecast</code> endpoint exists locally but has not been deployed yet.
          Production ping is expected to fail until Step D.
        </p>
        <button class="forecast-stub__btn" @click="pingForecastEndpoint">
          Ping /api/forecast
        </button>
        <div v-if="apiHealth.checked" class="forecast-stub__result"
             :class="{ 'forecast-stub__result--ok': apiHealth.ok,
                       'forecast-stub__result--err': !apiHealth.ok }">
          {{ apiHealth.message }}
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.forecast-stub {
  padding: 32px;
  min-height: calc(100vh - 80px);
  display: flex;
  align-items: flex-start;
  justify-content: center;
}

.forecast-stub__card {
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: 12px;
  padding: 40px;
  max-width: 720px;
  width: 100%;
  color: #e2e8f0;
}

.forecast-stub__icon {
  font-size: 56px;
  text-align: center;
  margin-bottom: 16px;
}

.forecast-stub__title {
  font-size: 28px;
  font-weight: 600;
  text-align: center;
  margin: 0 0 8px;
  color: #f1f5f9;
}

.forecast-stub__subtitle {
  text-align: center;
  color: #94a3b8;
  font-size: 14px;
  margin: 0 0 24px;
}

.forecast-stub__status {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-bottom: 32px;
}

.forecast-stub__pill {
  background: #0f766e;
  color: #f0fdfa;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}

.forecast-stub__pill--planned {
  background: #475569;
  color: #cbd5e1;
}

.forecast-stub__roadmap {
  background: #0f172a;
  border-radius: 8px;
  padding: 20px 24px;
  margin-bottom: 24px;
}

.forecast-stub__roadmap h3,
.forecast-stub__health h3 {
  margin: 0 0 12px;
  font-size: 14px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #94a3b8;
}

.forecast-stub__roadmap ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.forecast-stub__roadmap li {
  padding: 6px 0;
  font-size: 14px;
  color: #cbd5e1;
}

.forecast-stub__health {
  background: #0f172a;
  border-radius: 8px;
  padding: 20px 24px;
}

.forecast-stub__hint {
  font-size: 13px;
  color: #94a3b8;
  margin: 0 0 16px;
  line-height: 1.5;
}

.forecast-stub__hint code {
  background: #1e293b;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: ui-monospace, "SF Mono", Monaco, monospace;
  font-size: 12px;
  color: #67e8f9;
}

.forecast-stub__btn {
  background: #3b82f6;
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s;
}

.forecast-stub__btn:hover {
  background: #2563eb;
}

.forecast-stub__result {
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 6px;
  font-size: 13px;
  font-family: ui-monospace, "SF Mono", Monaco, monospace;
}

.forecast-stub__result--ok {
  background: #064e3b;
  color: #6ee7b7;
}

.forecast-stub__result--err {
  background: #7f1d1d;
  color: #fca5a5;
}
</style>
