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
                  <el-icon v-if="item.icon" class="nav-item-icon"
                    ><component :is="item.icon"
                  /></el-icon>
                  <span class="nav-item-label">{{ displayLabel(item) }}</span>
                  <el-icon class="nav-caret"><ArrowDown /></el-icon>
                </button>
                <div v-show="isGroupOpen(item)" class="nav-children">
                  <template v-for="(child, idx) in visibleChildren(item)" :key="child.key">
                    <div
                      v-if="child.groupLabel && child.groupLabel !== prevGroupLabel(item, idx)"
                      class="nav-child-group-title"
                    >
                      {{ child.groupLabel }}
                    </div>
                    <RouterLink
                      :to="child.to"
                      class="nav-item nav-child-item"
                      :class="{ 'is-active': isChildActive(child) }"
                    >
                      <el-icon v-if="child.icon" class="nav-item-icon nav-item-icon-sub"
                        ><component :is="child.icon"
                      /></el-icon>
                      <span class="nav-item-label">{{ child.label }}</span>
                    </RouterLink>
                  </template>
                </div>
              </div>
              <RouterLink
                v-else
                :to="item.to"
                class="nav-item"
                :class="{ 'is-active': isNavItemActive(item) }"
              >
                <el-icon v-if="item.icon" class="nav-item-icon"
                  ><component :is="item.icon"
                /></el-icon>
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
                :class="{
                  'is-active': isNavItemActive(item),
                  'is-pinned': activeRailPopoverKey === item.key,
                }"
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
                <template v-for="(child, idx) in item.children" :key="child.key">
                  <div
                    v-if="child.groupLabel && child.groupLabel !== prevRailGroupLabel(item, idx)"
                    class="rail-pop-subgroup-title"
                  >
                    {{ child.groupLabel }}
                  </div>
                  <RouterLink
                    :to="child.to"
                    class="rail-pop-item"
                    :class="{ 'is-active': isChildActive(child) }"
                    @click="handleRailChildClick(item)"
                  >
                    <el-icon v-if="child.icon" class="rail-pop-icon"
                      ><component :is="child.icon"
                    /></el-icon>
                    <span>{{ child.label }}</span>
                  </RouterLink>
                </template>
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
        :title="
          auth.mustChangePassword
            ? '当前账号已被强制跳转至改密流程,完成修改前不能进入业务首页。'
            : '当前账号仍处于初始化密码状态,请尽快完成改密。'
        "
      />

      <header class="app-header">
        <h1 class="page-title">{{ currentTitle }}</h1>
        <div class="header-actions">
          <div class="header-institution-block">
            <el-tag
              v-if="institutionStore.isSingleInstitution"
              type="info"
              effect="plain"
              size="large"
            >
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
                <div class="header-user-meta">
                  {{ auth.currentUser?.roles?.join(' / ') || '未分配角色' }}
                </div>
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
                <el-dropdown-item command="menu-preferences" divided>菜单偏好</el-dropdown-item>
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

    <!-- ============ 菜单偏好设置 ============ -->
    <el-dialog v-model="menuPreferencesVisible" title="菜单偏好" width="460px">
      <p class="menu-pref-tip">偏好仅保存在当前浏览器，不随账号同步。</p>
      <div class="menu-pref-group">
        <div class="menu-pref-label">组展开策略（完整导航）</div>
        <el-radio-group v-model="menuPreferences.groupExpand" class="menu-pref-options">
          <el-radio value="smart"
            >智能：自动展开当前组，可手动收起，点击页面其他区域自动收起</el-radio
          >
          <el-radio value="classic">经典：进入子页面后父组保持展开，无法收起</el-radio>
          <el-radio value="manual">手动：仅点击展开，不自动展开</el-radio>
        </el-radio-group>
      </div>
      <div class="menu-pref-group">
        <div class="menu-pref-label">浮层行为（图标导航）</div>
        <el-radio-group v-model="menuPreferences.railPopover" class="menu-pref-options">
          <el-radio value="pin-outside">悬停展开，点击钉住，点击其他区域收起</el-radio>
          <el-radio value="hover">仅悬停展开，移出即收起</el-radio>
          <el-radio value="pin">悬停展开，点击钉住后常开</el-radio>
        </el-radio-group>
      </div>
      <template #footer>
        <el-button @click="menuPreferencesVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch, type Component } from 'vue'
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
  Timer,
  User,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import type { SystemMenuTreeNode } from '@cornerstone/core/api/system'
