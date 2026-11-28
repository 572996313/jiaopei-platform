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

        <!-- 业务登记的范围芯片（校区一类跟着机构走的工作范围），紧跟机构芯片、读起来是「机构 / 校区」一组 -->
        <component v-for="chip in bannerChips" :is="chip.component" :key="chip.key" />

        <el-dropdown trigger="click" @command="handleUserCommand">
          <div class="banner-chip banner-user">
            <UserAvatar
              class="banner-user-avatar"
              :file-id="auth.currentUser?.avatarFileId"
              :name="auth.displayName"
              :size="25"
            />
            <div class="banner-user-info">
              <div class="banner-user-name">{{ auth.displayName }}</div>
              <div class="banner-user-meta">{{ roleSummary }}</div>
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
              <el-dropdown-item command="profile" divided>个人信息</el-dropdown-item>
              <el-dropdown-item command="menu-preferences">菜单偏好</el-dropdown-item>
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
            <div v-if="!isGroupTitleRedundant(group)" class="nav-group-title">{{ group.label }}</div>
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

      <!--
        图标导航(窄模式 / rail)：只放「常用 + 收藏」，不再铺全部分组。
        常用是最近点过的页面（位置进去就固定，不随点击重排），收藏是在「全部功能」面板里点星加进来的，
        收藏项可以上下拖动调整顺序。
        每一项都是可直达的叶子页面，**悬停只弹一个页面名**（不弹二级页清单）：
        不少菜单没配图标、图标长得一样，名字是唯一的辨识手段，所以用 0 延迟的 tooltip 而不是
        浏览器原生 title（原生要悬停一秒才出，客户等不到）。系统里还有什么，走列底那颗「全部功能」。
      -->
      <aside v-else class="app-nav is-rail">
        <nav class="rail-nav" @scroll.passive="railMenu = null">
          <div v-if="recentRailPages.length" class="rail-group-items">
            <div class="rail-section-label">常用</div>
            <el-tooltip
              v-for="page in recentRailPages"
              :key="page.to"
              v-bind="RAIL_TIP"
              :content="page.label"
            >
              <RouterLink
                :to="page.to"
                class="rail-trigger"
                :class="{ 'is-active': isPagePathActive(page.to) }"
                :aria-label="page.label"
                @contextmenu.prevent="openRailMenu($event, page, 'recent')"
              >
                <el-icon class="rail-icon"><component :is="page.icon || Document" /></el-icon>
              </RouterLink>
            </el-tooltip>
          </div>

          <div v-if="recentRailPages.length" class="rail-group-gap" />

          <div class="rail-group-items">
            <div class="rail-section-label">收藏</div>
            <!--
              收藏项可上下拖动排序。外层只负责拖拽命中区和插入线，里面仍然是原来的直达链接：
              锚点显式 draggable="false"，否则浏览器原生的"拖链接"会抢走手势，拖出来是一个网址
              而不是这颗图标。拖动中把 tooltip 关掉，免得名称标签跟着插入线一起闪。
            -->
            <div
              v-for="(page, index) in favoriteRailPages"
              :key="page.to"
              class="rail-drag-item"
              :class="{
                'is-dragging': dragFavoriteIndex === index,
                'is-drop-before': dropFavoriteIndex === index,
                'is-drop-after':
                  index === favoriteRailPages.length - 1 &&
                  dropFavoriteIndex === favoriteRailPages.length,
              }"
              draggable="true"
              @dragstart="onFavoriteDragStart(index, $event)"
              @dragover.prevent="onFavoriteDragOver(index, $event)"
              @drop.prevent="onFavoriteDrop"
              @dragend="onFavoriteDragEnd"
            >
              <el-tooltip
                v-bind="RAIL_TIP"
                :content="page.label"
                :disabled="dragFavoriteIndex !== null"
              >
                <RouterLink
                  :to="page.to"
                  class="rail-trigger"
                  :class="{ 'is-active': isPagePathActive(page.to) }"
                  :aria-label="page.label"
                  draggable="false"
                  @contextmenu.prevent="openRailMenu($event, page, 'favorite')"
                >
                  <el-icon class="rail-icon"><component :is="page.icon || Document" /></el-icon>
                </RouterLink>
              </el-tooltip>
            </div>
            <el-tooltip
              v-bind="RAIL_TIP"
              :content="favoriteRailPages.length ? '管理收藏' : '还没有收藏，点这里挑页面'"
            >
              <button
                type="button"
                class="rail-trigger rail-add"
                aria-label="管理收藏"
                @click="allPagesOpen = true"
              >
                <el-icon class="rail-icon"><Plus /></el-icon>
              </button>
            </el-tooltip>
          </div>
        </nav>

        <!--
          列底的「全部功能」：左侧不再铺全部分组，这里是"看全貌"的唯一入口。
          钉在滚动区之外，常用/收藏再多也不会把它挤出屏幕。
        -->
        <div class="rail-tail">
          <el-tooltip v-bind="RAIL_TIP" content="全部功能">
            <button
              type="button"
              class="rail-trigger"
              aria-label="全部功能"
              @click="allPagesOpen = true"
            >
              <el-icon class="rail-icon"><Grid /></el-icon>
            </button>
          </el-tooltip>
        </div>
      </aside>

      <div class="app-main">
        <!-- 只认强制改密总开关的生效值(auth.mustChangePassword)。
             passwordResetRequired 是库里"初始密码未轮换"的原始事实,不受总开关控制;
             拿它兜底会让"关闭总开关后仍挂着初始化密码横幅"永远关不掉。 -->
        <el-alert
          v-if="auth.mustChangePassword"
          class="app-alert"
          type="error"
          show-icon
          :closable="false"
          title="当前账号已被强制跳转至改密流程,完成修改前不能进入业务首页。"
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

    <!-- ============ 窄栏图标右键菜单：常用里收藏、收藏里取消/上移/下移 ============ -->
    <div
      v-if="railMenu"
      class="tab-context-menu rail-context-menu"
      :style="{ left: `${railMenu.x}px`, top: `${railMenu.y}px` }"
    >
      <div class="rail-context-title">{{ railMenu.page.label }}</div>
      <button type="button" class="tab-context-item" @click="runRailMenuAction">
        {{ railMenuActionLabel }}
      </button>
      <!--
        收藏可拖动排序，这里补一条不依赖拖拽的路径：触控板不好拖、键盘用户用不了拖拽。
        菜单不自动关，连点可以一格一格挪。
      -->
      <template v-if="railMenu.kind === 'favorite'">
        <button
          type="button"
          class="tab-context-item"
          :disabled="railFavoriteIndex <= 0"
          @click="moveRailFavorite(-1)"
        >
          上移
        </button>
        <button
          type="button"
          class="tab-context-item"
          :disabled="railFavoriteIndex < 0 || railFavoriteIndex >= favoriteRailPages.length - 1"
          @click="moveRailFavorite(1)"
        >
          下移
        </button>
      </template>
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
      <template #footer>
        <el-button @click="menuPreferencesVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ============ 个人信息：常用资料 + 换头像 + 快捷改密 ============ -->
    <UserProfileDialog v-model="profileDialogVisible" />

    <!-- ============ 全部功能：可拖可缩的大面板，收藏也在这里点 ============ -->
    <AllPagesDialog v-model="allPagesOpen" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowDown,
  Check,
  Close,
  Document,
  Grid,
  Monitor,
  Moon,
  Plus,
  Search,
  Sunny,
} from '@element-plus/icons-vue'
import AllPagesDialog from './AllPagesDialog.vue'
import NavSearchPalette from './NavSearchPalette.vue'
import UserAvatar from '@cornerstone/core/components/UserAvatar.vue'
import UserProfileDialog from '@cornerstone/core/components/UserProfileDialog.vue'
import {
  contributedBannerActions,
  contributedBannerChips,
} from '@cornerstone/core/extension/module-extensions'
import { useBranding } from '@cornerstone/core/composables/use-branding'
import { useFavoritePages } from '@cornerstone/core/composables/use-favorites'
import { useTheme, type ThemeMode } from '@cornerstone/core/composables/use-theme'
import {
  findMenuNode,
  pathMatchesPrefix,
  useNavModel,
  type SearchablePage,
  type SidebarChildDefinition,
  type SidebarDefinition,
} from '@cornerstone/core/composables/use-nav-model'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import {
  MAX_CACHED_PAGES,
  useTabsStore,
  type PageTab,
  type TabCloseMode,
  type VisitedPage,
} from '@cornerstone/core/stores/tabs'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const institutionStore = useInstitutionStore()

