<template>
  <el-dialog
    v-model="visible"
    class="all-pages-dialog"
    title="全部功能"
    width="min(1080px, 92vw)"
    append-to-body
    @opened="focusSearch"
    @closed="keyword = ''"
  >
    <div class="all-pages-toolbar">
      <el-input
        ref="searchRef"
        v-model="keyword"
        class="all-pages-search"
        placeholder="搜页面名称，如「排课」「名册」"
        :prefix-icon="Search"
        clearable
      />
      <span class="all-pages-hint"
        >点右侧星标即可收进左侧导航；在左栏里右键「常用」图标也能收藏，收藏的图标可上下拖动排序。</span
      >
    </div>

    <div v-if="!groups.length" class="all-pages-empty">
      当前账号还没有任何可用页面的授权，请联系管理员分配角色。
    </div>
    <div v-else-if="!matchedGroups.length" class="all-pages-empty">
      没有匹配「{{ keyword }}」的页面。
    </div>

    <!--
      分组卡多列铺排：每组占满一整行时，一个只有两三个页面的组也横贯整行，
      全系统十几页要滚好几屏才看得完——那和"看不到全貌"是一回事。
    -->
    <div v-else class="all-pages-groups">
      <section v-for="group in matchedGroups" :key="group.key" class="all-pages-group">
        <div class="all-pages-group-head">
          <el-icon v-if="group.icon" class="all-pages-group-icon"
            ><component :is="group.icon"
          /></el-icon>
          <b class="all-pages-group-title">{{ group.label }}</b>
          <span class="all-pages-group-count">{{ group.pages.length }}</span>
        </div>

        <div class="all-pages-list">
          <div
            v-for="page in group.pages"
            :key="page.to"
            class="all-pages-item"
            :class="{ 'is-active': isPageActive(page.to) }"
          >
            <button type="button" class="all-pages-link" :title="page.to" @click="pick(page.to)">
              <el-icon v-if="page.icon" class="all-pages-item-icon"
                ><component :is="page.icon"
              /></el-icon>
              <span class="all-pages-item-text">
                <span class="all-pages-item-name">{{ page.label }}</span>
                <span v-if="page.subLabel" class="all-pages-item-sub">{{ page.subLabel }}</span>
              </span>
            </button>
            <button
              type="button"
              class="all-pages-star"
              :class="{ 'is-on': isFavorite(page.to) }"
              :title="isFavorite(page.to) ? '取消收藏' : '收进左侧收藏'"
              :aria-pressed="isFavorite(page.to)"
              @click.stop="toggleFavorite(page.to)"
            >
              <el-icon>
                <StarFilled v-if="isFavorite(page.to)" />
                <Star v-else />
              </el-icon>
            </button>
          </div>
        </div>
      </section>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, Star, StarFilled } from '@element-plus/icons-vue'
import type { ElInput } from 'element-plus'
import {
  matchesPageKeyword,
  pathMatchesPrefix,
  useNavModel,
  type NavPageGroup,
} from '@cornerstone/core/composables/use-nav-model'
import { useFavoritePages } from '@cornerstone/core/composables/use-favorites'

/**
 * 「全部功能」大面板。
 *
 * 存在理由：图标导航不再铺全部分组，客户需要一个"系统里到底有哪些页面"的可见答案。
 * 做成可拖动、可缩放的悬浮面板而不是整页——整页会把人从当前正在做的事里拽走，
 * 找完一个页面回去还得再点一次页签。
 *
 * 数据一律来自 `useNavModel()` 的 `pageGroups`——与侧边栏、搜索面板同源。
 * **不要在这里再按分组名/路径硬编码一份清单，也不要自己再遍历 navGroups**，
 * 那是本仓库反复踩过的"前端第二份导航事实源"，客户在后台改完这里就不跟着变了。
 */

/** 目录里多一行"来路"小字，组名与父目录同名时（单页自成一组的常态）不重复一遍 */
type PageCard = NavPageGroup['pages'][number] & { subLabel: string }

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const route = useRoute()
const router = useRouter()
const { pageGroups } = useNavModel()
const { isFavorite, toggleFavorite } = useFavoritePages()

const keyword = ref('')
const searchRef = ref<InstanceType<typeof ElInput> | null>(null)

