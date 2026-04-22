import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard',      component: () => import('@/views/DashboardView.vue'),     meta: { title: 'Πίνακας Ελέγχου' } },
    { path: '/new-entry',      component: () => import('@/views/NewEntryView.vue'),      meta: { title: 'Νέα Καταχώριση' } },
    { path: '/transactions',   component: () => import('@/views/TransactionsView.vue'),  meta: { title: 'Κινήσεις' } },
    { path: '/payments',       component: () => import('@/views/PaymentsView.vue'),      meta: { title: 'Πληρωμές' } },
    { path: '/obligations',    component: () => import('@/views/ObligationsView.vue'),   meta: { title: 'Υποχρεώσεις' } },
    { path: '/karteles',       component: () => import('@/views/KartelesView.vue'),      meta: { title: 'Καρτέλες' } },
    { path: '/documents',    component: () => import('@/views/DocumentsView.vue'),    meta: { title: 'Παραστατικά' } },
    { path: '/reports',        component: () => import('@/views/ReportsView.vue'),       meta: { title: 'Αναφορές' } },
    { path: '/report-builder', component: () => import('@/views/ReportBuilderView.vue'), meta: { title: 'Report Builder' } },
    { path: '/ai-analysis',    component: () => import('@/views/AiAnalysisView.vue'),    meta: { title: 'AI Ανάλυση' } },
    { path: '/admin',          component: () => import('@/views/AdminView.vue'),         meta: { title: 'Admin Panel' } },
  ]
})

router.beforeEach((to) => {
  const user = localStorage.getItem('n2c_user')
  if (!to.meta.public && !user) {
    return '/login'
  }
})

export default router