// 第三方账号绑定完成会带回 ?identityBound=渠道；提示一次后从地址里清掉，刷新不再重复弹
watch(
  () => route.query.identityBound,
  (value) => {
    if (typeof value !== 'string' || !value) {
      return
    }
    ElMessage.success('第三方账号已绑定')
    const query = { ...route.query }
    delete query.identityBound
    router.replace({ path: route.path, query })
  },
  { immediate: true }
)

/** 顶栏胶囊只放得下一行：优先角色中文名，接口没给名称时回落角色码 */
const roleSummary = computed(() => {
  const names = auth.currentUser?.roleNames
  if (names?.length) {
    return names.join(' / ')
  }
  return auth.currentUser?.roles?.join(' / ') || '未分配角色'
})

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

interface MenuPreferences {
  groupExpand: GroupExpandMode
}

const MENU_PREFS_STORAGE_KEY = 'cornerstone-menu-preferences'
const defaultMenuPreferences: MenuPreferences = { groupExpand: 'smart' }

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
    }
  } catch {
    return { ...defaultMenuPreferences }
  }
}

const menuPreferences = ref<MenuPreferences>(readStoredMenuPreferences())
const menuPreferencesVisible = ref(false)
const profileDialogVisible = ref(false)
const searchOpen = ref(false)
/** 「全部功能」大面板：左侧窄栏看全貌的唯一入口，也是收藏页面的地方 */
const allPagesOpen = ref(false)

