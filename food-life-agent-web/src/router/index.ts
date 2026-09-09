import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../layouts/AppLayout.vue'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
    },
    {
      path: '/',
      component: AppLayout,
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('../views/HomeView.vue'),
        },
        {
          path: 'shops/:id',
          name: 'shop-detail',
          component: () => import('../views/ShopDetailView.vue'),
        },
        {
          path: 'orders',
          name: 'orders',
          component: () => import('../views/OrdersView.vue'),
        },
        {
          path: 'operations/audit',
          name: 'operation-audit',
          component: () => import('../views/OperationAuditView.vue'),
          meta: {
            requiresOperator: true,
          },
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.name !== 'login' && !auth.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.token) {
    return { name: 'home' }
  }
  if (to.meta.requiresOperator) {
    if (!auth.user) {
      await auth.fetchMe().catch(() => auth.logout())
    }
    const role = auth.user?.role?.toUpperCase()
    if (role !== 'ADMIN' && role !== 'OPERATOR') {
      return { name: 'home' }
    }
  }
  return true
})

export default router
