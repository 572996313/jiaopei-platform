import { computed, type Component } from 'vue'
import { Grid } from '@element-plus/icons-vue'
import type { SystemMenuTreeNode } from '@cornerstone/core/api/system'
import { resolveMenuIcon } from '@cornerstone/core/constants/menuIcons'
import {
  contributedNavItems,
  type NavItemDefinition,
} from '@cornerstone/core/extension/module-extensions'
import { useAuthStore } from '@cornerstone/core/stores/auth'

/**
 * 侧边栏导航的数据模型：当前用户已授权的菜单树 → 分组 → 页面。
 *
 * 侧边栏（AppLayout）、「全部功能」面板与 Ctrl+K 搜索（NavSearchPalette）**必须共用这一份**：
 * 两处各算一遍就会出现"目录里有的页面侧边栏没有、或者顺序不一致"，
 * 而这个仓库已经因为前端维护第二份导航事实源吃过一次亏（组名/顺序漂移）。
 *
 * 只算数据、不碰路由高亮与展开态——那些是 AppLayout 自己的交互状态。
 */

/** 一个可跳转的页面入口：label 是页名，来路由 groupLabel / parentLabel 拼出来 */
export interface SearchablePage {
  label: string
  groupLabel: string
  parentLabel: string
  to: string
  icon?: Component
}

/** 「全部功能」目录页的一张分组卡片：组名/顺序与侧边栏同源，组内是摊平的页面 */
export interface NavPageGroup {
  key: string
  label: string
  icon?: Component
  pages: SearchablePage[]
}

/**
 * 页面关键词匹配：空格分词、全部命中才算命中，顺序无关
 * （"排课 批量" 能找到"批量排课"）。
 * 侧边栏搜索面板与「全部功能」页共用，两处口径不一致时客户会以为搜不到就是没有那一页。
 */
export function matchesPageKeyword(item: SearchablePage, keyword: string): boolean {
  const terms = keyword.trim().toLowerCase().split(/\s+/).filter(Boolean)
  if (!terms.length) {
    return true
  }
  const haystack = `${item.label} ${item.groupLabel} ${item.parentLabel} ${item.to}`.toLowerCase()
  return terms.every((term) => haystack.includes(term))
}

