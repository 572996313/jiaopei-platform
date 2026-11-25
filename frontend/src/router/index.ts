import { createRouter, createWebHistory } from 'vue-router'
import { store } from '@cornerstone/core/stores'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { publicRoutes, platformChildRoutes } from '@cornerstone/core/router/platform-routes'
import { projectChildRoutes } from '@/modules'

// 路由组装：底座路由（@cornerstone/core）+ 壳工程装配清单（@/modules 的项目页与业务模块路由）。
// 新增业务模块时在 modules.ts 追加对应清单，底座部分零改动。
const routes = [
  ...publicRoutes,
  {
    path: '/',
    component: () => import('@cornerstone/core/layouts/AppLayout.vue'),
    redirect: '/dashboard',
    children: [...platformChildRoutes, ...projectChildRoutes],
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
      if (typeof to.query.redirect === 'string') {
        return to.query.redirect
      }
      // 绑定第三方账号的回调落在 /login?identityBound=…，带着标记回工作台让布局层提示
      if (typeof to.query.identityBound === 'string' && to.query.identityBound) {
        return { path: '/dashboard', query: { identityBound: to.query.identityBound } }
      }
      return '/dashboard'
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
