<template>
  <div class="app-shell">
    <aside class="app-sidebar">
      <div class="brand-block">
        <div class="brand-kicker">教培管理平台</div>
        <div class="brand-title">教培管理平台</div>
      </div>

      <div class="sidebar-summary">
        <div class="sidebar-summary-title">今日优先级</div>
        <div class="sidebar-summary-text">先看工作台，再处理排课、点名、请假与变动。</div>
      </div>

      <nav class="nav-groups">
        <section v-for="group in navGroups" :key="group.key" class="nav-group">
          <div class="nav-group-title">{{ group.label }}</div>
          <div class="nav-list">
            <RouterLink
              v-for="item in group.items"
              :key="item.key"
              :to="item.to"
              class="nav-item"
              :class="{ 'is-active': isNavItemActive(item) }"
            >
              <span class="nav-item-label">{{ item.label }}</span>
              <span v-if="item.hint" class="nav-item-hint">{{ item.hint }}</span>
            </RouterLink>
          </div>
        </section>
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
          <div class="header-institution-block">
            <el-tag v-if="institutionStore.isSingleInstitution" type="info" effect="plain" size="large">
              {{ institutionStore.currentInstitutionName }}
            </el-tag>
            <el-select
              v-else-if="institutionStore.hasInstitutions"
              :model-value="institutionStore.currentInstitutionId"
              placeholder="选择机构"
              filterable
              class="header-institution-select"
              @change="institutionStore.setInstitution($event as string)"
            >
              <el-option
                v-for="item in institutionStore.institutions"
                :key="item.id"
                :label="item.orgName || item.shortName || item.orgCode || `机构 #${item.id}`"
                :value="item.id"
              />
            </el-select>
            <el-tag v-else type="warning" effect="plain" size="large">未选择机构</el-tag>
          </div>
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
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useInstitutionStore } from '@/stores/institution'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const institutionStore = useInstitutionStore()

onMounted(() => {
  institutionStore.loadInstitutions()
})

interface SidebarDefinition {
  key: string
  to: string
  label: string
  title: string
  group: 'workbench' | 'daily' | 'archive' | 'class' | 'analysis' | 'system'
  hint?: string
  prefixes: string[]
  alwaysVisible?: boolean
  menuSignals?: string[]
  permissionSignals?: string[]
}

const sidebarDefinitions: SidebarDefinition[] = [
  {
    key: 'dashboard',
    to: '/dashboard',
    label: '今日教务',
    title: '今日教务工作台',
    group: 'workbench',
    hint: '先处理今日待办',
    prefixes: ['/dashboard'],
    alwaysVisible: true,
  },
  {
    key: 'schedule',
    to: '/schedule',
    label: '排课与执行',
    title: '排课与执行中心',
    group: 'daily',
    hint: '排课、点名、结课',
    prefixes: ['/schedule'],
    alwaysVisible: true,
  },
  {
    key: 'students',
    to: '/students',
    label: '报名与入班',
    title: '报名与入班',
    group: 'daily',
    hint: '处理新增与报名关系',
    prefixes: ['/students'],
    alwaysVisible: true,
  },
  {
    key: 'classes',
    to: '/classes',
    label: '班级学员',
    title: '班级学员',
    group: 'class',
    hint: '调整在班关系',
    prefixes: ['/classes'],
    alwaysVisible: true,
  },
  {
    key: 'campus',
    to: '/campus',
    label: '机构与教学资源',
    title: '机构与教学资源',
    group: 'archive',
    hint: '校区、教室、学期、时间段',
    prefixes: ['/campus'],
    alwaysVisible: true,
  },
  {
    key: 'teachers',
    to: '/teachers',
    label: '教师档案',
    title: '教师档案',
    group: 'archive',
    hint: '教师基础档案',
    prefixes: ['/teachers'],
    alwaysVisible: true,
  },
  {
    key: 'system',
    to: '/system/users',
    label: '系统设置',
    title: '系统设置',
    group: 'system',
    hint: '用户、角色、权限、字典',
    prefixes: ['/system'],
    menuSignals: ['system', 'system_users', 'system_roles', 'system_menus', 'system_config', 'system_dicts', '/system', '/system/users', '/system/roles', '/system/menus', '/system/config', '/system/dicts'],
    permissionSignals: ['system:user:manage', 'system:role:manage', 'system:menu:manage', 'system:config:read', 'system:config:write', 'system:dict:manage'],
  },
]

const navGroupMeta = [
  { key: 'workbench', label: '工作台' },
  { key: 'daily', label: '日常教务' },
  { key: 'class', label: '班级与教学' },
  { key: 'archive', label: '档案中心' },
  { key: 'analysis', label: '数据回查' },
  { key: 'system', label: '系统设置' },
] as const

const navItems = computed(() => {
  return sidebarDefinitions.filter((item) => {
    if (item.alwaysVisible) {
      return true
    }
    return auth.hasMenuAccess(item.menuSignals || []) || (item.permissionSignals || []).some((permission: string) => auth.hasPermission(permission))
  })
})

const navGroups = computed(() => {
  return navGroupMeta
    .map((group) => ({
      key: group.key,
      label: group.label,
      items: navItems.value.filter((item) => item.group === group.key),
    }))
    .filter((group) => group.items.length > 0)
})

const currentTitle = computed(() => {
  return sidebarDefinitions.find((item) => item.prefixes.some((prefix) => route.path.startsWith(prefix)))?.title ?? '教培管理平台'
})

function isNavItemActive(item: SidebarDefinition) {
  return item.prefixes.some((prefix) => route.path.startsWith(prefix))
}

async function handleLogout() {
  await auth.logout()
  institutionStore.reset()
  router.push('/login')
}
</script>