export interface SidebarChildDefinition {
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
export type SidebarDefinition = NavItemDefinition

/** 后端菜单节点 → 侧边栏子项结构(名称/顺序/图标来自 sys_menu,可后台配置) */
export function adaptMenuChildren(
  nodes: SystemMenuTreeNode[],
  groupLabel = ''
): SidebarChildDefinition[] {
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
export function findMenuNode(
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

/** 目录没配图标的回落：窄栏里一个图标都看不见，等于这一组消失了 */
const FALLBACK_GROUP_ICON = Grid

/** 收集一个顶层节点下所有可点页面的路径（含子目录里的），用作整组的高亮前缀 */
function collectPagePaths(nodes: SystemMenuTreeNode[]): string[] {
  const paths: string[] = []
  for (const node of nodes) {
    if (node.menuType === 'menu' && node.path) {
      paths.push(node.path)
    }
    if (node.children?.length) {
      paths.push(...collectPagePaths(node.children))
    }
  }
  return paths
}

/**
 * 前缀按**路径段**匹配：`/schedule` 命中 `/schedule` 与 `/schedule/xxx`，
 * 但不命中 `/schedule-completeness`——否则完成度页的横幅标题、导航高亮和页签
 * 都会被排课页抢走，出现两个同名页签。
 */
export function pathMatchesPrefix(path: string, prefix: string) {
  return path === prefix || path.startsWith(prefix.endsWith('/') ? prefix : `${prefix}/`)
}

export function useNavModel() {
  const auth = useAuthStore()

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
   * 顶层导航段：**一个 `sys_menu` 顶层节点 = 侧边栏一组（一个图标）**。
   * 组名取 `menu_name`、组间顺序取 `sort_no`、图标取 `icon`、组内页面与顺序取子节点，
   * 全部来自当前用户已授权的菜单树——客户在「导航编排」页改完刷新即生效，不用改代码。
   * 之前这套顺序写死在各模块 `registerNavItems` 的 `groupOrder` 里，客户调 `sort_no` 界面不动。
   */
  const menuDrivenDefinitions = computed<SidebarDefinition[]>(() => {
    // 登记项的图标只作为"菜单节点没配图"时的回落，顺序与名称一概不从代码取
    const contributed = contributedNavItems()
    const list: SidebarDefinition[] = []
    for (const node of auth.currentMenus || []) {
      if (node.menuType === 'button' || !node.menuCode) {
        continue
      }
      const groupKey = `menu:${node.menuCode}`
      const pagePaths = collectPagePaths(node.children || [])
      if (node.menuType === 'catalog') {
        // 空目录不渲染：窄栏会出现一个点开没有内容的图标
        if (!pagePaths.length) {
          continue
        }
        list.push({
          key: groupKey,
          to: pagePaths[0],
          label: node.menuName,
          title: node.menuName,
          icon:
            resolveMenuIcon(node.icon) ??
            contributed.find((item) => item.menuCatalogCode === node.menuCode)?.icon ??
            FALLBACK_GROUP_ICON,
          group: groupKey,
          groupLabel: node.menuName,
          groupOrder: node.sortNo ?? 100,
          prefixes: pagePaths,
          menuCatalogCode: node.menuCode,
        })
        continue
      }
      if (!node.path) {
        continue
      }
      list.push({
        key: groupKey,
        to: node.path,
        label: node.menuName,
        title: node.menuName,
        icon:
          resolveMenuIcon(node.icon) ??
          contributed.find((item) => item.menuCode === node.menuCode)?.icon ??
          FALLBACK_GROUP_ICON,
        group: groupKey,
        groupLabel: node.menuName,
        groupOrder: node.sortNo ?? 100,
        prefixes: [node.path],
        menuCode: node.menuCode,
      })
    }
    return list
  })

  /**
   * 代码登记的入口：只留"没有菜单节点承载"的那些（工作台这类落地页，全站必进、不对应权限）。
   * **凡登记时写了 `menuCode`/`menuCatalogCode` 的，一律由上面那份菜单树自己出现**——
   * 同一个入口两处出现会在窄栏画两颗一样的图标，而"节点没授权"正是要它不出现。
   */
  const registeredDefinitions = computed(() =>
    contributedNavItems().filter((item) => !item.menuCode && !item.menuCatalogCode)
  )

  const sidebarDefinitions = computed(() => [
    ...menuDrivenDefinitions.value,
    ...registeredDefinitions.value,
  ])

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

  /**
   * 「一个 `sys_menu` 目录自成一组」时（业务导航的常态：组名就取自目录节点名），
   * 完整导航不再渲染组标题——否则标题与下面那颗可展开的目录按钮同名、各占一行。
   * 窄栏曾经也有同一条判断（浮层组头）——窄栏改成只放常用/收藏后，只剩宽栏这一处。
   */
  function isGroupTitleRedundant(group: { label: string; items: SidebarDefinition[] }): boolean {
    return group.items.length === 1 && displayLabel(group.items[0]) === group.label
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

  /**
   * 「分组 → 页面」的一层扁平投影。搜索面板（只要一串页面）与「全部功能」目录页
   * （要保留分组边界）都从这里取——两边各自再遍历一遍 navGroups，去重口径与顺序
   * 就会各写各的，最后表现成"目录里多一页 / 搜索里少一页"。
   */
  const pageGroups = computed<NavPageGroup[]>(() => {
    const groups: NavPageGroup[] = []
    // 同一路径可能既是顶层直达页、又是目录子页（历史上两种配置都出现过），只留第一次出现的那条
    const seen = new Set<string>()
    for (const group of navGroups.value) {
      const pages: SearchablePage[] = []
      for (const item of group.items) {
        const parent = displayLabel(item)
        const children = resolveVisibleChildren(item)
        if (!children.length) {
          if (!seen.has(item.to)) {
            seen.add(item.to)
            pages.push({
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
          pages.push({
            label: child.label,
            groupLabel: group.label,
            // 有二级目录时记二级目录名（如「系统设置 / 权限与账号」），它比顶层目录名更能定位；
            // 没有二级目录就退回顶层项名，页面再判断是否与组名重名
            parentLabel: child.groupLabel || parent,
            to: child.to,
            icon: child.icon,
          })
        }
      }
      if (pages.length) {
        groups.push({ key: group.key, label: group.label, icon: group.items[0]?.icon, pages })
      }
    }
    return groups
  })

  const searchablePages = computed<SearchablePage[]>(() =>
    pageGroups.value.flatMap((group) => group.pages)
  )

  /**
   * 路径 → 页面。左栏的「常用 / 收藏」只存路径（菜单改名、换图标要跟着变），
   * 渲染时要靠它把路径换回名称与图标；查不到说明这一页对当前账号已无授权。
   */
  const pageByPath = computed(() => {
    const map = new Map<string, SearchablePage>()
    for (const page of searchablePages.value) {
      map.set(page.to, page)
    }
    return map
  })

  return {
    auth,
    sidebarDefinitions,
    navGroups,
    pageGroups,
    searchablePages,
    pageByPath,
    resolveVisibleChildren,
    hasVisibleChild,
    displayLabel,
    isGroupTitleRedundant,
  }
}
