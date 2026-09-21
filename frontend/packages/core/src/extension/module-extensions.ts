import type { Component, VNode } from 'vue'
import type { ListColumnConfig } from '@cornerstone/core/components/ConfigurableList/types'
import type { RenderContext } from '@cornerstone/core/components/ConfigurableList/renderers'
import type { PermissionLabelItem } from '@cornerstone/core/constants/permissionLabels'

/**
 * 底座 ↔ 业务模块的扩展点注册表。
 *
 * 提供六类扩展点：列表单元格渲染器（业务列的回显）、权限码中文名（业务域「查看/维护」标签与分组）、
 * 侧边栏导航项（业务模块自己的页面入口）、品牌文案（壳工程登记本项目产物名）、
 * 机构切换广播（清业务筛选范围）、页面插槽（业务往别的模块页面里注入内容块）。
 * 底座页面会需要业务维度的数据，但底座包不允许 import 业务包，否则换行业时拆不干净。做法与后端的"事件 + SPI"一致：
 * 业务模块在自己入口调用 register*／on* 登记实现，底座只按名字查表调用；
 * 模块未装配（清单里没挂）时查不到实现，底座按空数据降级而不是报错。
 *
 * 注册发生在模块入口被 import 时，所以壳工程的模块清单一定要引到模块包，
 * 只挂路由不挂清单 = 扩展点没注册。
 */

/** 列表单元格渲染器：入参是行数据、列配置与宿主注入的渲染上下文，返回单元格 VNode */
export type ListCellRenderer = (
  row: Record<string, any>,
  column: ListColumnConfig,
  context?: RenderContext
) => VNode

export interface ListRendererDefinition {
  /** 列表配置里 renderer 字段引用的名字，要与 sys_list_config 中已存配置对齐 */
  name: string
  /** 配置编辑器下拉里的中文说明 */
  label: string
  render: ListCellRenderer
}

const listRenderers = new Map<string, ListRendererDefinition>()

/**
 * 业务模块登记自己的列表单元格渲染器（如按 id 回显业务对象名称的列）。
 * 名称数据由渲染器自己取（读模块的 store 或接口），底座只透传行数据与列配置；
 * 底座 renderCell 先查内置渲染器，找不到再按名字回退到这里；模块未装配时该列按普通值渲染。
 */
export function registerListRenderer(definition: ListRendererDefinition) {
  listRenderers.set(definition.name, definition)
}

export function getListRenderer(name: string): ListCellRenderer | undefined {
  return listRenderers.get(name)?.render
}

/** 配置编辑器的候选渲染器列表（底座内置项之外的模块贡献部分） */
export function listRendererChoices(): Array<{ value: string; label: string }> {
  return Array.from(listRenderers.values()).map((item) => ({
    value: item.name,
    label: item.label,
  }))
}

const permissionLabelContributions: PermissionLabelItem[] = []

/**
 * 业务模块登记自己域名的权限码中文名与分组（如 organization:write → 组织维护）。
 * 权限码的权威源是后端 sys_menu 按钮节点（/system/permission-options 动态返回），
 * 本表只在接口不可用时作离线兜底，以及给角色弹窗的权限网格提供分组名。
 */
export function registerPermissionLabels(items: PermissionLabelItem[]) {
  for (const item of items) {
    if (!permissionLabelContributions.some((existing) => existing.code === item.code)) {
      permissionLabelContributions.push(item)
    }
  }
}

/** 底座读取业务贡献的权限码标签（按登记顺序追加在内置表之后） */
export function contributedPermissionLabels(): PermissionLabelItem[] {
  return permissionLabelContributions
}

/** 侧边栏顶层导航项：底座只按 groupOrder/登记顺序渲染，不认识具体业务路由 */
export interface NavItemDefinition {
  key: string
  /** 点击后进入的路由路径 */
  to: string
  /** 侧边栏显示名 */
  label: string
  /** 顶栏页面大标题 */
  title: string
  icon: Component
  /** 分组键：同键的项归到一段，键相同的后登记项沿用首个的标题与顺序 */
  group: string
  /** 分组标题（模块登记的中文组名，如"系统设置"） */
  groupLabel: string
  /** 分组自上而下的顺序，小的在前；未传取 100，底座「系统设置」占 900 恒在最后 */
  groupOrder?: number
  prefixes: string[]
  /** true=始终显示；false/未传=要有助手可见子项才显示（配置化目录用） */
  alwaysVisible?: boolean
  /** 命中该系统目录 code 时，子项改由后端菜单树动态提供 */
  menuCatalogCode?: string
}