/** 顶栏业务入口：现算而不是加载时取快照，登记晚于外壳挂载也要出现 */
const bannerActions = computed(() => contributedBannerActions())
/** 顶栏范围芯片（校区一类），落在机构芯片右侧 */
const bannerChips = computed(() => contributedBannerChips())

watch(
  menuPreferences,
  (prefs) => {
    window.localStorage.setItem(MENU_PREFS_STORAGE_KEY, JSON.stringify(prefs))
  },
  { deep: true }
)

/** ================= 导航数据模型(宽窄两态共用) ================= */

/**
 * 菜单树 → 分组 → 页面的推导整体收在 `composables/use-nav-model.ts`。
 * 「全部功能」面板(AllPagesDialog)、Ctrl+K 搜索、窄栏的常用/收藏渲染的都是同一份数据——
 * 各算一遍就会出现"目录里有的页面侧边栏没有、或者顺序不一致"，那正是前端第二份导航事实源的老毛病。
 */
const {
  sidebarDefinitions,
  navGroups,
  searchablePages,
  pageByPath,
  resolveVisibleChildren,
  hasVisibleChild,
  displayLabel,
  isGroupTitleRedundant,
} = useNavModel()

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
        // 顶层节点自己就是一张页面时（如公告中心），组名与页名同源，来路不再重复一遍
        return {
          groupLabel: isGroupTitleRedundant(group) ? '' : group.label,
          parentLabel: '',
          label: displayLabel(item),
        }
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
    // 不在菜单树里的固定页自带 meta.title，别让它显示成产品名
    (route.meta.title as string | undefined) ||
    brandName.value
)