const groups = computed<Array<Omit<NavPageGroup, 'pages'> & { pages: PageCard[] }>>(() =>
  pageGroups.value.map((group) => ({
    ...group,
    pages: group.pages.map((page) => ({
      ...page,
      subLabel: page.parentLabel === group.label ? '' : page.parentLabel,
    })),
  }))
)

/** 搜索时整组过滤：组里一条都没命中就不出现，命中的组保持菜单顺序 */
const matchedGroups = computed(() => {
  const term = keyword.value.trim()
  if (!term) {
    return groups.value
  }
  return groups.value
    .map((group) => ({
      ...group,
      pages: group.pages.filter((page) => matchesPageKeyword(page, term)),
    }))
    .filter((group) => group.pages.length > 0)
})

function isPageActive(path: string) {
  return pathMatchesPrefix(route.path, path)
}

/** 选定即收起面板：留着它挡住新页面没有意义，用户要看的是刚点开的那个页面 */
function pick(path: string) {
  visible.value = false
  if (path !== route.path) {
    router.push(path)
  }
}

function focusSearch() {
  searchRef.value?.focus()
}
</script>

<style scoped>
.all-pages-dialog :deep(.el-dialog__body) {
  /* 面板再大也不许顶出视口：内容在内部滚，标题和关闭按钮始终可见 */
  max-height: calc(100vh - 200px);
  padding-top: 6px;
  overflow-y: auto;
}

.all-pages-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.all-pages-search {
  flex: 0 0 auto;
  width: 280px;
}

.all-pages-hint {
  color: var(--text-secondary);
  font-size: 12.5px;
}

.all-pages-empty {
  padding: 32px 0;
  color: var(--text-secondary);
  text-align: center;
}

/*
 * 分组卡按最小宽度自适应列数：组多的系统三四列并排、一屏装下，
 * 组少的系统自然退回单列，不写死断点。
 * 用 auto-fit 而不是 auto-fill——组只有三个时 auto-fill 会留下空轨道，卡片挤在左边一条。
 */
.all-pages-groups {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 10px;
  /* 同一行的卡片等高：行数不同的两组并排时，卡片底边参差比空白更难看 */
  align-items: stretch;
}

.all-pages-group {
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-block);
  background: var(--card-bg);
}

.all-pages-group-head {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 22px;
}

.all-pages-group-icon {
  color: var(--accent);
  font-size: 16px;
}

.all-pages-group-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.all-pages-group-count {
  padding: 0 6px;
  border-radius: 999px;
  color: var(--text-secondary);
  font-size: 11.5px;
  background: var(--tint-bg);
}

/* 组内页面单列排：卡片本身已经分列，再嵌一层网格会把组内顺序读乱 */
.all-pages-list {
  display: grid;
  gap: 2px;
  margin-top: 6px;
}

.all-pages-item {
  display: flex;
  align-items: center;
  gap: 4px;
  border-radius: 8px;
}

.all-pages-item:hover {
  background: var(--hover-bg);
}

.all-pages-item.is-active {
  background: var(--nav-tint);
}

.all-pages-link {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 7px 8px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--nav-ink);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.all-pages-link:hover {
  color: var(--text-primary);
}

.all-pages-item.is-active .all-pages-link {
  color: var(--accent);
}

.all-pages-item-icon {
  flex-shrink: 0;
  font-size: 16px;
}

.all-pages-item-text {
  display: grid;
  min-width: 0;
  gap: 1px;
}

.all-pages-item-name {
  overflow: hidden;
  font-size: 13.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.all-pages-item-sub {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 11.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/*
 * 星标：平时半透明（不抢页名，但一眼知道这行可以收藏）；悬停到行上、或已收藏时常亮。
 * 不要默认完全隐藏——面板顶部的提示写着"点右侧星标"，星标却不出现，读起来像坏掉了。
 */
.all-pages-star {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  margin-right: 4px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--text-faint);
  opacity: 0.35;
  cursor: pointer;
  transition:
    opacity 0.12s,
    color 0.12s,
    background 0.12s;
}

.all-pages-item:hover .all-pages-star,
.all-pages-star.is-on,
.all-pages-star:focus-visible {
  opacity: 1;
}

.all-pages-star:hover {
  background: var(--tint-bg);
  color: var(--brand-gold-deep);
}

.all-pages-star.is-on {
  color: var(--brand-gold-deep);
}
</style>
