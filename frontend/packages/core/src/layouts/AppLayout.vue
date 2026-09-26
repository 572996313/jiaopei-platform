<template>
  <div class="app-shell" :class="shellClass">
    <!-- ============ 横幅:全站唯一一条深色带,品牌 / 当前页 / 搜索 / 机构 / 账号 ============ -->
    <header class="shell-banner">
      <div class="banner-brand">
        <span v-if="brandMark" class="banner-mark">{{ brandMark }}</span>
        <div class="banner-brand-text">
          <b>{{ brandName }}</b>
          <span>{{ brandTagline }}</span>
        </div>
      </div>

      <div class="banner-ctx">
        <div class="banner-crumb">{{ crumbText }}</div>
        <h1 class="banner-title">{{ pageTitle }}</h1>
      </div>

      <div class="banner-actions">
        <!-- 业务登记的顶栏全局入口（公告铃铛一类），排在底座那几个固定项之前；没装业务模块这段就是空的 -->
        <component v-for="action in bannerActions" :is="action.component" :key="action.key" />

        <!-- 窄栏把二级页收进悬停浮层了,搜索是它的兜底入口,所以不做成可关掉的装饰 -->
        <button type="button" class="banner-search" @click="searchOpen = true">
          <el-icon><Search /></el-icon>
          <span>搜页面</span>
          <kbd>Ctrl K</kbd>
        </button>

        <!-- 外观三档,和登录页同一套(状态挂在 use-theme 上,这里只是第二个入口) -->
        <div class="banner-theme" role="group" aria-label="页面外观">
          <button
            v-for="option in THEME_OPTIONS"
            :key="option.mode"
            type="button"
            class="banner-theme-btn"
            :class="{ 'is-active': themeMode === option.mode }"
            :title="option.label"
            :aria-label="option.label"
            :aria-pressed="themeMode === option.mode"
            @click="setTheme(option.mode)"
          >
            <el-icon><component :is="THEME_ICONS[option.mode]" /></el-icon>
          </button>
        </div>

        <div v-if="institutionStore.isSingleInstitution" class="banner-chip banner-chip-static">
          {{ institutionStore.currentInstitutionName }}
        </div>
        <div v-else-if="institutionStore.hasInstitutions" class="banner-chip">
          <el-select
            :model-value="institutionStore.currentInstitutionId"
            placeholder="选择机构"
            filterable
            @change="institutionStore.setInstitution($event as string)"
          >
            <el-option
              v-for="org in institutionStore.institutions"
              :key="org.id"
              :label="org.orgName || org.shortName || org.orgCode || `机构 #${org.id}`"
              :value="org.id"
            />
          </el-select>
        </div>
        <div v-else class="banner-chip banner-chip-static is-warn">未选择机构</div>

        <el-dropdown trigger="click" @command="handleUserCommand">
          <div class="banner-chip banner-user">
            <span class="banner-user-avatar">{{ auth.displayName.slice(0, 1) }}</span>
            <div class="banner-user-info">
              <div class="banner-user-name">{{ auth.displayName }}</div>
              <div class="banner-user-meta">
                {{ auth.currentUser?.roles?.join(' / ') || '未分配角色' }}
              </div>
            </div>
            <el-icon class="banner-user-caret"><ArrowDown /></el-icon>
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
    <div class="banner-seam" />

    <div class="shell-body">
      <!-- ============ 完整导航(宽模式) ============ -->
      <aside v-if="navMode === 'full'" class="app-nav is-full">
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
                  <span class="nav-item-label">{{ displayLabel(item) }}</span>
                </RouterLink>
              </template>
            </div>
          </section>
        </nav>
      </aside>

      <!-- ============ 图标导航(窄模式 / rail) ============ -->
      <aside v-else class="app-nav is-rail">
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
                    <span v-if="group.label !== item.label" class="rail-pop-group">
                      {{ group.label }}
                    </span>
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

        <!-- 内容卡里第一行是页签:标题在横幅上,页签只负责"开着哪几页"和切换 -->
        <div class="app-tabbar">
          <nav ref="tabStripRef" class="app-tab-strip" @scroll="tabMenu = null">
            <div
              v-for="(tab, index) in tabs.tabs"
              :key="tab.path"
              class="app-tab"
              :class="{
                'is-active': tab.path === tabs.activePath,
                'is-dragging': draggingIndex === index,
              }"
              draggable="true"
              @click="activateTab(tab.path)"
              @auxclick="onTabAuxClick(tab.path, $event)"
              @contextmenu.prevent="openTabMenu(tab, $event)"
              @dragstart="onTabDragStart(index, $event)"
              @dragover.prevent
              @drop.prevent="onTabDrop(index)"
              @dragend="draggingIndex = null"
            >
              <span class="app-tab-label">{{ tab.label || tab.path }}</span>
              <el-icon
                v-if="tabs.closable"
                class="app-tab-action"
                title="关闭页签"
                @click.stop="closeTab(tab.path)"
              >
                <Close />
              </el-icon>
            </div>
          </nav>

          <!-- 最近查看固定在行尾,不跟页签一起滚:页签被上限挤掉时靠它找回去,滚走了就本末倒置 -->
          <button
            ref="historyButtonRef"
            type="button"
            class="app-tab-history"
            :class="{ 'is-open': !!historyMenu }"
            title="最近查看的页面"
            @click="toggleHistoryMenu"
          >
            最近查看
          </button>
        </div>

        <main class="app-content">
          <RouterView v-slot="{ Component }">
            <!--
            按页签换 key:同一页面切回来命中缓存(现场不丢),刷新或关掉重开时 key 变了,
            重新挂一个干净实例。没走 include 是因为 include 认组件 name,而路由组件是懒加载的,
            页签只拿得到路径;遗留的失活实例交给 max 按 LRU 挤出。
          -->
            <KeepAlive :max="MAX_CACHED_PAGES">
              <component :is="Component" v-if="Component" :key="tabs.cacheKey(route.path)" />
            </KeepAlive>
          </RouterView>
        </main>
      </div>
    </div>

    <!-- ============ 横幅全局搜索 ============ -->
    <NavSearchPalette v-model="searchOpen" :items="searchablePages" />

    <!-- ============ 页签右键菜单 ============ -->
    <div
      v-if="tabMenu"
      class="tab-context-menu"
      :style="{ left: `${tabMenu.x}px`, top: `${tabMenu.y}px` }"
    >
      <button
        v-for="item in tabMenuActions"
        :key="item.action"
        type="button"
        class="tab-context-item"
        :class="{ 'is-divided': item.action === 'single' }"
        :disabled="!item.enabled"
        @click="runTabMenu(item.action)"
      >
        {{ item.label }}
      </button>
    </div>

    <!-- ============ 最近查看 ============ -->
    <div
      v-if="historyMenu"
      class="tab-context-menu app-history-menu"
      :style="{ left: `${historyMenu.x}px`, top: `${historyMenu.y}px` }"
    >
      <button
        v-for="item in tabs.history"
        :key="item.path"
        type="button"
        class="tab-context-item app-history-item"
        :class="{ 'is-opened': openedPaths.has(item.path) }"
        :title="item.fullPath"
        @click="goVisited(item)"
      >
        {{ item.label || item.path }}
      </button>
      <div v-if="!tabs.history.length" class="app-history-empty">还没有访问记录</div>
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
import { computed, nextTick, onMounted, onUnmounted, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDown,
  Check,
  Close,
  Monitor,
  Moon,
  Search,
  Setting,
  Sunny,
} from '@element-plus/icons-vue'
import NavSearchPalette, { type SearchablePage } from './NavSearchPalette.vue'
import {
  contributedBannerActions,
  contributedNavItems,
  type NavItemDefinition,
} from '@cornerstone/core/extension/module-extensions'
import { useBranding } from '@cornerstone/core/composables/use-branding'
import { useTheme, type ThemeMode } from '@cornerstone/core/composables/use-theme'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import {
  MAX_CACHED_PAGES,
  useTabsStore,
  type PageTab,
  type TabCloseMode,
  type VisitedPage,
} from '@cornerstone/core/stores/tabs'
import type { SystemMenuTreeNode } from '@cornerstone/core/api/system'
import { resolveMenuIcon } from '@cornerstone/core/constants/menuIcons'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const institutionStore = useInstitutionStore()

