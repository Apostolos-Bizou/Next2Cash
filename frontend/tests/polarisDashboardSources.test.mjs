// node --test — the P&L Dashboard must only read aggregate endpoints (source-level).
//
// A Polaris viewer (e.g. Simos) renders this page, so it may never fetch
// payroll people, milestones or assumptions. The forecast block comes from the
// server-side GET /api/polaris/forecast/summary instead of the store's
// client-side calc (which needs those admin-only inputs).
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const dashboard = readFileSync(join(here, '../src/views/PolarisDashboardView.vue'), 'utf8')
const polarisApi = readFileSync(join(here, '../src/api/polaris.js'), 'utf8')
const store = readFileSync(join(here, '../src/stores/polarisPl.js'), 'utf8')

test('member-forecast mode is persisted as the members_mode assumption (server is the source of truth)', () => {
  assert.match(store, /membersMode: 'members_mode'/)
  assert.match(store, /const MEMBERS_MODE_VALUE = \{ growth: 0, milestones: 1 \}/)
  // the toggle saves through the admin-only assumptions endpoint ...
  assert.match(store, /inputs\.membersMode = MEMBERS_MODE_VALUE\[mode\] \?\? 0\s*\n\s*return saveAssumption\('membersMode'\)/)
  // ... and load() applies the stored value over the localStorage cache
  assert.match(store, /memberForecastMode\.value = membersModeFromValue\(inputs\.membersMode\)/)
  // the Dashboard never sends a mode: it takes whatever the admin persisted
  assert.ok(!dashboard.includes('membersMode'), 'Dashboard must not override the persisted mode')
})

test('Dashboard uses the server-side forecast summary, never people/milestones/assumptions', () => {
  assert.match(dashboard, /import \{ fetchForecastSummary \} from '@\/api\/polaris'/)
  assert.match(dashboard, /fetchForecastSummary\(fcYears\.value\)/)
  for (const forbidden of ['peopleApi', 'milestonesApi', 'assumptionsApi', 'pl.people', 'pl.inputs', 'pl.pnlAtYear', 'pl.cur']) {
    assert.ok(!dashboard.includes(forbidden), `Dashboard must not reference ${forbidden}`)
  }
})

test('forecast summary client hits only /api/polaris/forecast/summary with years', () => {
  assert.match(polarisApi, /export async function fetchForecastSummary\(years = 1\)/)
  assert.match(polarisApi, /'\/api\/polaris\/forecast\/summary'/)
  assert.match(polarisApi, /params: \{ entityId: POLARIS_ENTITY_ID, years \}/)
})

test('Dashboard renders the 1/3/5 horizon toggle, 2 hero cards, 5 KPI tiles and the chart canvas', () => {
  assert.match(dashboard, /v-for="y in \[1, 3, 5\]"[^>]*class="horizon-btn"/)
  assert.equal((dashboard.match(/class="kpi-hero-card /g) || []).length, 2)
  assert.equal((dashboard.match(/<div class="kpi-card">/g) || []).length, 5)
  assert.match(dashboard, /<canvas ref="fcCanvas"><\/canvas>/)
})
