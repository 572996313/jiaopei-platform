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
    // 第三方登录回调：票据在 #ticket=… 里，只做换票，不渲染业务内容
    path: '/oauth/callback',
    name: 'oauth-callback',
    meta: {
      public: true,
    },
    component: () => import('@cornerstone/core/views/auth/OauthCallbackView.vue'),
  },
  {
    // 同一第三方账号绑定多个系统账号时的选择页
    path: '/oauth/select',
    name: 'oauth-select',
    meta: {
      public: true,
    },
    component: () => import('@cornerstone/core/views/auth/OauthSelectView.vue'),
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
    // 已删除的「全部功能」整页路由，改成了悬浮面板（layouts/AllPagesDialog.vue）。
    // 留着这条重定向是为了旧书签、旧页签和旧浏览记录：指过来时落到首页，而不是空白内容区。
    // 不写死 /dashboard——落地页属于壳工程，core 只回到根路由由壳决定去哪。
    path: 'all-pages',
    redirect: '/',
  },
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
    path: 'system/schema-dictionary',
    name: 'system-schema-dictionary',
    component: () => import('@cornerstone/core/views/system/SchemaDictionaryView.vue'),
  },
  {
    path: 'system/endpoint-chain',
    name: 'system-endpoint-chain',
    component: () => import('@cornerstone/core/views/system/EndpointChainView.vue'),
  },
  {
    path: 'system/document-lineage',
    name: 'system-document-lineage',
    component: () => import('@cornerstone/core/views/system/DocumentLineageView.vue'),
  },
  {
    path: 'system/data-sources',
    name: 'system-data-sources',
    component: () => import('@cornerstone/core/views/system/DataSourceManagementView.vue'),
  },
  {
    path: 'system/auth-providers',
    name: 'system-auth-providers',
    component: () => import('@cornerstone/core/views/system/AuthProviderManagementView.vue'),
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
    path: 'system/files',
    name: 'system-files',
    component: () => import('@cornerstone/core/views/system/FileManagementView.vue'),
  },
  {
    path: 'system/navigation',
    name: 'system-navigation',
    component: () => import('@cornerstone/core/views/system/NavigationView.vue'),
  },
  {
    path: 'system/operlogs',
    name: 'system-operlogs',
    component: () => import('@cornerstone/core/views/system/OperLogView.vue'),
  },
]