/** 品牌文案来自系统参数 brand.*：直接刷新进后台时也要拉一次，登录页拉过则复用同一份请求 */
const { brandName, brandTagline, brandMark, loadBranding } = useBranding()

onMounted(() => {
  institutionStore.loadInstitutions()
  loadBranding()
})

/** ================= 外观档位（明亮/夜晚/跟随系统） ================= */
const { themeMode, setTheme, THEME_OPTIONS } = useTheme()

const THEME_ICONS: Record<ThemeMode, Component> = {
  light: Sunny,
  dark: Moon,
  system: Monitor,
}

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
const searchOpen = ref(false)

/** 顶栏业务入口：现算而不是加载时取快照，登记晚于外壳挂载也要出现 */
const bannerActions = computed(() => contributedBannerActions())

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

/** 顶层导航项结构由扩展点定义，业务模块经 registerNavItems 登记，底座不写死业务路由 */
type SidebarDefinition = NavItemDefinition

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

/**
 * 底座自有的顶层导航项。业务模块的入口一律经 registerNavItems 登记，
 * 这里只保留底座自身（系统设置是底座概念，任何行业都要）。
 */
const coreSidebarDefinitions: SidebarDefinition[] = [
  {
    key: 'system',
    to: '/system/users',
    label: '系统设置',
    title: '系统设置',
    icon: Setting,
    group: 'system',
    groupLabel: '系统设置',
    // 底座段固定沉底，业务模块的分组顺序号都小于它
    groupOrder: 900,
    prefixes: ['/system'],
    // 系统设置组:父级目录与子项均来自 sys_menu(/auth/current/menus),名称/顺序/图标可在后台菜单管理配置
    menuCatalogCode: 'system',
  },
]

