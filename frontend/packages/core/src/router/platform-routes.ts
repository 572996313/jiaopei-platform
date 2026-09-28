import type { RouteRecordRaw } from 'vue-router'

/**
 * 底座路由：登录、改密与系统管理。
 * 不含工作台首页——首页内容属于具体项目，由壳工程在自己的清单里登记。
 */
export const publicRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    meta: {
      public: true,
    },
    component: () => import('@cornerstone/core/views/auth/LoginView.vue'),
  },
  {
    path: '/register',
    name: 'register',
    meta: {
      public: true,
    },
    component: () => import('@cornerstone/core/views/auth/RegisterView.vue'),
  },
  {
    path: '/activate',
    name: 'activate',
    meta: {
      public: true,
    },
    component: () => import('@cornerstone/core/views/auth/ActivateView.vue'),
  },
  {
    path: '/change-password',
    name: 'change-password',
    meta: {
      allowForceChange: true,
    },
    component: () => import('@cornerstone/core/views/auth/ChangePasswordView.vue'),
  },
]

export const platformChildRoutes: RouteRecordRaw[] = [
  {
    path: 'system',
    redirect: '/system/users',
  },
  {
    path: 'system/users',
    name: 'system-users',
    component: () => import('@cornerstone/core/views/system/UserManagementView.vue'),
  },
  {
    path: 'system/institution',
    name: 'system-institution',
    component: () => import('@cornerstone/core/views/system/InstitutionProfileView.vue'),
  },
  {
    path: 'system/roles',
    name: 'system-roles',
    component: () => import('@cornerstone/core/views/system/RoleManagementView.vue'),
  },
  {
    path: 'system/menus',
    name: 'system-menus',
    component: () => import('@cornerstone/core/views/system/MenuManagementView.vue'),
  },
  {
    path: 'system/config',
    name: 'system-config',
    component: () => import('@cornerstone/core/views/system/ConfigManagementView.vue'),
  },
  {
    path: 'system/dicts',
    name: 'system-dicts',
    component: () => import('@cornerstone/core/views/system/DictManagementView.vue'),
  },
  {
    path: 'system/list-configs',
    name: 'system-list-configs',
    component: () => import('@cornerstone/core/views/system/ListConfigManagementView.vue'),
  },
  {
    path: 'system/permission-overview',
    name: 'system-permission-overview',
    component: () => import('@cornerstone/core/views/system/PermissionOverviewView.vue'),
  },
  {
    path: 'system/data-sources',
    name: 'system-data-sources',
    component: () => import('@cornerstone/core/views/system/DataSourceManagementView.vue'),
  },
  {
    path: 'system/invitations',
    name: 'system-invitations',
    component: () => import('@cornerstone/core/views/system/InvitationManagementView.vue'),
  },
  {
    path: 'system/code-rules',
    name: 'system-code-rules',
    component: () => import('@cornerstone/core/views/system/CodeRuleManagementView.vue'),
  },
  {
    path: 'system/operlogs',
    name: 'system-operlogs',
    component: () => import('@cornerstone/core/views/system/OperLogView.vue'),
  },
]
