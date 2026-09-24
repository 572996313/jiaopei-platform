import { defineStore } from 'pinia'

/**
 * 后台多页签：一次会话内打开过的页面各占一个页签，切换时不丢现场（表单草稿、
 * 已加载的数据、滚动位置），刷新靠页签右键菜单，不做自动重拉。
 */

/** 一个访问过的页面：页签与「最近查看」共用这份形状（也是本机持久化的形状） */
export interface VisitedPage {
  /** 页面路径，页签与历史记录的唯一标识 */
  path: string
  /** 最近一次的完整地址（含查询参数），点回去能复原现场 */
  fullPath: string
  /** 标题，菜单树未就绪时的兜底显示 */
  label: string
}

/** 一个页签对应一个路径（查询参数只记最新一次，同一路径不会开出两个页签） */
export interface PageTab extends VisitedPage {
  /** keep-alive 组件 key 的后缀：加号即强制重挂，用于刷新与"关掉后重开要是新页面" */
  seq: number
}

const TABS_STORAGE_KEY = 'cornerstone-open-tabs'
const HISTORY_STORAGE_KEY = 'cornerstone-visited-pages'

/** 右键菜单的关闭范围（相对被右键的那个页签） */
export type TabCloseMode = 'single' | 'others' | 'left' | 'right'

/** 同时打开的页签上限，超出后淘汰最早打开且不是当前页的那个 */
export const MAX_OPEN_TABS = 10

/**
 * 「最近查看」条数上限。再多就不是"最近"了，翻找的成本比省下的点击更高，
 * 而且这份列表的用处很具体：找回被页签上限淘汰掉、或自己关掉的那几页。
 */
export const MAX_VISITED_PAGES = 20

/**
 * keep-alive 缓存条目上限。这里不用 include 按组件名收口（页签记的是路径，
 * 拿不到懒加载组件的 name），而是靠 LRU 兜底：关掉/刷新的页签会留下一个已失活的
 * 缓存实例，被后续访问挤出。留 2 个余量，保证当前页不会被挤出。
 */
export const MAX_CACHED_PAGES = MAX_OPEN_TABS + 2

function readStoredPages(key: string, limit: number): VisitedPage[] {
  if (typeof window === 'undefined') {
    return []
  }
  try {
    const stored = JSON.parse(window.localStorage.getItem(key) || '[]')
    if (!Array.isArray(stored)) {
      return []
    }
    return stored
      .filter(
        (item): item is VisitedPage =>
          !!item &&
          typeof item.path === 'string' &&
          item.path.startsWith('/') &&
          typeof item.fullPath === 'string' &&
          typeof item.label === 'string'
      )
      .slice(0, limit)
  } catch {
    return []
  }
}

function writePages(key: string, pages: VisitedPage[]) {
  if (typeof window === 'undefined') {
    return
  }
  window.localStorage.setItem(
    key,
    JSON.stringify(pages.map(({ path, fullPath, label }) => ({ path, fullPath, label })))
  )
}

/** 恢复时按路径去重，seq 从 0 起算（刷新缓存本来就随页面重载清空） */
const restoredTabs: PageTab[] = []
for (const item of readStoredPages(TABS_STORAGE_KEY, MAX_OPEN_TABS)) {
  if (!restoredTabs.some((tab) => tab.path === item.path)) {
    restoredTabs.push({ ...item, seq: 0 })
  }
}

const restoredHistory = readStoredPages(HISTORY_STORAGE_KEY, MAX_VISITED_PAGES)