/** 业务登记的在前、底座自有在后；组间顺序看 groupOrder，组内保持登记顺序 */
const sidebarDefinitions = computed(() => [...contributedNavItems(), ...coreSidebarDefinitions])

/** 配置化目录/单页入口的显示名优先取菜单树中的 menuName(后台可改名),树未就绪回退静态 label */
function displayLabel(item: SidebarDefinition): string {
  const code = item.menuCatalogCode ?? item.menuCode
  if (code) {
    const node = findMenuNode(auth.currentMenus, code)
    if (node?.menuName) {
      return node.menuName
    }
  }
  return item.label
}

const navItems = computed(() => {
  return sidebarDefinitions.value.filter((item) => {
    // 单页入口：菜单节点没授给当前角色就不出现（后端照样拦，但别留一个点进去整页 403 的入口）
    if (item.menuCode) {
      return findMenuNode(auth.currentMenus, item.menuCode) !== null
    }
    if (item.alwaysVisible) {
      return true
    }
    // 其余顶层项(当前仅系统目录):至少有一个可见子项才显示
    return hasVisibleChild(item)
  })
})

/** 分组由导航项自带的 group/groupLabel/groupOrder 推导，没有可见导航项的组不出现 */
const navGroups = computed(() => {
  const buckets: Array<{
    key: string
    label: string
    order: number
    items: SidebarDefinition[]
  }> = []
  for (const item of navItems.value) {
    let bucket = buckets.find((entry) => entry.key === item.group)
    if (!bucket) {
      bucket = {
        key: item.group,
        label: item.groupLabel,
        order: item.groupOrder ?? 100,
        items: [],
      }
      buckets.push(bucket)
    }
    bucket.items.push(item)
  }
  return buckets.sort((a, b) => a.order - b.order)
})

