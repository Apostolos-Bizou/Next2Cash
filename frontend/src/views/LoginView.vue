<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import api from '@/api'

const router = useRouter()
const userStore = useUserStore()
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

// PHASE_61_LANDING_ROUTE: resolve landing route based on allowedSections
// Section -> route path map (must stay in sync with router/index.js)
const SECTION_TO_ROUTE = {
  'dashboard': '/dashboard',
  'new-entry': '/new-entry',
  'transactions': '/transactions',
  'payments': '/payments',
  'obligations': '/obligations',
  'karteles': '/karteles',
  'zip-export': '/documents',
  'reports': '/reports',
  'report-builder': '/report-builder',
  'ai-analysis': '/ai-analysis',
  'projects': '/projects',
  'admin': '/admin',
  'admin-categories': '/admin',
  'admin-accounts': '/admin',
  'admin-banks': '/admin',
  'admin-audit': '/admin',
}
// Preferred landing order when user has multiple allowed sections
const LANDING_PRIORITY = ['dashboard', 'transactions', 'new-entry', 'payments', 'obligations', 'karteles', 'reports', 'report-builder', 'ai-analysis', 'projects', 'zip-export', 'admin']
function resolveLandingRoute(user) {
  if (!user || !user.allowedSections) return '/dashboard'
  let allowed = null
  try { allowed = JSON.parse(user.allowedSections) } catch { return '/dashboard' }
  if (!Array.isArray(allowed) || allowed.length === 0) return '/dashboard'
  // Pick by priority: first match in LANDING_PRIORITY that user has
  for (const s of LANDING_PRIORITY) {
    if (allowed.includes(s) && SECTION_TO_ROUTE[s]) return SECTION_TO_ROUTE[s]
  }
  // Fallback: first allowed section regardless of priority
  for (const s of allowed) {
    if (SECTION_TO_ROUTE[s]) return SECTION_TO_ROUTE[s]
  }
  return '/dashboard'
}

const login = async () => {
  error.value = ''
  loading.value = true
  try {
    const res = await api.post('/api/auth/login', {
      username: username.value,
      password: password.value
    })
    if (res.data.success) {
      userStore.setUser(res.data.user, res.data.token)

      // M.6 + S87.16: Auto-select entity, but RESPECT the user's last
      // chosen entity if it is still among their allowed entities.
      // Previously this always forced entityIds[0] (= Next2Me), which
      // discarded the entity the user was working in (e.g. Next2Me Group)
      // every time they logged back in.
      const user = res.data.user
      if (user.entityIds && user.entityIds.length > 0) {
        // User has entity restriction - map UUID to key
        const uuidToKey = {
          '58202b71-4ddb-45c9-8e3c-39e816bde972': 'next2me',
          'dea1f32c-7b30-4981-b625-633da9dbe71e': 'house',
          '50317f44-9961-4fb4-add0-7a118e32dc14': 'next2megroup',
        }
        const allowedKeys = user.entityIds
          .map((id) => uuidToKey[id])
          .filter((k) => !!k)
        const stored = localStorage.getItem('n2c_entity')
        if (stored && allowedKeys.includes(stored)) {
          // Keep the user's last selection - do not overwrite.
        } else {
          const firstKey = allowedKeys[0]
          if (firstKey) {
            localStorage.setItem('n2c_entity', firstKey)
          }
        }
      }

      // PHASE_61_LANDING_ROUTE: route to first allowed section (not always /dashboard)
      router.push(resolveLandingRoute(user))
    } else {
      error.value = 'Λάθος username ή password'
    }
  } catch (err) {
    if (err.response?.status === 401) {
      error.value = 'Λάθος username ή password'
    } else {
      error.value = 'Σφάλμα σύνδεσης. Δοκιμάστε ξανά.'
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-logo"><svg class="login-logo-svg" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
        <rect width="100" height="100" rx="18" fill="#162B40"/>
        <ellipse cx="50" cy="42" rx="38" ry="14" fill="none" stroke="#FFFFFF" stroke-width="2" opacity="0.9" transform="rotate(-25 50 42)"/>
        <path d="M32 72 L45 22 Q50 8 55 22 L68 72 L60 72 L55 52 Q50 30 45 52 L40 72 Z" fill="#FFFFFF"/>
        <rect x="40" y="54" width="20" height="5" rx="1" fill="#FFFFFF"/>
        <path d="M22 48 Q36 32 50 24 Q64 16 78 18" fill="none" stroke="#FFFFFF" stroke-width="2.5" stroke-linecap="round" opacity="0.9"/>
        <path d="M74 14 L80 17 L74 22" fill="none" stroke="#FFFFFF" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" opacity="0.9"/>
      </svg></div>
      <h1 class="login-title">Next2Cash</h1>
      <p class="login-sub">Group Cash Control</p>
      <div class="login-form">
        <div class="form-group">
          <label>Username</label>
          <input v-model="username" type="text" placeholder="username" @keyup.enter="login" />
        </div>
        <div class="form-group">
          <label>Κωδικός</label>
          <input v-model="password" type="password" placeholder="••••••" @keyup.enter="login" />
        </div>
        <div class="error-msg" v-if="error">⚠ {{ error }}</div>
        <button class="login-btn" @click="login" :disabled="loading">
          {{ loading ? 'Σύνδεση...' : 'Σύνδεση' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page { min-height: 100vh; background: #0f1e2e; display: flex; align-items: center; justify-content: center; }
.login-card { background: #162B40; border-radius: 16px; padding: 48px 40px; width: 360px; text-align: center; border: 1px solid rgba(255,255,255,0.06); }
.login-logo { width: 56px; height: 56px; margin: 0 auto 16px; }
.login-logo-svg { width: 100%; height: 100%; display: block; border-radius: 12px; }
.login-title { color: #fff; font-size: 1.5rem; font-weight: 700; margin: 0 0 4px; }
.login-sub { color: #8899aa; font-size: 0.85rem; margin: 0 0 32px; }
.form-group { text-align: left; margin-bottom: 16px; }
.form-group label { display: block; color: #8899aa; font-size: 0.8rem; margin-bottom: 6px; }
.form-group input { width: 100%; background: #1e3448; border: 1px solid #2a4a6a; color: #e0e6ed; padding: 10px 14px; border-radius: 8px; font-size: 0.95rem; box-sizing: border-box; }
.form-group input:focus { outline: none; border-color: #4FC3A1; }
.error-msg { background: rgba(239,83,80,0.1); color: #ef5350; padding: 8px 12px; border-radius: 6px; font-size: 0.85rem; margin-bottom: 16px; }
.login-btn { width: 100%; background: #4FC3A1; color: #0f1e2e; border: none; padding: 12px; border-radius: 8px; font-size: 1rem; font-weight: 700; cursor: pointer; }
.login-btn:hover { background: #3db08e; }
.login-btn:disabled { opacity: 0.6; cursor: not-allowed; }
</style>
