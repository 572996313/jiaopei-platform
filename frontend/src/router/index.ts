import { createRouter, createWebHistory } from 'vue-router'
import { store } from '@/stores'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/login',
    name: 'login',
    meta: {
      public: true,
    },
    component: () => import('@/views/auth/LoginView.vue'),
  },
  {
    path: '/change-password',
    name: 'change-password',
    meta: {
      allowForceChange: true,
    },
    component: () => import('@/views/auth/ChangePasswordView.vue'),
  },
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
      },
      {
        path: 'system',
        redirect: '/system/users',
      },
      {
        path: 'system/users',
        name: 'system-users',
        meta: {
          systemTab: 'users',
        },
        component: () => import('@/views/system/SystemManagementView.vue'),
      },
      {
        path: 'system/institution',
        name: 'system-institution',
        meta: {
          systemTab: 'institution',
        },
        component: () => import('@/views/system/SystemManagementView.vue'),
      },
      {
        path: 'system/roles',
        name: 'system-roles',
        meta: {
          systemTab: 'roles',
        },
        component: () => import('@/views/system/SystemManagementView.vue'),
      },
      {
        path: 'system/menus',
        name: 'system-menus',
        meta: {
          systemTab: 'menus',
        },
        component: () => import('@/views/system/SystemManagementView.vue'),
      },
      {
        path: 'system/config',
        name: 'system-config',
        meta: {
          systemTab: 'configs',
        },
        component: () => import('@/views/system/SystemManagementView.vue'),
      },
      {
        path: 'system/dicts',
        name: 'system-dicts',
        meta: {
          systemTab: 'dicts',
        },
        component: () => import('@/views/system/SystemManagementView.vue'),
      },
      {
        path: 'system/list-configs',
        name: 'system-list-configs',
        meta: {
          systemTab: 'listConfigs',
        },
        component: () => import('@/views/system/SystemManagementView.vue'),
      },
      {
        path: 'campus',
        name: 'campus',
        component: () => import('@/views/organization/CampusManagementView.vue'),
      },
      {
        path: 'teachers',
        name: 'teachers',
        component: () => import('@/views/education/TeacherManagementView.vue'),
      },
      {
        path: 'students',
        name: 'students',
        component: () => import('@/views/education/StudentManagementView.vue'),
      },
      {
        path: 'classes',
        name: 'classes',
        component: () => import('@/views/education/ClassManagementView.vue'),
      },
      {
        path: 'schedule',
        name: 'schedule',
        component: () => import('@/views/education/ScheduleAttendanceView.vue'),
      },
    ],
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
