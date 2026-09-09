<template>
  <div class="app-shell" :class="shellClass">
    <!-- ============ 完整导航(宽模式) ============ -->
    <aside v-if="navMode === 'full'" class="app-sidebar">
      <div class="brand-block">
        <div class="brand-kicker">线下班课教务中台</div>
        <div class="brand-title">教培管理平台</div>
      </div>

      <nav class="nav-groups">
        <section v-for="group in navGroups" :key="group.key" class="nav-group">
          <div class="nav-group-title">{{ group.label }}</div>
          <div class="nav-list">
            <template v-for="item in group.items" :key="item.key">
              <div v-if="visibleChildren(item).length" class="nav-parent">
                <button
                  type="button"
                  class="nav-item nav-toggle"
                  :class="{ 'is-active': isNavItemActive(item), 'is-open': isGroupOpen(item) }"
                  @click="toggleGroup(item.key)"
                >
                  <el-icon v-if="item.icon" class="nav-item-icon"><component :is="item.icon" /></el-icon>
                  <span class="nav-item-label">{{ displayLabel(item) }}</span>
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
                    <el-icon v-if="child.icon" class="nav-item-icon nav-item-icon-sub"><component :is="child.icon" /></el-icon>
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

    <!-- ============ 图标导航(窄模式 / rail) ============ -->
    <aside v-else class="app-sidebar app-sidebar-rail">
      <div class="brand-block rail-brand">
        <span class="rail-brand-mark">培</span>
      </div>

      <nav class="rail-nav">
        <template v-for="(group, groupIndex) in navRailGroups" :key="group.key">
          <div v-if="groupIndex > 0" class="rail-group-gap" />
          <div class="rail-group-items">
            <div
              v-for="item in group.items"
              :key="item.key"
              class="rail-cell"
              @mouseenter="openRail(item)"
              @mouseleave="scheduleCloseRail()"
            >
              <RouterLink
                v-if="!item.children.length"
                :to="item.to"
                class="rail-trigger"
                :class="{ 'is-active': isNavItemActive(item) }"
                :title="item.label"
              >
                <el-icon class="rail-icon"><component :is="item.icon" /></el-icon>
              </RouterLink>
              <button
                v-else
                type="button"
                class="rail-trigger"
                :class="{ 'is-active': isNavItemActive(item), 'is-pinned': activeRailPopoverKey === item.key }"
                :title="item.label"
                @click="toggleRailPin(item)"
              >
                <el-icon class="rail-icon"><component :is="item.icon" /></el-icon>
              </button>

              <div
                v-if="item.children.length && activeRailPopoverKey === item.key"
                class="rail-popover"
                @mouseenter="cancelCloseRail()"
                @mouseleave="scheduleCloseRail()"
              >
                <div class="rail-pop-head">
                  <span class="rail-pop-group">{{ group.label }}</span>
                  <span class="rail-pop-title">{{ item.label }}</span>
                </div>
                <RouterLink
                  v-for="child in item.children"
                  :key="child.key"
                  :to="child.to"
                  class="rail-pop-item"
                  :class="{ 'is-active': isChildActive(child) }"
                  @click="handleRailChildClick(item)"
                >
                  <el-icon v-if="child.icon" class="rail-pop-icon"><component :is="child.icon" /></el-icon>
                  <span>{{ child.label }}</span>
                </RouterLink>
              </div>
            </div>
          </div>
        </template>
      </nav>
    </aside>

    <div class="app-main">
      <el-alert
        v-if="auth.currentUser?.passwordResetRequired || auth.mustChangePassword"
        class="app-alert"
        :type="auth.mustChangePassword ? 'error' : 'warning'"
        show-icon
        :closable="false"
        :title="auth.mustChangePassword ? '当前账号已被强制跳转至改密流程,完成修改前不能进入业务首页。' : '当前账号仍处于初始化密码状态,请尽快完成改密。'"
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
                <el-dropdown-item disabled>
                  <span class="nav-mode-label">导航模式</span>
                </el-dropdown-item>
                <el-dropdown-item command="nav-mode:full">
                  <el-icon v-if="navMode === 'full'" class="nav-mode-check"><Check /></el-icon>
                  <span>完整导航</span>
                </el-dropdown-item>
                <el-dropdown-item command="nav-mode:rail">
                  <el-icon v-if="navMode === 'rail'" class="nav-mode-check"><Check /></el-icon>
                  <span>图标导航</span>
                </el-dropdown-item>
                <el-dropdown-item command="change-password" divided>修改密码</el-dropdown-item>
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
  Check,
  OfficeBuilding,
  Reading,
  Setting,
  User,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useInstitutionStore } from '@/stores/institution'
