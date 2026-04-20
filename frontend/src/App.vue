<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api'
import { useUiStore } from '@/stores/ui'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const ui = useUiStore()
const userStore = useUserStore()

// M.6: Parse allowed sections from user profile
const userAllowedSections = computed(() => {
  const user = userStore.profile
  if (!user) return null // not logged in
  if (!user.allowedSections) return null // null = all sections allowed
  try {
    return JSON.parse(user.allowedSections)
  } catch {
    return null
  }
})

// M.6: Check if user has entity restrictions
const userEntityIds = computed(() => {
  const user = userStore.profile
  if (!user || !user.entityIds || user.entityIds.length === 0) return null // null = all entities
  return user.entityIds
})

// M.6: Filter nav sections based on allowed sections
const filteredNavSections = computed(() => {
  const allowed = userAllowedSections.value
  if (allowed === null) return navSections // null = show all

  return navSections
    .map(section => ({
      ...section,
      items: section.items.filter(item => {
        if (!item.section) return true // no section key = always show
        return allowed.includes(item.section)
      })
    }))
    .filter(section => section.items.length > 0) // remove empty sections
})

// M.6: Filter entities for restricted users
const filteredEntities = computed(() => {
  const restricted = userEntityIds.value
  if (restricted === null) return entities.value // null = all entities

  // Map entity UUIDs to keys
  const uuidToKey = {
    '58202b71-4ddb-45c9-8e3c-39e816bde972': 'next2me',
    'dea1f32c-7b30-4981-b625-633da9dbe71e': 'house',
    '50317f44-9961-4fb4-add0-7a118e32dc14': 'polaris',
  }
  const allowedKeys = restricted.map(uuid => uuidToKey[uuid]).filter(Boolean)
  return entities.value.filter(e => allowedKeys.includes(e.key))
})

// M.6: User display name for topbar
const userDisplayName = computed(() => {
  const user = userStore.profile
  if (!user) return 'User'
  return user.displayName || user.username || 'User'
})

// M.6: User initials for avatar
const userInitial = computed(() => {
  const name = userDisplayName.value
  return name.charAt(0).toUpperCase()
})

const logout = () => {
  localStorage.removeItem('n2c_user')
  router.push('/login')
}

const selectedEntity = ref(localStorage.getItem('n2c_entity') || 'next2me')
const entities = ref([
  { key: 'next2me', label: 'Next2Me' },
  { key: 'house',   label: 'House' },
  { key: 'polaris', label: 'Polaris' }
])
function selectEntity(e) {
  selectedEntity.value = e.key
  localStorage.setItem('n2c_entity', e.key)
  showEntityMenu.value = false
  window.dispatchEvent(new Event('entity-changed'))
}
const showEntityMenu = ref(false)

// Load entities from API (dynamic)
onMounted(async () => {
  try {
    const res = await api.get('/api/config/entities')
    if (res.data.success && res.data.data && res.data.data.length > 0) {
      entities.value = res.data.data.map(e => ({ key: e.code, label: e.name, id: e.id }))
    }
  } catch (e) {
    // Silent fallback to hardcoded defaults
  }
})
// M.6: Auto-select entity for restricted users (ACCOUNTANT/VIEWER)
if (filteredEntities.value.length === 1 && selectedEntity.value !== filteredEntities.value[0].key) {
  selectEntity(filteredEntities.value[0])
}