import { resolveMenuIcon } from '@cornerstone/core/constants/menuIcons'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const institutionStore = useInstitutionStore()

onMounted(() => {
  institutionStore.loadInstitutions()
})

/** ================= 菜单偏好(本机 localStorage,不同操作习惯可自选) ================= */

/** 宽栏组展开策略:classic=路由命中强制展开(旧行为),smart=自动展开当前组+可收起+开一收其余+点侧栏外自动收起,manual=纯手动点击展开 */
type GroupExpandMode = 'classic' | 'smart' | 'manual'
/** 窄栏浮层行为:hover=悬停展开移出即收,pin-outside=点击钉住且点外部收起(默认),pin=点击钉住常开(旧行为) */
type RailPopoverMode = 'hover' | 'pin-outside' | 'pin'

interface MenuPreferences {
  groupExpand: GroupExpandMode
  railPopover: RailPopoverMode
}

const MENU_PREFS_STORAGE_KEY = 'cornerstone-menu-preferences'
const defaultMenuPreferences: MenuPreferences = { groupExpand: 'smart', railPopover: 'pin-outside' }

function pickValue<T extends string>(value: unknown, allowed: readonly T[], fallback: T): T {
  return allowed.includes(value as T) ? (value as T) : fallback
}

function readStoredMenuPreferences(): MenuPreferences {
  if (typeof window === 'undefined') {
    return { ...defaultMenuPreferences }
  }
  try {
    const stored: Partial<MenuPreferences> = JSON.parse(
      window.localStorage.getItem(MENU_PREFS_STORAGE_KEY) || '{}'
    )
    return {
      groupExpand: pickValue(
        stored.groupExpand,
        ['classic', 'smart', 'manual'] as const,
        defaultMenuPreferences.groupExpand
      ),
      railPopover: pickValue(
        stored.railPopover,
        ['hover', 'pin-outside', 'pin'] as const,
        defaultMenuPreferences.railPopover
      ),
    }
  } catch {
    return { ...defaultMenuPreferences }
  }
}

const menuPreferences = ref<MenuPreferences>(readStoredMenuPreferences())
const menuPreferencesVisible = ref(false)

watch(
  menuPreferences,
  (prefs) => {
    window.localStorage.setItem(MENU_PREFS_STORAGE_KEY, JSON.stringify(prefs))
  },
  { deep: true }
)

/** ================= 导航数据模型(宽窄两态共用) ================= */

interface SidebarChildDefinition {
  key: string
  to: string
  label: string
  title: string
  prefixes: string[]
  icon?: Component
  /** 所属二级目录名(空=直属顶层目录);用于侧边栏分组小标题 */
  groupLabel?: string
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
function adaptMenuChildren(nodes: SystemMenuTreeNode[], groupLabel = ''): SidebarChildDefinition[] {
  const flat: SidebarChildDefinition[] = []
  const walk = (list: SystemMenuTreeNode[], currentGroup: string) => {
    for (const node of list) {
      // 目录(catalog)本身不作为可点击子项,但需下钻收集其下的页面菜单,并记录分组名
      if (node.menuType === 'catalog') {
        walk(node.children || [], node.menuName)
      } else if (node.menuType === 'menu' && node.path) {
        flat.push({
          key: node.menuCode,
          to: node.path as string,
          label: node.menuName,
          title: node.menuName,
          prefixes: [node.path as string],
          icon: resolveMenuIcon(node.icon),
          groupLabel: currentGroup,
        })
      }
    }
  }
  walk(nodes, groupLabel)
  return flat
}

/** 当前用户菜单树里 code 对应的节点(含目录与菜单) */
function findMenuNode(
  items: SystemMenuTreeNode[] | undefined,
  code: string
): SystemMenuTreeNode | null {
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
    key: 'scheduling',
    to: '/scheduling',
    label: '排课中心',
    title: '排课中心',
    icon: AlarmClock,
    group: 'daily',
    hint: '排课、点名、结课',
    prefixes: ['/scheduling', '/schedule'],
    // 目录与子项来自 sys_menu(V37),名称/顺序/图标可后台配置
    menuCatalogCode: 'education_schedule_center',
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
    hint: '校区、教室、学期',
    prefixes: ['/campus'],
    alwaysVisible: true,
  },
  {
    key: 'time-slots',
    to: '/time-slots',
    label: '时间段设置',
    title: '时间段设置(节次模板与网格编辑)',
    icon: Timer,
    group: 'archive',
    hint: '按参数生成节次并网格微调',
    prefixes: ['/time-slots'],
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
    sidebarDefinitions.find((item) => item.prefixes.some((prefix) => route.path.startsWith(prefix)))
      ?.title ??
    '教培管理平台'
  )
})

