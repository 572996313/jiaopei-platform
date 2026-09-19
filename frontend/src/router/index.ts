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
        component: () => import('@/views/system/UserManagementView.vue'),
      },
      {
        path: 'system/institution',
        name: 'system-institution',
        component: () => import('@/views/system/InstitutionProfileView.vue'),
      },
      {
        path: 'system/roles',
        name: 'system-roles',
        component: () => import('@/views/system/RoleManagementView.vue'),
      },
      {
        path: 'system/menus',
        name: 'system-menus',
        component: () => import('@/views/system/MenuManagementView.vue'),
      },
      {
        path: 'system/config',
        name: 'system-config',
        component: () => import('@/views/system/ConfigManagementView.vue'),
      },
      {
        path: 'system/dicts',
        name: 'system-dicts',
        component: () => import('@/views/system/DictManagementView.vue'),
      },
      {
        path: 'system/list-configs',
        name: 'system-list-configs',
        component: () => import('@/views/system/ListConfigManagementView.vue'),
      },
      {
        path: 'system/permission-overview',
        name: 'system-permission-overview',
        component: () => import('@/views/system/PermissionOverviewView.vue'),
      },
      {
        path: 'system/data-sources',
        name: 'system-data-sources',
        component: () => import('@/views/system/DataSourceManagementView.vue'),
      },
      {
        path: 'system/operlogs',
        name: 'system-operlogs',
        component: () => import('@/views/system/OperLogView.vue'),
      },
      {
        path: 'campus',
        name: 'campus',
        component: () => import('@/views/organization/CampusManagementView.vue'),
      },
      {
        path: 'time-slots',
        name: 'time-slots',
        component: () => import('@/views/organization/TimeSlotTemplateView.vue'),
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
        path: 'scheduling',
        name: 'scheduling',
        component: () => import('@/views/education/ScheduleManageView.vue'),
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
