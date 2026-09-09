<template>
  <div class="app-shell">
    <aside class="app-sidebar">
      <div class="brand-block">
        <div class="brand-kicker">线下班课教务中台</div>
        <div class="brand-title">教培管理平台</div>
      </div>

      <nav class="nav-groups">
        <section v-for="group in navGroups" :key="group.key" class="nav-group">
          <div class="nav-group-title">{{ group.label }}</div>
          <div class="nav-list">
            <template v-for="item in group.items" :key="item.key">
              <div v-if="item.children?.length" class="nav-parent">
                <button
                  type="button"
                  class="nav-item nav-toggle"
                  :class="{ 'is-active': isNavItemActive(item), 'is-open': isGroupOpen(item) }"
                  @click="toggleGroup(item.key)"
                >
                  <el-icon v-if="item.icon" class="nav-item-icon"><component :is="item.icon" /></el-icon>
                  <span class="nav-item-label">{{ item.label }}</span>
                  <el-icon class="nav-caret"><ArrowDown /></el-icon>
                </button>
                <div v-show="isGroupOpen(item)" class="nav-children">
                  <RouterLink
                    v-for="child in visibleChildren(item)"
                    :key="child.key"
                    :to="child.to"
                    class="nav-item nav-child-item"
                    :class="{ 'is-active': isChildActive(child) }"
                  >
                    <span class="nav-item-label">{{ child.label }}</span>
                  </RouterLink>
                </div>
              </div>
              <RouterLink
                v-else
                :to="item.to"
                class="nav-item"
                :class="{ 'is-active': isNavItemActive(item) }"
              >
                <el-icon v-if="item.icon" class="nav-item-icon"><component :is="item.icon" /></el-icon>
                <span class="nav-item-label">{{ item.label }}</span>
              </RouterLink>
            </template>
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
        <h1 class="page-title">{{ currentTitle }}</h1>
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
          <el-dropdown class="header-user-dropdown" trigger="click" @command="handleUserCommand">
            <div class="header-user-block">
              <span class="header-user-avatar">{{ auth.displayName.slice(0, 1) }}</span>
              <div class="header-user-info">
                <div class="header-user-name">{{ auth.displayName }}</div>
                <div class="header-user-meta">{{ auth.currentUser?.roles?.join(' / ') || '未分配角色' }}</div>
              </div>
              <el-icon class="header-user-caret"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="change-password">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="app-content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AlarmClock,
  ArrowDown,
  Avatar,
  Calendar,
  OfficeBuilding,
  Reading,
  Setting,
  User,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useInstitutionStore } from '@/stores/institution'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const institutionStore = useInstitutionStore()

onMounted(() => {
  institutionStore.loadInstitutions()
})

interface SidebarChildDefinition {
  key: string
  to: string
  label: string
  title: string
  prefixes: string[]
  menuSignals?: string[]
  permissionSignals?: string[]
}

interface SidebarDefinition {
  key: string
  to: string
  label: string
  title: string
  icon: Component
  group: 'workbench' | 'daily' | 'archive' | 'class' | 'analysis' | 'system'
  hint?: string
  prefixes: string[]
  alwaysVisible?: boolean
  menuSignals?: string[]
  permissionSignals?: string[]
  children?: SidebarChildDefinition[]
}

/** 当前用户对某菜单项是否有访问权(命中菜单信号或任一权限信号) */
function canAccess(menuSignals?: string[], permissionSignals?: string[]) {
  return (
    auth.hasMenuAccess(menuSignals || []) ||
    (permissionSignals || []).some((permission: string) => auth.hasPermission(permission))
  )
}

function hasVisibleChild(item: SidebarDefinition) {
  return (item.children || []).some((child) => canAccess(child.menuSignals, child.permissionSignals))
}