export const useTabsStore = defineStore('tabs', {
  state: () => ({
    tabs: restoredTabs,
    /** 最近查看的页面，新的在前；不等于页签列表——页签关掉了这里还留着 */
    history: restoredHistory,
    activePath: '' as string,
    /** 每个路径用过的最大 seq，关掉重开要拿到新号，避免命中上次遗留的缓存实例 */
    seqSeed: {} as Record<string, number>,
  }),

  getters: {
    /** 只剩一个页签时不提供关闭，否则内容区会空着（底座不知道首页路径） */
    closable(): boolean {
      return this.tabs.length > 1
    },
  },

  actions: {
    /** 路由落定后登记页签：已存在就更新地址与标题，不存在则新开 */
    open(tab: VisitedPage) {
      const existing = this.tabs.find((item) => item.path === tab.path)
      if (existing) {
        existing.fullPath = tab.fullPath
        if (tab.label) {
          existing.label = tab.label
        }
      } else {
        const seq = (this.seqSeed[tab.path] ?? -1) + 1
        this.seqSeed[tab.path] = seq
        this.tabs.push({ ...tab, seq })
        if (this.tabs.length > MAX_OPEN_TABS) {
          const victim = this.tabs.find((item) => item.path !== tab.path)
          if (victim) {
            this.tabs.splice(this.tabs.indexOf(victim), 1)
          }
        }
      }
      this.activePath = tab.path
    },

    /** 当前页在 keep-alive 里的缓存 key；页签还没登记时按 seq 0 算，保证首屏不闪 */
    cacheKey(path: string): string {
      const tab = this.tabs.find((item) => item.path === path)
      return `${path}#${tab ? tab.seq : 0}`
    },

    /** 页签内刷新：换 key 让 keep-alive 认不出旧实例，等于重挂一个干净页面 */
    refresh(path: string) {
      const tab = this.tabs.find((item) => item.path === path)
      if (!tab) {
        return
      }
      tab.seq += 1
      this.seqSeed[tab.path] = tab.seq
    },

    /** 全部页签换号：切换机构后用，失活页签的 watch 不会补跑，只能靠重挂拿新数据 */
    refreshAll() {
      for (const tab of this.tabs) {
        this.refresh(tab.path)
      }
    },

    /**
     * 关闭页签：页签上的 × 与右键菜单的"关闭其他/左侧/右侧"都走这里。
     * 返回需要跳转的地址——只有当前页被关掉时才给（按索引就近取幸存页签，
     * 关掉一整侧时通常会落到被右键的那个页签），否则返回 null。
     * 不提供"关闭全部"：底座不知道首页在哪，关空了内容区就没东西了。
     */
    closeGroup(mode: TabCloseMode, path: string): string | null {
      const index = this.tabs.findIndex((item) => item.path === path)
      if (index < 0) {
        return null
      }
      let closing: string[]
      switch (mode) {
        case 'single':
          closing = [path]
          break
        case 'others':
          closing = this.tabs.filter((item) => item.path !== path).map((item) => item.path)
          break
        case 'left':
          closing = this.tabs.slice(0, index).map((item) => item.path)
          break
        default:
          closing = this.tabs.slice(index + 1).map((item) => item.path)
          break
      }
      const closed = new Set(closing)
      const survivors = this.tabs.filter((item) => !closed.has(item.path))
      const activeIndex = this.tabs.findIndex((item) => item.path === this.activePath)
      this.tabs = survivors
      if (!closed.has(this.activePath)) {
        return null
      }
      const next = survivors[Math.min(activeIndex, survivors.length - 1)]
      if (!next) {
        this.activePath = ''
        return null
      }
      this.activePath = next.path
      return next.fullPath
    },

    /** 拖拽排序：把 from 位置的页签搬到 to 位置 */
    move(from: number, to: number) {
      if (from === to || from < 0 || to < 0 || from >= this.tabs.length) {
        return
      }
      const [tab] = this.tabs.splice(from, 1)
      this.tabs.splice(Math.min(to, this.tabs.length), 0, tab)
    },

    /**
     * 记下最近查看的页面：同一路径只留最新一次并提到最前（按访问时间排，不是访问堆栈）。
     * 页签上限会淘汰、也会被自己关掉，这份记录就是"回得去刚才那几页"的那条线。
     */
    rememberVisit(page: VisitedPage) {
      const rest = this.history.filter((item) => item.path !== page.path)
      this.history = [page, ...rest].slice(0, MAX_VISITED_PAGES)
      writePages(HISTORY_STORAGE_KEY, this.history)
    },

    /** 菜单树晚到时补标题（历史记录可能是没标题时存下的） */
    renameVisits(labelOf: (path: string) => string | null) {
      let changed = false
      for (const item of this.history) {
        const label = labelOf(item.path)
        if (label && label !== item.label) {
          item.label = label
          changed = true
        }
      }
      if (changed) {
        writePages(HISTORY_STORAGE_KEY, this.history)
      }
    },

    /** 剪掉进不去的历史记录：恢复出来的条目可能指向已卸载的模块 */
    pruneHistory(isReachable: (path: string) => boolean) {
      const kept = this.history.filter((item) => isReachable(item.path))
      if (kept.length !== this.history.length) {
        this.history = kept
        writePages(HISTORY_STORAGE_KEY, this.history)
      }
    },

    /** 退出登录：页签和历史同属上一个账号的浏览现场，一起清掉 */
    clear() {
      this.tabs = []
      this.history = []
      this.activePath = ''
      this.seqSeed = {}
      writePages(HISTORY_STORAGE_KEY, this.history)
    },

    persist() {
      writePages(TABS_STORAGE_KEY, this.tabs)
    },
  },
})