/** ================= 当前页匹配(页签标题 / 高亮) ================= */

/**
 * 路径 → 菜单节点标题(子项优先于父项)。菜单树还没就绪时返回 null,
 * 由调用方回退到页签登记时记下的标题。
 */
function titleOfPath(path: string): string | null {
  for (const item of sidebarDefinitions.value) {
    for (const child of resolveVisibleChildren(item)) {
      if (child.prefixes.some((prefix) => pathMatchesPrefix(path, prefix))) {
        return child.title
      }
    }
  }
  const matched = sidebarDefinitions.value.find((item) =>
    item.prefixes.some((prefix) => pathMatchesPrefix(path, prefix))
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
      // 不在菜单树里的固定页用路由自带的标题，否则页签显示成空白路径
      label: titleOfPath(route.path) || (route.meta.title as string | undefined) || '',
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
  return item.prefixes.some((prefix) => pathMatchesPrefix(route.path, prefix))
}

function isChildActive(child: SidebarChildDefinition) {
  return child.prefixes.some((prefix) => pathMatchesPrefix(route.path, prefix))
}

/** 完整模式:可展开父菜单的展开态,策略由「菜单偏好-组展开策略」控制 */
const openGroups = ref(new Set<string>())

function routeMatchesItem(item: { prefixes: string[] }) {
  return item.prefixes.some((prefix) => pathMatchesPrefix(route.path, prefix))
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
 * 窄栏的 常用 + 收藏。
 *
 * - 常用：最近点过的页面（`stores/tabs.ts` 的 history 决定谁进谁出），**位置进去就固定**，
 *   不按最后访问时间重排；
 * - 收藏：在「全部功能」面板里点星加进来的，新的追加在末尾，可上下拖动排序。
 *
 * 两边都只存路径，名称与图标从 `pageByPath` 实时取——菜单改名、换图标就跟着变；
 * 查不到（授权被收回、页面下线）就不渲染。收藏优先：同一页同时在这两段里时只留在收藏段，
 * 免得窄栏里挂两颗一样的图标。
 */
const MAX_RECENT_RAIL_PAGES = 6

/**
 * 窄栏悬停名称的统一参数。用 Element Plus tooltip 而不是原生 `title`：
 * - `showAfter: 0`：原生 title 要悬停约一秒才冒出来，客户等不到；图标没配或长得一样时，
 *   名字是唯一的辨识手段，必须"放上去就看见"；
 * - `hideAfter: 0`：相邻图标之间移动时不让上一条继续悬着，避免两个标签同时出现；
 * - `enterable: false`：它只是一个名称标签，不该能移进去停留。
 */
const RAIL_TIP = {
  placement: 'right' as const,
  effect: 'light' as const,
  popperClass: 'rail-tip',
  showAfter: 0,
  hideAfter: 0,
  enterable: false,
}

const { favoritePaths, toggleFavorite, moveFavorite } = useFavoritePages()

/**
 * 常用栏的位置存本机：只存"图标按什么顺序排"，名称与图标仍从菜单树实时取。
 * 换账号后成员按新的浏览历史重算，旧顺序里查不到的路径自然被丢掉。
 */
const RECENT_ORDER_STORAGE_KEY = 'cornerstone-rail-recent-order'

function readStoredRecentOrder(): string[] {
  if (typeof window === 'undefined') {
    return []
  }
  try {
    const stored = JSON.parse(window.localStorage.getItem(RECENT_ORDER_STORAGE_KEY) || '[]')
    return Array.isArray(stored)
      ? stored.filter((item): item is string => typeof item === 'string')
      : []
  } catch {
    return []
  }
}

const recentOrder = ref<string[]>(readStoredRecentOrder())

/**
 * 常用的成员：最近点过的几个页面（去掉已收藏的，同一页只在收藏段出现一次）。
 * **谁进谁出**照旧按最近访问算——最久没点的那个让位给新页面。
 */
const recentMembers = computed<SearchablePage[]>(() => {
  const favoriteSet = new Set(favoritePaths.value)
  const pages: SearchablePage[] = []
  for (const item of tabs.history) {
    if (pages.length >= MAX_RECENT_RAIL_PAGES) {
      break
    }
    if (favoriteSet.has(item.path)) {
      continue
    }
    const page = pageByPath.value.get(item.path)
    if (page) {
      pages.push(page)
    }
  }
  return pages
})

/**
 * 常用栏的位置一旦定下就不再动：新进列表的排在末尾，其余保持原位。
 *
 * 以前直接按"最后访问时间"排，每点一个页面整列就重排一次——鼠标底下那颗图标刚认熟就换了
 * 位置，客户的原话是"跳来跳去看不懂"。位置稳定比"最近的在最上面"重要得多：
 * 频繁用的页面靠"不会被挤出去"留在常用里，而不是靠往上跳。
 */
const recentRailPages = computed<SearchablePage[]>(() => {
  const members = recentMembers.value
  const byPath = new Map(members.map((page) => [page.to, page]))
  const ordered: SearchablePage[] = []
  for (const path of recentOrder.value) {
    const page = byPath.get(path)
    if (page) {
      ordered.push(page)
    }
  }
  const placed = new Set(ordered.map((page) => page.to))
  for (const page of members) {
    if (!placed.has(page.to)) {
      ordered.push(page)
    }
  }
  return ordered
})

// 把算出来的顺序写回本机：只在成员真的增删时写，日常导航不碰存储
watch(
  recentRailPages,
  (pages) => {
    const next = pages.map((page) => page.to)
    const changed =
      next.length !== recentOrder.value.length ||
      next.some((path, index) => path !== recentOrder.value[index])
    if (changed) {
      recentOrder.value = next
      if (typeof window !== 'undefined') {
        window.localStorage.setItem(RECENT_ORDER_STORAGE_KEY, JSON.stringify(next))
      }
    }
  },
  { immediate: true }
)

const favoriteRailPages = computed<SearchablePage[]>(() =>
  favoritePaths.value
    .map((path) => pageByPath.value.get(path))
    .filter((page): page is SearchablePage => Boolean(page))
)

/**
 * 收藏拖动排序。目标是"插到哪一格之前"，不是两两交换——交换在长列表里对不准，
 * 插入线能直接看见落点。`dropFavoriteIndex` 取 0..length，等于 length 表示落到最后。
 * 拖动结束浏览器不会再补发 click（指针移动过的话），所以不会误打开页面。
 */
const dragFavoriteIndex = ref<number | null>(null)
const dropFavoriteIndex = ref<number | null>(null)

function onFavoriteDragStart(index: number, event: DragEvent) {
  dragFavoriteIndex.value = index
  dropFavoriteIndex.value = null
  // Firefox 必须往 dataTransfer 里放点东西拖拽才会真正开始
  event.dataTransfer?.setData('text/plain', favoriteRailPages.value[index]?.to ?? '')
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

/** 指针在目标图标上半区就插到它前面，下半区就插到它后面 */
function onFavoriteDragOver(index: number, event: DragEvent) {
  if (dragFavoriteIndex.value === null) {
    return
  }
  const box = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const next = event.clientY > box.top + box.height / 2 ? index + 1 : index
  // 落回原位（自己前面/后面）不算移动：不画插入线，免得指向一个松手什么都不变的位置
  dropFavoriteIndex.value =
    next === dragFavoriteIndex.value || next === dragFavoriteIndex.value + 1 ? null : next
}

function onFavoriteDrop() {
  if (dragFavoriteIndex.value === null || dropFavoriteIndex.value === null) {
    return
  }
  moveFavorite(dragFavoriteIndex.value, dropFavoriteIndex.value)
  dragFavoriteIndex.value = null
  dropFavoriteIndex.value = null
}

/** 拖到列表外松手、或按 Esc 取消：清掉拖拽态，插入线不能留在那 */
function onFavoriteDragEnd() {
  dragFavoriteIndex.value = null
  dropFavoriteIndex.value = null
}

/** 页面级高亮：`/schedule` 命中 `/schedule` 与 `/schedule/xxx`，但不会串到 `/schedule-xxx` */
function isPagePathActive(path: string) {
  return pathMatchesPrefix(route.path, path)
}

/**
 * 窄栏图标的右键菜单：在「常用」里右键收藏、在「收藏」里右键取消收藏。
 *
 * 为什么用右键而不是"拖到收藏区"：68px 宽的列里拖拽既难瞄准又和点击打开抢手势，
 * 而且拖拽没有键盘路径；右键菜单是这个后台既有的一套交互（页签、最近查看都用它），
 * 客户不用重新学。发现成本靠「全部功能」面板顶部那句提示补上。
 */
/** 与 `.rail-context-menu` 的 width 保持一致，钳制右边界时按它算 */
const RAIL_MENU_WIDTH = 168

const railMenu = ref<{
  x: number
  y: number
  page: SearchablePage
  kind: 'recent' | 'favorite'
} | null>(null)

function openRailMenu(event: MouseEvent, page: SearchablePage, kind: 'recent' | 'favorite') {
  railMenu.value = {
    x: Math.max(8, Math.min(event.clientX, window.innerWidth - RAIL_MENU_WIDTH - 8)),
    y: Math.max(8, Math.min(event.clientY, window.innerHeight - 96)),
    page,
    kind,
  }
}

/** 菜单里那一条动作的文案：常用里是收藏，收藏里是取消 */
const railMenuActionLabel = computed(() =>
  railMenu.value?.kind === 'favorite' ? '取消收藏' : '收进收藏'
)

/** 右键的收藏项当前排在第几位：按路径实时算，拖动过、或别处删过收藏也能跟上 */
const railFavoriteIndex = computed(() =>
  railMenu.value?.kind === 'favorite'
    ? favoriteRailPages.value.findIndex((page) => page.to === railMenu.value?.page.to)
    : -1
)

/** 菜单里的上移/下移：`to` 是插入位，往下挪一档等于插到下一项的后面 */
function moveRailFavorite(offset: -1 | 1) {
  const index = railFavoriteIndex.value
  if (index < 0) {
    return
  }
  moveFavorite(index, offset < 0 ? index - 1 : index + 2)
}

function runRailMenuAction() {
  if (!railMenu.value) {
    return
  }
  toggleFavorite(railMenu.value.page.to)
  railMenu.value = null
}

/** 点击侧边栏之外的区域:收起 smart 模式下展开的组,以及页签右键菜单 */
function handleDocumentClick(event: MouseEvent) {
  const target = event.target as HTMLElement | null
  if (tabMenu.value && !target?.closest('.tab-context-menu')) {
    tabMenu.value = null
  }
  // 入口自身那一击交给 toggleHistoryMenu 处理,否则点了打不开
  if (historyMenu.value && !target?.closest('.app-history-menu, .app-tab-history')) {
    historyMenu.value = null
  }
  // 窄栏图标的右键菜单：点菜单本身要能执行动作，点别处才收起
  if (railMenu.value && !target?.closest('.rail-context-menu')) {
    railMenu.value = null
  }
  const insideNav = !!target?.closest('.app-nav')
  if (menuPreferences.value.groupExpand === 'smart' && !insideNav && openGroups.value.size) {
    openGroups.value = new Set()
  }
}

function handleDocumentKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    tabMenu.value = null
    historyMenu.value = null
    railMenu.value = null
    searchOpen.value = false
    return
  }
  // Ctrl/⌘+K：不打开「全部功能」面板也能直接跳页，习惯键盘的人不必点两次
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
  } else if (command === 'profile') {
    profileDialogVisible.value = true
  } else if (command === 'menu-preferences') {
    menuPreferencesVisible.value = true
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