/** ================= 横幅上下文(当前页在导航里的位置) ================= */

/** 当前路由命中的导航位置:子页优先,其次顶层直达项;都没命中就是空,由 pageTitle 兜底 */
const currentPageTrail = computed(() => {
  for (const group of navGroups.value) {
    for (const item of group.items) {
      const child = resolveVisibleChildren(item).find((entry) => isNavItemActive(entry))
      if (child) {
        return { groupLabel: group.label, parentLabel: displayLabel(item), label: child.label }
      }
      if (isNavItemActive(item)) {
        return { groupLabel: group.label, parentLabel: '', label: displayLabel(item) }
      }
    }
  }
  return { groupLabel: '', parentLabel: '', label: '' }
})

/** 面包屑只装"来路",末级名已经在横幅大标题上,不重复第三遍;组名与目录名同名时只留一处 */
const crumbText = computed(() => {
  const trail = currentPageTrail.value
  return [...new Set([trail.groupLabel, trail.parentLabel].filter(Boolean))].join(' / ')
})

const pageTitle = computed(
  () =>
    currentPageTrail.value.label ||
    tabs.tabs.find((tab) => tab.path === route.path)?.label ||
    titleOfPath(route.path) ||
    brandName.value
)

/** 窄栏把二级页收进悬停浮层,搜索面板就是它的平铺目录:直达项与目录子页都在内 */
const searchablePages = computed<SearchablePage[]>(() => {
  const list: SearchablePage[] = []
  const seen = new Set<string>()
  for (const group of navGroups.value) {
    for (const item of group.items) {
      const parent = displayLabel(item)
      const children = resolveVisibleChildren(item)
      if (!children.length) {
        if (!seen.has(item.to)) {
          seen.add(item.to)
          list.push({
            label: parent,
            groupLabel: group.label,
            parentLabel: '',
            to: item.to,
            icon: item.icon,
          })
        }
        continue
      }
      for (const child of children) {
        if (seen.has(child.to)) {
          continue
        }
        seen.add(child.to)
        list.push({
          label: child.label,
          groupLabel: group.label,
          parentLabel: parent,
          to: child.to,
          icon: child.icon,
        })
      }
    }
  }
  return list
})

/** ================= 当前页匹配(页签标题 / 高亮) ================= */

/**
 * 路径 → 菜单节点标题(子项优先于父项)。菜单树还没就绪时返回 null,
 * 由调用方回退到页签登记时记下的标题。
 */
function titleOfPath(path: string): string | null {
  for (const item of sidebarDefinitions.value) {
    for (const child of resolveVisibleChildren(item)) {
      if (child.prefixes.some((prefix) => path.startsWith(prefix))) {
        return child.title
      }
    }
  }
  const matched = sidebarDefinitions.value.find((item) =>
    item.prefixes.some((prefix) => path.startsWith(prefix))
  )
  if (!matched) {
    return null
  }
  // 带 menuCode 的顶层项与目录子项同口径:标题跟着菜单节点走,后台改名页签同步
  const nodeName = matched.menuCode
    ? findMenuNode(auth.currentMenus, matched.menuCode)?.menuName
    : null
  return nodeName || matched.title
}

/** ================= 多页签(切页不丢现场) ================= */

const tabs = useTabsStore()

/** 路由落定即登记页签;纯 redirect 记录没有组件,不占页签 */
watch(
  () => route.fullPath,
  () => {
    if (!route.name) {
      return
    }
    const page = {
      path: route.path,
      fullPath: route.fullPath,
      label: titleOfPath(route.path) || '',
    }
    tabs.open(page)
    tabs.rememberVisit(page)
  },
  { immediate: true }
)

/** 菜单树晚到或后台改过菜单名:所有页签(含未激活的)按最新菜单重算标题 */
watch(
  () => auth.currentMenus,
  () => {
    for (const tab of tabs.tabs) {
      const title = titleOfPath(tab.path)
      if (title) {
        tab.label = title
      }
    }
    tabs.renameVisits(titleOfPath)
  }
)

