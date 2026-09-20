import { createRouter, createWebHistory } from 'vue-router'
import { store } from '@/stores'
import { useAuthStore } from '@/stores/auth'
import { publicRoutes, platformChildRoutes } from './platform-routes'
import { businessChildRoutes } from './business-routes'

// 路由组装：底座路由（platform-routes）+ 各业务模块路由清单（business-routes）。
// 新增业务模块时在 business-routes.ts 中追加对应清单，底座部分零改动。
const routes = [
  ...publicRoutes,
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    redirect: '/dashboard',
    children: [...platformChildRoutes, ...businessChildRoutes],
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to) => {
  const auth = useAuthStore(store)

  if (!auth.initialized) {
    await auth.bootstrap()
  }

  if (to.meta.public) {
    if (auth.isAuthenticated) {
      if (auth.mustChangePassword) {
        return '/change-password'
      }
      return typeof to.query.redirect === 'string' ? to.query.redirect : '/dashboard'
    }
    return true
  }

  if (!auth.isAuthenticated) {
    return {
      path: '/login',
      query: {
        redirect: to.fullPath,
      },
    }
  }

  if (auth.mustChangePassword && !to.meta.allowForceChange) {
    return {
      path: '/change-password',
      query: {
        redirect: to.fullPath,
      },
    }
  }

  return true
})