import type { SystemMenuTreeNode } from '@/api/system'
import { resolveMenuIcon } from '@/constants/menuIcons'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const institutionStore = useInstitutionStore()

onMounted(() => {
  institutionStore.loadInstitutions()
})

/** ================= 导航数据模型(宽窄两态共用) ================= */

interface SidebarChildDefinition {
  key: string
  to: string
  label: string
  title: string
  prefixes: string[]
  icon?: Component
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
  /** 命中该项对应系统目录(menuCode)时,子项改由后端菜单树动态提供 */
  menuCatalogCode?: string
}

/** 后端菜单节点 → 侧边栏子项结构(名称/顺序/图标来自 sys_menu,可后台配置) */
function adaptMenuChildren(nodes: SystemMenuTreeNode[]): SidebarChildDefinition[] {
  return nodes
    .filter((node) => node.menuType === 'menu' && node.path)
    .map((node) => ({
      key: node.menuCode,
      to: node.path as string,
      label: node.menuName,
      title: node.menuName,
      prefixes: [node.path as string],
      icon: resolveMenuIcon(node.icon),
    }))
}

/** 当前用户菜单树里 code 对应的节点(含目录与菜单) */
function findMenuNode(items: SystemMenuTreeNode[] | undefined, code: string): SystemMenuTreeNode | null {
  for (const item of items || []) {
    if (item.menuCode === code) {
      return item
    }
    const found = findMenuNode(item.children, code)
    if (found) {
      return found
    }
  }
  return null
}

/**
 * 计算某菜单项应展示的子项。
 * - 配置化目录(system):子项直接来自后端菜单树(角色已授权 + 按 sortNo 排序),目录未授权则无子项;
 * - 其余顶层项无子项。
 */
function resolveVisibleChildren(item: SidebarDefinition): SidebarChildDefinition[] {
  if (item.menuCatalogCode) {
    const catalog = findMenuNode(auth.currentMenus, item.menuCatalogCode)
    if (!catalog) {
      return []
    }
    return adaptMenuChildren(catalog.children || [])
  }
  return []
}

function hasVisibleChild(item: SidebarDefinition) {
  return resolveVisibleChildren(item).length > 0
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
    // 系统设置组:父级目录与子项均来自 sys_menu(/auth/current/menus),名称/顺序/图标可在后台菜单管理配置
    menuCatalogCode: 'system',
  },
]

/** 配置化目录的显示名优先取菜单树中的 menuName(后台可改名),树未就绪回退静态 label */
function displayLabel(item: SidebarDefinition): string {
  if (item.menuCatalogCode) {
    const catalog = findMenuNode(auth.currentMenus, item.menuCatalogCode)
    if (catalog?.menuName) {
      return catalog.menuName
    }
  }
  return item.label
}

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
    // 其余顶层项(当前仅系统目录):至少有一个可见子项才显示
    return hasVisibleChild(item)
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

/** ================= 当前页匹配(标题 / 高亮) ================= */

/** 当前路径命中的子项(用于标题与高亮,子项优先于父项) */
const matchedChild = computed(() => {
  for (const item of sidebarDefinitions) {
    for (const child of resolveVisibleChildren(item)) {
      if (child.prefixes.some((prefix) => route.path.startsWith(prefix))) {
        return child
      }
    }
  }
  return null
})

