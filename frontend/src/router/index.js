import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard',      component: () => import('@/views/DashboardView.vue'),     meta: { section: 'dashboard', title: 'Πίνακας Ελέγχου' } },
    { path: '/new-entry',      component: () => import('@/views/NewEntryView.vue'),      meta: { section: 'new-entry', title: 'Νέα Καταχώριση' } },
    { path: '/transactions',   component: () => import('@/views/TransactionsView.vue'),  meta: { section: 'transactions', title: 'Κινήσεις' } },
    { path: '/payments',       component: () => import('@/views/PaymentsView.vue'),      meta: { section: 'payments', title: 'Πληρωμές' } },
    { path: '/obligations',    component: () => import('@/views/ObligationsView.vue'),   meta: { section: 'obligations', title: 'Υποχρεώσεις' } },
    { path: '/karteles',       component: () => import('@/views/KartelesView.vue'),      meta: { section: 'karteles', title: 'Καρτέλες' } },
  { path: '/recurring',     component: () => import('@/views/RecurringView.vue'),    meta: { section: 'recurring', title: 'Επαναλαμβανόμενες' } },
    { path: '/documents',    component: () => import('@/views/DocumentsView.vue'),    meta: { section: 'zip-export', title: 'Παραστατικά' } },
    { path: '/reports',        component: () => import('@/views/ReportsView.vue'),       meta: { section: 'reports', title: 'Αναφορές' } },
    { path: '/report-builder', component: () => import('@/views/ReportBuilderView.vue'), meta: { section: 'report-builder', title: 'Report Builder' } },
    { path: '/ai-analysis',    component: () => import('@/views/AiAnalysisView.vue'),    meta: { section: 'ai-analysis', title: 'AI Ανάλυση' } },
    { path: '/projects',       component: () => import('@/views/ProjectsView.vue'),       meta: { section: 'projects', title: 'Projects' } },
    { path: '/projects/:id',   component: () => import('@/views/ProjectDetailView.vue'), meta: { section: 'projects', title: 'Project Detail' } },
    { path: '/admin',          component: () => import('@/views/AdminView.vue'),         meta: { section: 'admin', title: 'Admin Panel' } },
    { path: '/investor-reports', component: () => import('@/views/ForecastView.vue'),   meta: { section: 'investor-reports', title: 'Investor Reports' } },
    { path: '/pricing-calculator', component: () => import('@/views/PricingCalculatorView.vue'),   meta: { section: 'pricing-calculator', title: 'Pricing Calculator' } },
  ]
})

// PHASE_61_SECTION_GUARD: enforce allowedSections on every navigation
// Section -> route map (kept in sync with LoginView.vue resolveLandingRoute)
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
  'investor-reports': '/investor-reports',
  'pricing-calculator': '/pricing-calculator',
  'admin-categories': '/admin',
  'admin-accounts': '/admin',
  'admin-banks': '/admin',
  'admin-audit': '/admin',
}
const LANDING_PRIORITY = ['dashboard', 'transactions', 'new-entry', 'payments', 'obligations', 'karteles', 'reports', 'report-builder', 'ai-analysis', 'projects', 'investor-reports', 'pricing-calculator', 'zip-export', 'admin']

function firstAllowedRoute(allowed) {
  if (!Array.isArray(allowed) || allowed.length === 0) return '/dashboard'
  for (const s of LANDING_PRIORITY) {
    if (allowed.includes(s) && SECTION_TO_ROUTE[s]) return SECTION_TO_ROUTE[s]
  }
  for (const s of allowed) {
    if (SECTION_TO_ROUTE[s]) return SECTION_TO_ROUTE[s]
  }
  return '/dashboard'
}

router.beforeEach((to) => {
  const userRaw = localStorage.getItem('n2c_user')
  // Public routes (e.g. /login): allow always
  if (to.meta.public) return
  // Authenticated routes: require user
  if (!userRaw) return '/login'
  // Section-level authorization
  let user = null
  try { user = JSON.parse(userRaw) } catch { return '/login' }
  // null/undefined allowedSections = full access (admin/legacy users)
  if (!user.allowedSections) return
  let allowed = null
  try { allowed = JSON.parse(user.allowedSections) } catch { return }
  if (!Array.isArray(allowed)) return
  // If route has no section meta, allow (e.g. redirects)
  const routeSection = to.meta && to.meta.section
  if (!routeSection) return
  // If section is allowed, proceed
  if (allowed.includes(routeSection)) return
  // Otherwise redirect to first allowed route
  const target = firstAllowedRoute(allowed)
  if (to.path === target) return // avoid infinite loop
  return target
})

export default router