function isNavItemActive(item: { prefixes: string[] }) {
  return item.prefixes.some((prefix) => route.path.startsWith(prefix))
}

function isChildActive(child: SidebarChildDefinition) {
  return child.prefixes.some((prefix) => route.path.startsWith(prefix))
}

/** 完整模式:可展开父菜单的展开态,策略由「菜单偏好-组展开策略」控制 */
const openGroups = ref(new Set<string>())

function routeMatchesItem(item: { prefixes: string[] }) {
  return item.prefixes.some((prefix) => route.path.startsWith(prefix))
}

function isGroupOpen(item: SidebarDefinition) {
  if (menuPreferences.value.groupExpand === 'classic') {
    return openGroups.value.has(item.key) || routeMatchesItem(item)
  }
  return openGroups.value.has(item.key)
}

function toggleGroup(key: string) {
  const next = new Set(openGroups.value)
  if (next.has(key)) {
    next.delete(key)
  } else {
    if (menuPreferences.value.groupExpand === 'smart') {
      // 手风琴:展开一个组时收起其余组
      next.clear()
    }
    next.add(key)
  }
  openGroups.value = next
}

/** smart 模式下 openGroups 是展开态唯一来源:路由切换/菜单树就绪时只展开当前页所在组 */
watch(
  [() => route.path, () => auth.currentMenus, () => menuPreferences.value.groupExpand],
  () => {
    if (menuPreferences.value.groupExpand !== 'smart') {
      return
    }
    const activeParent = sidebarDefinitions.find(
      (item) => hasVisibleChild(item) && routeMatchesItem(item)
    )
    openGroups.value = activeParent ? new Set([activeParent.key]) : new Set()
  },
  { immediate: true }
)

function visibleChildren(item: SidebarDefinition) {
  return resolveVisibleChildren(item)
}

/** 分组小标题判定:与上一个子项的目录名不同才显示标题 */
function prevGroupLabel(item: SidebarDefinition, idx: number) {
  if (idx === 0) {
    return ''
  }
  return resolveVisibleChildren(item)[idx - 1]?.groupLabel ?? ''
}

/** 窄栏浮层分组小标题判定(item.children 已是扁平数组) */
function prevRailGroupLabel(item: RailItem, idx: number) {
  if (idx === 0) {
    return ''
  }
  return item.children[idx - 1]?.groupLabel ?? ''
}

/** ================= 导航模式(完整 / 图标窄栏) ================= */

const NAV_MODE_STORAGE_KEY = 'cornerstone-nav-mode'
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
  // hover 模式:浮层只跟随鼠标,点击不钉住
  if (menuPreferences.value.railPopover === 'hover') {
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

/** 点击侧边栏之外的区域:收起钉住的窄栏浮层(pin-outside)与 smart 模式下展开的组 */
function handleDocumentClick(event: MouseEvent) {
  const target = event.target as HTMLElement | null
  const insideSidebar = !!target?.closest('.app-sidebar')
  if (
    pinnedRailKey.value &&
    menuPreferences.value.railPopover === 'pin-outside' &&
    !insideSidebar
  ) {
    pinnedRailKey.value = null
  }
  if (menuPreferences.value.groupExpand === 'smart' && !insideSidebar && openGroups.value.size) {
    openGroups.value = new Set()
  }
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
})

onUnmounted(() => {
  document.removeEventListener('click', handleDocumentClick)
})

/** ================= 顶栏用户操作 ================= */

function handleUserCommand(command: string | number | object) {
  if (command === 'nav-mode:full') {
    setNavMode('full')
  } else if (command === 'nav-mode:rail') {
    setNavMode('rail')
  } else if (command === 'menu-preferences') {
    menuPreferencesVisible.value = true
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

<style scoped>
.menu-pref-tip {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.menu-pref-group {
  margin-bottom: 16px;
}

.menu-pref-label {
  margin-bottom: 6px;
  font-weight: 600;
}

.menu-pref-options {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}
</style>