const currentTitle = computed(() => {
  return (
    matchedChild.value?.title ??
    sidebarDefinitions.find((item) => item.prefixes.some((prefix) => route.path.startsWith(prefix)))?.title ??
    '教培管理平台'
  )
})

function isNavItemActive(item: { prefixes: string[] }) {
  return item.prefixes.some((prefix) => route.path.startsWith(prefix))
}

function isChildActive(child: SidebarChildDefinition) {
  return child.prefixes.some((prefix) => route.path.startsWith(prefix))
}

/** 完整模式:可展开父菜单的展开态(默认收起,当前路径在父项前缀内自动展开) */
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
  return resolveVisibleChildren(item)
}

/** ================= 导航模式(完整 / 图标窄栏) ================= */

const NAV_MODE_STORAGE_KEY = 'jiaopei-nav-mode'
type NavMode = 'full' | 'rail'

function readStoredNavMode(): NavMode {
  if (typeof window === 'undefined') {
    return 'full'
  }
  return window.localStorage.getItem(NAV_MODE_STORAGE_KEY) === 'rail' ? 'rail' : 'full'
}

const navMode = ref<NavMode>(readStoredNavMode())

const shellClass = computed(() => (navMode.value === 'rail' ? 'is-rail' : 'is-full'))

function setNavMode(mode: NavMode) {
  navMode.value = mode
  window.localStorage.setItem(NAV_MODE_STORAGE_KEY, mode)
}

/**
 * 窄栏数据:分组 → 目录项。目录项统一带 children(空数组表示无子页的直达项)。
 */
interface RailItem {
  key: string
  label: string
  icon: Component
  to: string
  prefixes: string[]
  children: SidebarChildDefinition[]
}

const navRailGroups = computed(() => {
  return navGroups.value.map((group) => ({
    key: group.key,
    label: group.label,
    items: group.items.map<RailItem>((item) => ({
      key: item.key,
      label: displayLabel(item),
      icon: item.icon,
      to: item.to,
      prefixes: item.prefixes,
      children: visibleChildren(item),
    })),
  }))
})

/** 窄栏浮层状态:hover 打开、点击可钉住;移出图标+浮层后延迟收起 */
const hoveredRailKey = ref<string | null>(null)
const pinnedRailKey = ref<string | null>(null)
const railCloseTimer = ref<ReturnType<typeof setTimeout> | null>(null)

const activeRailPopoverKey = computed(() => {
  return pinnedRailKey.value ?? hoveredRailKey.value
})

function openRail(item: RailItem) {
  cancelCloseRail()
  if (!item.children.length) {
    hoveredRailKey.value = null
    return
  }
  hoveredRailKey.value = item.key
}

function cancelCloseRail() {
  if (railCloseTimer.value) {
    clearTimeout(railCloseTimer.value)
    railCloseTimer.value = null
  }
}

function scheduleCloseRail() {
  if (pinnedRailKey.value) {
    return
  }
  cancelCloseRail()
  railCloseTimer.value = setTimeout(() => {
    hoveredRailKey.value = null
    railCloseTimer.value = null
  }, 200)
}

function toggleRailPin(item: RailItem) {
  if (!item.children.length) {
    hoveredRailKey.value = null
    return
  }
  if (pinnedRailKey.value === item.key) {
    pinnedRailKey.value = null
  } else {
    pinnedRailKey.value = item.key
    hoveredRailKey.value = null
  }
}

function handleRailChildClick(item: RailItem) {
  if (pinnedRailKey.value === item.key) {
    pinnedRailKey.value = null
  }
}

/** ================= 顶栏用户操作 ================= */

function handleUserCommand(command: string | number | object) {
  if (command === 'nav-mode:full') {
    setNavMode('full')
  } else if (command === 'nav-mode:rail') {
    setNavMode('rail')
  } else if (command === 'change-password') {
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