/** 本机页签偏好:与菜单偏好一样只存当前浏览器,不随账号同步 */
watch(
  () => tabs.tabs,
  () => tabs.persist(),
  { deep: true }
)

/**
 * 切换/重置机构后让全部页签换 key:失活页面的 watch 在缓存期间不会补跑,
 * 光靠它们自己拉数会把上一个机构的数据留在页签里。
 */
watch(
  () => institutionStore.currentInstitutionId,
  (id, prevId) => {
    if (prevId && id !== prevId) {
      tabs.refreshAll()
    }
  }
)

/** 恢复出来的页签/历史记录可能指向已卸载模块的路由,进不去就不留着 */
function isReachable(path: string): boolean {
  return router.resolve(path).matched.some((record) => record.name)
}

onMounted(() => {
  for (const tab of [...tabs.tabs]) {
    if (!isReachable(tab.path)) {
      tabs.closeGroup('single', tab.path)
    }
  }
  tabs.pruneHistory(isReachable)
})

function activateTab(path: string) {
  const tab = tabs.tabs.find((item) => item.path === path)
  if (!tab || path === route.path) {
    return
  }
  router.push(tab.fullPath)
}

/** 关闭一条:× 按钮、鼠标中键、右键菜单的"关闭当前"共用 */
function closeTab(path: string) {
  const target = tabs.closeGroup('single', path)
  if (target) {
    router.push(target)
  }
}

function onTabAuxClick(path: string, event: MouseEvent) {
  // 浏览器习惯:中键点页签即关闭
  if (event.button === 1 && tabs.closable) {
    closeTab(path)
  }
}

/** ================= 页签右键菜单 ================= */

/** 菜单里的动作:刷新不改页签列表,其余四种是按被右键页签算的关闭范围 */
type TabMenuAction = 'refresh' | TabCloseMode

const TAB_MENU_WIDTH = 152
const TAB_MENU_HEIGHT = 172

const tabMenu = ref<{ path: string; x: number; y: number } | null>(null)

/** 被右键页签的位置:左侧/右侧两项的可用范围按它算 */
const tabMenuIndex = computed(() =>
  tabMenu.value ? tabs.tabs.findIndex((item) => item.path === tabMenu.value?.path) : -1
)

const tabMenuActions = computed<Array<{ label: string; action: TabMenuAction; enabled: boolean }>>(
  () => {
    const index = tabMenuIndex.value
    const total = tabs.tabs.length
    return [
      { label: '刷新页面', action: 'refresh', enabled: true },
      { label: '关闭当前', action: 'single', enabled: total > 1 },
      { label: '关闭其他', action: 'others', enabled: total > 1 },
      { label: '关闭左侧', action: 'left', enabled: index > 0 },
      { label: '关闭右侧', action: 'right', enabled: index >= 0 && index < total - 1 },
    ]
  }
)

function openTabMenu(tab: PageTab, event: MouseEvent) {
  tabMenu.value = {
    path: tab.path,
    x: Math.max(8, Math.min(event.clientX, window.innerWidth - TAB_MENU_WIDTH - 8)),
    y: Math.max(8, Math.min(event.clientY, window.innerHeight - TAB_MENU_HEIGHT - 8)),
  }
}

function runTabMenu(action: TabMenuAction) {
  const target = tabMenu.value
  tabMenu.value = null
  if (!target) {
    return
  }
  if (action === 'refresh') {
    tabs.refresh(target.path)
    return
  }
  const next = tabs.closeGroup(action, target.path)
  if (next) {
    router.push(next)
  }
}

/** ================= 页签拖拽排序 ================= */

const draggingIndex = ref<number | null>(null)

