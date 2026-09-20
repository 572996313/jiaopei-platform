import type { RouteRecordRaw } from 'vue-router'

/**
 * 底座路由：登录、改密、工作台与系统管理。
 * 这部分不随业务方向（教培/其他行业）变化，阶段三将随 @cornerstone/core 一起下沉为底座包。
 */
export const publicRoutes: RouteRecordRaw[] = [
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
]

export const platformChildRoutes: RouteRecordRaw[] = [
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
]