const navItemContributions: NavItemDefinition[] = []

/**
 * 业务模块登记自己的侧边栏入口。
 * 底座不再写死业务路由：换项目时新页面由各自模块往这里加一条，AppLayout 不用动。
 * 「系统设置」是底座概念，仍由 AppLayout 内置，登记项一律排在它前面。
 */
export function registerNavItems(items: NavItemDefinition[]) {
  for (const item of items) {
    if (!navItemContributions.some((existing) => existing.key === item.key)) {
      navItemContributions.push(item)
    }
  }
}

/** 底座读取业务贡献的导航项（按登记顺序） */
export function contributedNavItems(): NavItemDefinition[] {
  return navItemContributions
}

/** 品牌文案：侧边栏与登录页展示的产物名，底座只给中性默认值 */
export interface BrandDefinition {
  /** 侧边栏顶部小字（副标题） */
  sidebarKicker: string
  /** 侧边栏主标题，同时是页面标题兜底 */
  product: string
  /** 窄栏（图标导航）品牌角标，单字 */
  mark: string
  /** 登录页左侧小字 */
  loginKicker: string
  /** 登录页左侧大字 */
  loginTitle: string
  /** 登录页一句话介绍 */
  loginCopy: string
  /** 登录页能力卡，空数组则整块不渲染 */
  loginFeatures: Array<{ title: string; text: string }>
  /** 登录表单标题 */
  loginFormTitle: string
}

const defaultBrand: BrandDefinition = {
  sidebarKicker: 'Cornerstone',
  product: '管理平台',
  mark: 'C',
  loginKicker: 'Cornerstone',
  loginTitle: '通用管理平台',
  loginCopy: '账号、角色、权限、字典与列表配置由底座提供，业务模块按需挂载。',
  loginFeatures: [],
  loginFormTitle: '登录平台',
}

let brand: BrandDefinition = { ...defaultBrand }

/** 壳工程登记本项目的品牌文案（只调用一次，后调覆盖前调） */
export function registerBranding(next: Partial<BrandDefinition>) {
  brand = { ...brand, ...next }
}

export function currentBrand(): BrandDefinition {
  return brand
}

const institutionResetHandlers: Array<() => void> = []
/** 业务模块登记"机构切换/退出登录后要清空的范围状态" */
export function onInstitutionReset(handler: () => void) {
  institutionResetHandlers.push(handler)
}

/** 底座 institution store 在切换/重置时广播，不直接依赖任何业务 store */
export function notifyInstitutionReset() {
  for (const handler of institutionResetHandlers) {
    handler()
  }
}

/** 页面插槽内容：component 自己取数据、自己管状态，宿主只提供渲染位 */
export interface PageSlot {
  name: string
  order?: number
  component: Component
}

const pageSlots = new Map<string, PageSlot[]>()

/** 往某个页面的插槽位注入内容：宿主页面声明插槽，其它模块按 pageKey 注入自己的卡片/表格 */
export function registerPageSlot(pageKey: string, slot: PageSlot) {
  const list = pageSlots.get(pageKey) ?? []
  if (list.some((item) => item.name === slot.name)) {
    return
  }
  list.push(slot)
  list.sort((a, b) => (a.order ?? 100) - (b.order ?? 100))
  pageSlots.set(pageKey, list)
}

/** 宿主页面取插槽内容；注入方未装配时返回空数组，页面自动少这几块 */
export function getPageSlots(pageKey: string): PageSlot[] {
  return pageSlots.get(pageKey) ?? []
}

const refreshHandlers = new Map<string, Array<() => unknown>>()

/** 插槽内容登记自己的刷新逻辑，宿主刷新按钮统一触发；返回注销函数（组件卸载时调用） */
export function onPageRefresh(pageKey: string, handler: () => unknown): () => void {
  const list = refreshHandlers.get(pageKey) ?? []
  list.push(handler)
  refreshHandlers.set(pageKey, list)
  return () => {
    refreshHandlers.set(
      pageKey,
      list.filter((item) => item !== handler)
    )
  }
}

export function notifyPageRefresh(pageKey: string) {
  for (const handler of refreshHandlers.get(pageKey) ?? []) {
    void handler()
  }
}
