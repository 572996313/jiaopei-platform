<template>
  <div class="app-shell">
    <aside class="app-sidebar">
      <div class="brand-block">
        <div class="brand-kicker">JIAOPEI PLATFORM</div>
        <div class="brand-title">教培管理平台</div>
      </div>

      <nav class="nav-list">
        <RouterLink
          v-for="item in navItems"
          :key="item.key"
          :to="item.to"
          class="nav-item"
          :class="{ 'is-active': isNavItemActive(item) }"
        >
          {{ item.label }}
        </RouterLink>
      </nav>
    </aside>

    <div class="app-main">
      <el-alert
        v-if="auth.currentUser?.passwordResetRequired || auth.mustChangePassword"
        class="app-alert"
        :type="auth.mustChangePassword ? 'error' : 'warning'"
        show-icon
        :closable="false"
        :title="auth.mustChangePassword ? '当前账号已被强制跳转至改密流程，完成修改前不能进入业务首页。' : '当前账号仍处于初始化密码状态，请尽快完成改密。'"
      />

      <header class="app-header">
        <div>
          <div class="page-kicker">线下班课教务中台</div>
          <h1 class="page-title">{{ currentTitle }}</h1>
        </div>
        <div class="header-actions">
          <div class="header-user-block">
            <div class="header-user-name">{{ auth.displayName }}</div>
            <div class="header-user-meta">
              {{ auth.currentUser?.username || 'guest' }} / {{ auth.currentUser?.roles?.join(', ') || '未分配角色' }}
            </div>
          </div>
          <el-button @click="router.push('/change-password')">修改密码</el-button>
          <el-button @click="handleLogout">退出登录</el-button>
        </div>
      </header>

      <main class="app-content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

interface SidebarDefinition {
  key: string
  to: string
  label: string
  title: string
  prefixes: string[]
  alwaysVisible?: boolean
  menuSignals?: string[]
  permissionSignals?: string[]
}

const sidebarDefinitions: SidebarDefinition[] = [
  {
    key: 'dashboard',
    to: '/dashboard',
    label: '工作台',
    title: '首版项目骨架',
    prefixes: ['/dashboard'],
    alwaysVisible: true,
  },
  {
    key: 'system',
    to: '/system/users',
    label: '系统管理',
    title: '系统管理',
    prefixes: ['/system'],
    menuSignals: ['system', 'system_users', 'system_roles', 'system_menus', 'system_dicts', '/system', '/system/users', '/system/roles', '/system/menus', '/system/dicts'],
    permissionSignals: ['system:user:manage', 'system:role:manage', 'system:menu:manage', 'system:dict:manage'],
  },
  {
    key: 'campus',
    to: '/campus',
    label: '校区管理',
    title: '校区管理',
    prefixes: ['/campus'],
    alwaysVisible: true,
  },
  {
    key: 'teachers',
    to: '/teachers',
    label: '教师管理',
    title: '教师管理',
    prefixes: ['/teachers'],
    alwaysVisible: true,
  },
  {
    key: 'students',
    to: '/students',
    label: '学员管理',
    title: '学员管理',
    prefixes: ['/students'],
    alwaysVisible: true,
  },
  {
    key: 'classes',
    to: '/classes',
    label: '班级管理',
    title: '班级管理',
    prefixes: ['/classes'],
    alwaysVisible: true,
  },
  {
    key: 'schedule',
    to: '/schedule',
    label: '排课与考勤',
    title: '排课与考勤',
    prefixes: ['/schedule'],
    alwaysVisible: true,
  },
]

const navItems = computed(() => {
  return sidebarDefinitions.filter((item) => {
    if (item.alwaysVisible) {
      return true
    }
    return auth.hasMenuAccess(item.menuSignals || []) || (item.permissionSignals || []).some((permission: string) => auth.hasPermission(permission))
  })
})

const currentTitle = computed(() => {
  return sidebarDefinitions.find((item) => item.prefixes.some((prefix) => route.path.startsWith(prefix)))?.title ?? '教培管理平台'
})

function isNavItemActive(item: SidebarDefinition) {
  return item.prefixes.some((prefix) => route.path.startsWith(prefix))
}

async function handleLogout() {
  await auth.logout()
  router.push('/login')
}
</script>
