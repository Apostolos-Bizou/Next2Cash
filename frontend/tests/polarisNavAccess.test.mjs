// node --test — Polaris P&L nav/router role gating (source-level, no Vue runtime).
//
// Access model (Notion decision, Oct 2026): the P&L Dashboard is visible to the
// global VIEWER role too (read-only aggregate); Μισθοδοσία & Προσωπικό and
// Προβλέψεις stay admin/user/accountant only. Every Polaris item additionally
// requires the Polaris entity to be in scope AND selected (entityOk), so a viewer
// without Polaris in scope sees nothing from this section.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const appVue = readFileSync(join(here, '../src/App.vue'), 'utf8')
const routerJs = readFileSync(join(here, '../src/router/index.js'), 'utf8')

// Pull `roles: [...]` off the line that declares the given path.
function rolesFor(source, path) {
  const line = source.split('\n').find(l => l.includes(`'${path}'`))
  assert.ok(line, `no declaration for ${path}`)
  const m = line.match(/roles:\s*\[([^\]]*)\]/)
  assert.ok(m, `no roles array on ${path}`)
  return m[1].split(',').map(s => s.trim().replace(/['"]/g, '')).filter(Boolean)
}

const DASHBOARD = '/polaris/dashboard'
const RESTRICTED = ['/polaris/payroll', '/polaris/forecast']

test('nav: only the P&L Dashboard item admits viewer', () => {
  assert.deepEqual(rolesFor(appVue, DASHBOARD), ['admin', 'user', 'accountant', 'viewer'])
  for (const p of RESTRICTED) {
    const roles = rolesFor(appVue, p)
    assert.deepEqual(roles, ['admin', 'user', 'accountant'], p)
    assert.ok(!roles.includes('viewer'), `${p} must not be nav-reachable for viewer`)
  }
})

test('router: mirrors the nav — viewer admitted on the Dashboard route only', () => {
  assert.deepEqual(rolesFor(routerJs, DASHBOARD), ['admin', 'user', 'accountant', 'viewer'])
  for (const p of RESTRICTED) {
    assert.ok(!rolesFor(routerJs, p).includes('viewer'), `${p} route must not admit viewer`)
  }
  // a viewer who types a restricted URL is bounced to /dashboard
  assert.match(routerJs, /if \(to\.meta\.polaris\) return '\/dashboard'/)
})

test('nav: every Polaris item requires the Polaris entity in scope AND selected (entityOk)', () => {
  for (const p of [DASHBOARD, ...RESTRICTED]) {
    const line = appVue.split('\n').find(l => l.includes(`'${p}'`))
    assert.match(line, /requiresEntity:\s*'polaris'/, p)
  }
  // the guard itself: scope membership + current selection, both required
  assert.match(appVue, /const entityOk = \(item\) => !item\.requiresEntity\s*\n?\s*\|\| \(filteredEntities\.value\.some\(e => e\.key === item\.requiresEntity\) && selectedEntity\.value === item\.requiresEntity\)/)
  // and an empty section is dropped, so a viewer with no Polaris scope sees no POLARIS P&L header
  assert.match(appVue, /\.filter\(section => section\.items\.length > 0\)/)
})

test('nav filter: roleOk + entityOk are both applied to every item', () => {
  assert.match(appVue, /const roleOk = \(item\) => !item\.roles \|\| item\.roles\.includes\(userRole\.value\)/)
  assert.match(appVue, /if \(!roleOk\(item\)\) return false\s*\n\s*if \(!entityOk\(item\)\) return false/)
})