function onTabDragStart(index: number, event: DragEvent) {
  draggingIndex.value = index
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    // Firefox 要求先 setData 才允许拖拽,值本身用不上
    event.dataTransfer.setData('text/plain', tabs.tabs[index]?.path ?? '')
  }
}

function onTabDrop(index: number) {
  if (draggingIndex.value !== null) {
    tabs.move(draggingIndex.value, index)
  }
  draggingIndex.value = null
}

/** ================= 最近查看 ================= */

/** 行尾那个入口的位置;菜单是右对齐挂在入口下面的 */
const HISTORY_MENU_WIDTH = 220

const historyButtonRef = ref<HTMLElement | null>(null)
const historyMenu = ref<{ x: number; y: number } | null>(null)

/** 已经开着的历史记录只是切过去,不另开页签,所以给个区别标记 */
const openedPaths = computed(() => new Set(tabs.tabs.map((tab) => tab.path)))

function toggleHistoryMenu() {
  if (historyMenu.value) {
    historyMenu.value = null
    return
  }
  const rect = historyButtonRef.value?.getBoundingClientRect()
  if (!rect) {
    return
  }
  tabMenu.value = null
  historyMenu.value = {
    x: Math.max(8, rect.right - HISTORY_MENU_WIDTH),
    y: rect.bottom + 6,
  }
}

/** 点历史记录:没开过就等于新开一页,开过就是切回它最近那次的位置 */
function goVisited(page: VisitedPage) {
  historyMenu.value = null
  if (page.path === route.path) {
    return
  }
  router.push(page.fullPath)
}

/** 页签多到溢出时,切过去的页签要自己滚进可视区(block: nearest 避免顺带把页面滚走) */
const tabStripRef = ref<HTMLElement | null>(null)

watch(
  () => tabs.activePath,
  async () => {
    await nextTick()
    tabStripRef.value
      ?.querySelector('.app-tab.is-active')
      ?.scrollIntoView({ inline: 'nearest', block: 'nearest' })
  }
)

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
    const activeParent = sidebarDefinitions.value.find(
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

/** 默认走图标导航:横幅已经给出当前页与来路,宽栏那 232px 留给内容表 */
function readStoredNavMode(): NavMode {
  if (typeof window === 'undefined') {
    return 'rail'
  }
  return window.localStorage.getItem(NAV_MODE_STORAGE_KEY) === 'full' ? 'full' : 'rail'
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

/** 点击侧边栏之外的区域:收起钉住的窄栏浮层(pin-outside)、smart 模式下展开的组,以及页签右键菜单 */
function handleDocumentClick(event: MouseEvent) {
  const target = event.target as HTMLElement | null
  if (tabMenu.value && !target?.closest('.tab-context-menu')) {
    tabMenu.value = null
  }
  // 入口自身那一击交给 toggleHistoryMenu 处理,否则点了打不开
  if (historyMenu.value && !target?.closest('.app-history-menu, .app-tab-history')) {
    historyMenu.value = null
  }
  const insideNav = !!target?.closest('.app-nav')
  if (
    pinnedRailKey.value &&
    menuPreferences.value.railPopover === 'pin-outside' &&
    !insideNav
  ) {
    pinnedRailKey.value = null
  }
  if (menuPreferences.value.groupExpand === 'smart' && !insideNav && openGroups.value.size) {
    openGroups.value = new Set()
  }
}

function handleDocumentKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    tabMenu.value = null
    historyMenu.value = null
    searchOpen.value = false
    return
  }
  // Ctrl/⌘+K:窄栏的二级页藏在悬停浮层里,键盘直达是它的兜底入口
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    tabMenu.value = null
    historyMenu.value = null
    searchOpen.value = !searchOpen.value
  }
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
  document.addEventListener('keydown', handleDocumentKeydown)
})

onUnmounted(() => {
  document.removeEventListener('click', handleDocumentClick)
  document.removeEventListener('keydown', handleDocumentKeydown)
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
  // 页签属于上一个账号的浏览现场,连同缓存一起清掉
  tabs.clear()
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