const navSections = [
  {
    label: 'ΞΞ¥Ξ΅Ξ™Ξ‘',
    items: [
      { to: '/dashboard', section: 'dashboard',     label: 'Ξ Ξ―Ξ½Ξ±ΞΊΞ±Ο‚ Ξ•Ξ»Ξ­Ξ³Ο‡ΞΏΟ…', icon: 'M3 12l9-9 9 9v9a2 2 0 0 1-2 2h-4v-7h-6v7H5a2 2 0 0 1-2-2z' },
      { to: '/new-entry', section: 'new-entry',     label: 'ΞΞ­Ξ± ΞΞ±Ο„Ξ±Ο‡ΟΟΞΉΟƒΞ·',  icon: 'M12 5v14M5 12h14' },
      { to: '/transactions', section: 'transactions',  label: 'ΞΞΉΞ½Ξ®ΟƒΞµΞΉΟ‚',         icon: 'M7 7h10M7 12h10M7 17h6M3 5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z' },
      { to: '/payments', section: 'payments',      label: 'Ξ Ξ»Ξ·ΟΟ‰ΞΌΞ­Ο‚',         icon: 'M2 8h20M2 12h20M5 16h4M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z' },
      { to: '/obligations', section: 'obligations',   label: 'Ξ¥Ο€ΞΏΟ‡ΟΞµΟΟƒΞµΞΉΟ‚',      icon: 'M12 8v4l3 2M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z' },
      { to: '/karteles', section: 'karteles',      label: 'ΞΞ±ΟΟ„Ξ­Ξ»ΞµΟ‚',         icon: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8zM14 2v6h6' },
    ]
  },
  {
    label: 'Ξ›ΞΞ“Ξ™Ξ£Ξ¤Ξ—Ξ΅Ξ™Ξ',
    items: [
      { to: '/documents', section: 'zip-export',   label: 'Ξ Ξ±ΟΞ±ΟƒΟ„Ξ±Ο„ΞΉΞΊΞ¬',     icon: 'M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z' },
    ]
  },
  {
    label: 'Ξ‘ΞΞ‘Ξ¦ΞΞ΅Ξ•Ξ£',
    items: [
      { to: '/reports', section: 'reports',        label: 'Ξ‘Ξ½Ξ±Ο†ΞΏΟΞ­Ο‚',        icon: 'M3 3v18h18M8 17V9M13 17V5M18 17v-7' },
      { to: '/report-builder', section: 'report-builder', label: 'Report Builder',  icon: 'M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7', badge: 'NEW' },
      { to: '/ai-analysis', section: 'ai-analysis',    label: 'AI Ξ‘Ξ½Ξ¬Ξ»Ο…ΟƒΞ·',      icon: 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 6v4l3 3', badge: 'NEW' },
    ]
  },
  {
    label: 'Ξ”Ξ™Ξ‘Ξ§Ξ•Ξ™Ξ΅Ξ™Ξ£Ξ—',
    items: [
      { to: '/admin', section: 'admin',          label: 'Admin Panel',     icon: 'M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0' },
    ]
  }
]

const currentTitle = computed(() => route.meta?.title || 'Next2Cash')
</script>

<template>
  <div class="layout" :class="{ 'sidebar-collapsed': ui.sidebarCollapsed }">
    <aside class="sidebar">
      <div class="sidebar__brand">
        <div class="sidebar__logo">A</div>
        <div class="sidebar__brand-text" v-if="!ui.sidebarCollapsed">
          <div class="sidebar__brand-title">CashControl</div>
          <div class="sidebar__brand-sub">{{ entities.find(e => e.key === selectedEntity)?.label || 'Next2Me' }}</div>
        </div>
      </div>

      <div class="entity-selector" v-if="!ui.sidebarCollapsed && filteredEntities.length > 1">
        <div class="entity-btn" @click="showEntityMenu = !showEntityMenu">
          <span>{{ entities.find(e => e.key === selectedEntity)?.label || 'Next2Me' }}</span>
          <span>β–Ύ</span>
        </div>
        <div class="entity-menu" v-if="showEntityMenu">
          <div v-for="e in filteredEntities" :key="e.key" class="entity-option" :class="{ active: e.key === selectedEntity }" @click="selectEntity(e)">{{ e.label }}</div>
        </div>
      </div>

      <nav class="sidebar__nav">
        <template v-for="section in filteredNavSections" :key="section.label">
          <div class="nav-section-label" v-if="!ui.sidebarCollapsed">{{ section.label }}</div>
          <RouterLink v-for="item in section.items" :key="item.to" :to="item.to" class="nav-item" active-class="nav-item--active">
            <svg class="nav-item__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path :d="item.icon" />
            </svg>
            <span v-if="!ui.sidebarCollapsed" class="nav-item__label">{{ item.label }}</span>
            <span v-if="item.badge && !ui.sidebarCollapsed" class="nav-badge">{{ item.badge }}</span>
          </RouterLink>
        </template>
      </nav>

      <div class="sidebar__bottom">
        <button class="logout-btn" @click="logout" :title="ui.sidebarCollapsed ? 'Ξ‘Ο€ΞΏΟƒΟΞ½Ξ΄ΞµΟƒΞ·' : ''">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" class="logout-icon">
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
            <polyline points="16 17 21 12 16 7" />
            <line x1="21" y1="12" x2="9" y2="12" />
          </svg>
          <span v-if="!ui.sidebarCollapsed">Ξ‘Ο€ΞΏΟƒΟΞ½Ξ΄ΞµΟƒΞ·</span>
        </button>
      </div>
      <button class="sidebar__collapse" type="button" @click="ui.toggleSidebar()">
        <span>{{ ui.sidebarCollapsed ? 'β€Ί' : 'β€Ή' }}</span>
      </button>
    </aside>

    <div class="main">
      <header class="topbar">
        <h1 class="topbar__title">{{ currentTitle }}</h1>
        <div class="topbar__user">
          <span class="topbar__user-name">{{ userDisplayName }}</span>
          <div class="topbar__avatar">{{ userInitial }}</div>
        </div>
      </header>
      <main class="content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout { display: grid; grid-template-columns: var(--sidebar-width) 1fr; height: 100vh; overflow: hidden; transition: grid-template-columns 180ms ease; }
.layout.sidebar-collapsed { grid-template-columns: var(--sidebar-width-collapsed) 1fr; }
.sidebar { position: relative; background: #162B40; color: var(--text-primary); display: flex; flex-direction: column; border-right: 1px solid rgba(255,255,255,0.04); overflow: hidden; height: 100vh; }
.sidebar__brand { display: flex; align-items: center; gap: 12px; padding: 18px 18px 16px; border-bottom: 1px solid rgba(255,255,255,0.06); }
.sidebar__logo { flex: 0 0 36px; height: 36px; border-radius: 8px; background: linear-gradient(135deg, #4FC3A1, #2FA585); display: grid; place-items: center; font-weight: 700; color: #0f1e2e; font-size: 16px; }
.sidebar__brand-title { font-weight: 700; font-size: 15px; color: #fff; }
.sidebar__brand-sub { font-size: 11px; color: #8899aa; margin-top: 2px; }
.entity-selector { padding: 10px 12px; position: relative; }
.entity-btn { background: #1e3448; border: 1px solid #2a4a6a; border-radius: 6px; padding: 8px 12px; color: #e0e6ed; font-size: 13px; cursor: pointer; display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.entity-menu { position: absolute; top: 100%; left: 12px; right: 12px; background: #1e3448; border: 1px solid #2a4a6a; border-radius: 6px; z-index: 100; }
.entity-option { padding: 8px 12px; cursor: pointer; font-size: 13px; color: #e0e6ed; }
.entity-option:hover { background: #2a4a6a; }
.entity-option.active { color: #4FC3A1; font-weight: 600; }
.nav-section-label { padding: 12px 12px 4px; font-size: 0.65rem; color: #556677; letter-spacing: 1.5px; font-weight: 600; }
.sidebar__nav { display: flex; flex-direction: column; gap: 1px; padding: 8px 10px; flex: 1; overflow-y: auto; }
.nav-item { display: flex; align-items: center; gap: 12px; padding: 10px 12px; border-radius: 8px; color: #8899aa; font-size: 14px; font-weight: 500; transition: all 120ms ease; white-space: nowrap; text-decoration: none; }
.nav-item:hover { background: rgba(255,255,255,0.04); color: #e0e6ed; }
.nav-item--active { background: rgba(79,195,161,0.12); color: #4FC3A1; }
.nav-item__icon { width: 20px; height: 20px; flex-shrink: 0; }
.nav-item__label { overflow: hidden; text-overflow: ellipsis; flex: 1; }
.nav-badge { background: #4FC3A1; color: #0f1e2e; font-size: 0.6rem; font-weight: 700; padding: 2px 5px; border-radius: 4px; }
.sidebar__bottom { padding: 8px 10px; border-top: 1px solid rgba(255,255,255,0.06); }
.logout-btn { display: flex; align-items: center; gap: 10px; width: 100%; padding: 10px 12px; border-radius: 8px; background: none; border: none; color: #8899aa; font-size: 14px; font-weight: 500; cursor: pointer; transition: all 120ms ease; }
.logout-btn:hover { background: rgba(239,83,80,0.1); color: #ef5350; }
.logout-icon { width: 18px; height: 18px; flex-shrink: 0; }
.sidebar__collapse { margin: 8px 12px; padding: 8px; background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.06); color: #8899aa; border-radius: 6px; font-size: 16px; cursor: pointer; }
.main { display: flex; flex-direction: column; min-width: 0; height: 100vh; overflow: hidden; }
.topbar { height: var(--topbar-height); display: flex; align-items: center; justify-content: space-between; padding: 0 24px; background: #0d1e2e; border-bottom: 1px solid #1e3448; }
.topbar__title { font-size: 16px; font-weight: 600; margin: 0; color: #e0e6ed; }
.topbar__user { display: flex; align-items: center; gap: 10px; }
.topbar__user-name { font-size: 13px; color: #8899aa; }
.topbar__avatar { width: 32px; height: 32px; border-radius: 50%; background: #1e3448; color: #e0e6ed; display: grid; place-items: center; font-size: 13px; font-weight: 600; }
.content { flex: 1; overflow: auto; background: #0d1e2e; }
</style>
