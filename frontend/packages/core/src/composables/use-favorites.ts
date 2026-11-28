import { readonly, ref } from 'vue'

/**
 * 页面收藏（本机浏览器）。
 *
 * 用途：图标窄栏不再铺全部分组，只放「常用 + 收藏」。用户在「全部功能」面板里
 * 给页面点星，收藏项就出现在左栏，一点直达。
 *
 * **只存路径**，名称与图标渲染时从菜单树实时取——客户在「导航编排」改了页名或图标，
 * 收藏项跟着变；存快照就会出现"后台改名、左栏还是旧名字"。
 * 与「最近查看」（`stores/tabs.ts`）不同：收藏是用户明确的选择，**退出登录不清**
 * （浏览记录会清）；渲染前按当前账号的授权页面表过滤，换个账号看不到别人的收藏页。
 */

const STORAGE_KEY = 'cornerstone-favorite-pages'

/**
 * 收藏上限。窄栏一列放不下几十颗，超出后挤掉**最早收藏**的那一条。
 * 30 是"够用但不会让人滚半天"的量级，与「最近查看」的 20 条同一思路。
 */
export const MAX_FAVORITE_PAGES = 30

function readStored(): string[] {
  if (typeof window === 'undefined') {
    return []
  }
  try {
    const stored = JSON.parse(window.localStorage.getItem(STORAGE_KEY) || '[]')
    if (!Array.isArray(stored)) {
      return []
    }
    return stored
      .filter((item): item is string => typeof item === 'string' && item.startsWith('/'))
      .slice(0, MAX_FAVORITE_PAGES)
  } catch {
    return []
  }
}

/**
 * 模块级单例：左栏与「全部功能」面板是两个组件，各自 `useFavoritePages()` 必须
 * 看到同一份数据，否则一边点星另一边不亮。用模块级 ref 而不是 Pinia store，
 * 是因为这份状态不需要进 devtools、也不参与 SSR，少一层依赖。
 */
const favoritePaths = ref<string[]>(readStored())

function persist() {
  if (typeof window === 'undefined') {
    return
  }
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(favoritePaths.value))
}

// 多标签页同步：另一个窗口改了收藏，这边跟着变（storage 事件只在其它窗口触发）
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (event) => {
    if (event.key === STORAGE_KEY) {
      favoritePaths.value = readStored()
    }
  })
}

export function useFavoritePages() {
  function isFavorite(path: string): boolean {
    return favoritePaths.value.includes(path)
  }

  /** 切换收藏，返回切换后的状态（true=已收藏），方便调用方给提示 */
  function toggleFavorite(path: string): boolean {
    if (isFavorite(path)) {
      favoritePaths.value = favoritePaths.value.filter((item) => item !== path)
      persist()
      return false
    }
    // 新的排最后：收藏区按"收藏先后"稳定排序，不会因为今天用得多就往上跳
    favoritePaths.value = [...favoritePaths.value, path].slice(-MAX_FAVORITE_PAGES)
    persist()
    return true
  }

  /**
   * 拖动排序：把第 `from` 条移到"插入位置" `to`（`to` 以移动前的列表为基准，取值 0..length）。
   * 落在自己身上、或紧邻自己的位置等于没动，直接返回，免得白白重写一次 localStorage。
   */
  function moveFavorite(from: number, to: number) {
    const list = [...favoritePaths.value]
    if (from < 0 || from >= list.length || to < 0 || to > list.length) {
      return
    }
    if (to === from || to === from + 1) {
      return
    }
    const [moved] = list.splice(from, 1)
    // 往下拖时，先摘掉自己会把后面的位置整体前移一格
    list.splice(to > from ? to - 1 : to, 0, moved)
    favoritePaths.value = list
    persist()
  }

  return {
    favoritePaths: readonly(favoritePaths),
    isFavorite,
    toggleFavorite,
    moveFavorite,
  }
}
