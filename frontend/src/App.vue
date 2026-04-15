<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUiStore } from '@/stores/ui'

const route = useRoute()
const ui = useUiStore()

const navItems = [
  { to: '/dashboard',    label: 'Dashboard',    icon: 'M3 12l9-9 9 9v9a2 2 0 0 1-2 2h-4v-7h-6v7H5a2 2 0 0 1-2-2z' },
  { to: '/transactions', label: 'Transactions', icon: 'M7 7h10M7 12h10M7 17h6M3 5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z' },
  { to: '/payments',     label: 'Payments',     icon: 'M2 8h20M2 12h20M5 16h4M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z' },
  { to: '/obligations',  label: 'Obligations',  icon: 'M12 8v4l3 2M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z' },
  { to: '/documents',    label: 'Documents',    icon: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8zM14 2v6h6M8 13h8M8 17h8M8 9h2' },
  { to: '/reports',      label: 'Reports',      icon: 'M3 3v18h18M8 17V9M13 17V5M18 17v-7' },
  { to: '/admin',        label: 'Admin',        icon: 'M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0' }
]

const currentTitle = computed(() => route.meta?.title || 'Next2Cash')
</script>

<template>
  <div class="layout" :class="{ 'sidebar-collapsed': ui.sidebarCollapsed }">
    <aside class="sidebar">
      <div class="sidebar__brand">
        <div class="sidebar__logo">N2</div>
        <div class="sidebar__brand-text" v-if="!ui.sidebarCollapsed">
          <div class="sidebar__brand-title">Next2Cash</div>
          <div class="sidebar__brand-sub">Group Cash Control</div>
        </div>
      </div>

      <nav class="sidebar__nav">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="nav-item"
          active-class="nav-item--active"
        >
          <svg
            class="nav-item__icon"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path :d="item.icon" />
          </svg>
          <span v-if="!ui.sidebarCollapsed" class="nav-item__label">{{ item.label }}</span>
        </RouterLink>
      </nav>

      <button
        class="sidebar__collapse"
        type="button"
        :title="ui.sidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'"
        @click="ui.toggleSidebar()"
      >
        <span aria-hidden="true">{{ ui.sidebarCollapsed ? '›' : '‹' }}</span>
      </button>
    </aside>

    <div class="main">
      <header class="topbar">
        <h1 class="topbar__title">{{ currentTitle }}</h1>
        <div class="topbar__user">
          <span class="topbar__user-name">Guest</span>
          <div class="topbar__avatar">G</div>
        </div>
      </header>

      <main class="content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: var(--sidebar-width) 1fr;
  min-height: 100vh;
  transition: grid-template-columns 180ms ease;
}

.layout.sidebar-collapsed {
  grid-template-columns: var(--sidebar-width-collapsed) 1fr;
}

/* ---------- Sidebar ---------- */
.sidebar {
  position: relative;
  background: #162B40;
  color: var(--text-primary);
  display: flex;
  flex-direction: column;
  border-right: 1px solid rgba(255, 255, 255, 0.04);
  overflow: hidden;
}

.sidebar__brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 18px 22px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.sidebar__logo {
  flex: 0 0 36px;
  height: 36px;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--accent), #2FA585);
  display: grid;
  place-items: center;
  font-weight: 700;
  color: #0f1e2e;
  font-size: 14px;
  letter-spacing: 0.5px;
}

.sidebar__brand-title {
  font-weight: 600;
  font-size: 15px;
  color: var(--text-primary);
}

.sidebar__brand-sub {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
}

.sidebar__nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 12px 10px;
  flex: 1;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  transition: background-color 120ms ease, color 120ms ease;
  white-space: nowrap;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.04);
  color: var(--text-primary);
}

.nav-item--active {
  background: rgba(79, 195, 161, 0.12);
  color: var(--accent);
}

.nav-item--active .nav-item__icon {
  color: var(--accent);
}

.nav-item__icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
}

.nav-item__label {
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar__collapse {
  margin: 12px;
  padding: 8px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  color: var(--text-secondary);
  border-radius: 6px;
  font-size: 16px;
  line-height: 1;
  transition: background-color 120ms ease;
}

.sidebar__collapse:hover {
  background: rgba(255, 255, 255, 0.08);
  color: var(--text-primary);
}

/* ---------- Main ---------- */
.main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  height: var(--topbar-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-subtle);
}

.topbar__title {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
}

.topbar__user {
  display: flex;
  align-items: center;
  gap: 10px;
}

.topbar__user-name {
  font-size: 13px;
  color: var(--text-muted);
}

.topbar__avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--navy-800);
  color: var(--text-primary);
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 600;
}

.content {
  padding: 24px;
  flex: 1;
  overflow: auto;
}
</style>