const sidebarDefinitions: SidebarDefinition[] = [
  {
    key: 'dashboard',
    to: '/dashboard',
    label: '今日教务',
    title: '今日教务工作台',
    icon: Calendar,
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
    icon: AlarmClock,
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
    icon: User,
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
    icon: Reading,
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
    icon: OfficeBuilding,
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
    icon: Avatar,
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
    icon: Setting,
    group: 'system',
    hint: '用户、角色、权限、字典',
    prefixes: ['/system'],
    children: [
      {
        key: 'system-institution',
        to: '/system/institution',
        label: '机构资料',
        title: '机构资料',
        prefixes: ['/system/institution'],
        menuSignals: ['system_institution', '/system/institution'],
        permissionSignals: ['organization:read', 'organization:write', 'system:user:manage'],
      },
      {
        key: 'system-users',
        to: '/system/users',
        label: '用户管理',
        title: '用户管理',
        prefixes: ['/system/users'],
        menuSignals: ['system_users', '/system/users'],
        permissionSignals: ['system:user:manage'],
      },
      {
        key: 'system-roles',
        to: '/system/roles',
        label: '角色管理',
        title: '角色管理',
        prefixes: ['/system/roles'],
        menuSignals: ['system_roles', '/system/roles'],
        permissionSignals: ['system:role:manage'],
      },
      {
        key: 'system-menus',
        to: '/system/menus',
        label: '菜单管理',
        title: '菜单管理',
        prefixes: ['/system/menus'],
        menuSignals: ['system_menus', '/system/menus'],
        permissionSignals: ['system:menu:manage'],
      },
      {
        key: 'system-config',
        to: '/system/config',
        label: '系统参数',
        title: '系统参数',
        prefixes: ['/system/config'],
        menuSignals: ['system_config', '/system/config'],
        permissionSignals: ['system:config:read', 'system:config:write'],
      },
      {
        key: 'system-dicts',
        to: '/system/dicts',
        label: '字典管理',
        title: '字典管理',
        prefixes: ['/system/dicts'],
        menuSignals: ['system_dicts', '/system/dicts'],
        permissionSignals: ['system:dict:manage'],
      },
      {
        key: 'system-list-configs',
        to: '/system/list-configs',
        label: '列表配置',
        title: '列表配置',
        prefixes: ['/system/list-configs'],
        menuSignals: ['system_list_configs', '/system/list-configs'],
        permissionSignals: ['system:list-config:manage'],
      },
      {
        key: 'system-permission-overview',
        to: '/system/permission-overview',
        label: '功能权限',
        title: '功能权限总览',
        prefixes: ['/system/permission-overview'],
        menuSignals: ['system_permission_overview', '/system/permission-overview'],
        permissionSignals: ['system:menu:manage'],
      },
    ],
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
    if (item.children?.length) {
      // 父菜单自身不带信号时,可见性完全由子项决定(复刻原系统设置页 tabs 为空则整组隐藏的语义)
      return hasVisibleChild(item)
    }
    if (item.alwaysVisible) {
      return true
    }
    return canAccess(item.menuSignals, item.permissionSignals)
  })
})

/** 当前路径命中的子项(用于标题与高亮,子项优先于父项) */
const matchedChild = computed(() => {
  for (const item of sidebarDefinitions) {
    for (const child of item.children || []) {
      if (child.prefixes.some((prefix) => route.path.startsWith(prefix))) {
        return child
      }
    }
  }
  return null
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
  return matchedChild.value?.title ?? sidebarDefinitions.find((item) => item.prefixes.some((prefix) => route.path.startsWith(prefix)))?.title ?? '教培管理平台'
})

function isNavItemActive(item: SidebarDefinition) {
  return item.prefixes.some((prefix) => route.path.startsWith(prefix))
}

function isChildActive(child: SidebarChildDefinition) {
  return child.prefixes.some((prefix) => route.path.startsWith(prefix))
}

// 展开态:默认收起;当前路径位于父项前缀内时自动展开(可点击手动收起)
const openGroups = ref(new Set<string>())

function isGroupOpen(item: SidebarDefinition) {
  return openGroups.value.has(item.key) || item.prefixes.some((prefix) => route.path.startsWith(prefix))
}

function toggleGroup(key: string) {
  const next = new Set(openGroups.value)
  if (next.has(key)) {
    next.delete(key)
  } else {
    next.add(key)
  }
  openGroups.value = next
}

function visibleChildren(item: SidebarDefinition) {
  return (item.children || []).filter((child) => canAccess(child.menuSignals, child.permissionSignals))
}

function handleUserCommand(command: string | number | object) {
  if (command === 'change-password') {
    router.push('/change-password')
  } else if (command === 'logout') {
    handleLogout()
  }
}

async function handleLogout() {
  await auth.logout()
  institutionStore.reset()
  router.push('/login')
}
</script>
