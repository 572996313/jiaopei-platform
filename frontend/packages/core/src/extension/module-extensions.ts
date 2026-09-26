import type { Component, VNode } from 'vue'
import type { ListColumnConfig } from '@cornerstone/core/components/ConfigurableList/types'
import type { RenderContext } from '@cornerstone/core/components/ConfigurableList/renderers'
import type { PermissionLabelItem } from '@cornerstone/core/constants/permissionLabels'

/**
 * 底座 ↔ 业务模块的扩展点注册表。
 *
 * 提供八类扩展点：列表单元格渲染器（业务列的回显）、权限码中文名（业务域「查看/维护」标签与分组）、
 * 侧边栏导航项（业务模块自己的页面入口）、品牌文案（壳工程登记本项目产物名）、
 * 机构切换广播（清业务筛选范围）、页面插槽（业务往别的模块页面里注入内容块）、
 * 注册凭证行操作（业务把端能力按钮注入凭证页操作列，如小程序码）、
 * 横幅右侧动作（业务把全局入口注入顶栏，如公告铃铛）。
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
 * 这张表是权限码中文名的权威源（一个码可挂多个页面节点，节点名只说明挂在哪），
 * /system/permission-options 动态返回的码清单里，本表没登记过的码才用后端给的节点名。
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
  /**
   * 该入口对应的系统菜单节点 code：只有在当前用户已授权的菜单树里能找到这个节点才显示，
   * 显示名同时改取节点的 menuName（后台可改名）。用于单页入口按授权显隐，底座只认 code 不认业务路由。
   */
  menuCode?: string
  /** true=始终显示；false/未传=要有助手可见子项才显示（配置化目录用）。写了 menuCode 时以 menuCode 为准 */
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

/**
 * 构建期品牌配置：系统名称一类的展示文案已改由系统参数 brand.* 运行期下发（见 composables/use-branding.ts），
 * 这里只留"结构上是数组、系统参数页的单值输入框塞不下"的内容，底座给空默认值、不写死任何项目名词。
 */
export interface BrandDefinition {
  /** 登录页能力卡，空数组则整块不渲染 */
  loginFeatures: Array<{ title: string; text: string }>
}

const defaultBrand: BrandDefinition = {
  loginFeatures: [],
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

/**
 * 注册凭证页的行操作：小程序码这类"端能力"按钮底座不认识（底座不许出现微信词汇），
 * 由业务模块登记组件渲染进操作列。组件收到 props.row（InvitationSummary 行数据），
 * 自管按钮、弹窗与取数；未装配业务模块时操作列只剩底座自带的「停用」。
 */
export interface InvitationActionDefinition {
  key: string
  /** 只对列出的凭证类型展示(INVITE=邀请码,ACTIVATE=激活码)，不传=全部类型 */
  kinds?: string[]
  /** 行内自上而下顺序，未传取 100（底座「停用」恒在最前） */
  order?: number
  component: Component
}

const invitationActionContributions: InvitationActionDefinition[] = []

export function registerInvitationActions(items: InvitationActionDefinition[]) {
  for (const item of items) {
    if (!invitationActionContributions.some((existing) => existing.key === item.key)) {
      invitationActionContributions.push(item)
      invitationActionContributions.sort((a, b) => (a.order ?? 100) - (b.order ?? 100))
    }
  }
}

/** 底座按行类型现算可见的业务行操作（模块登记晚于页面加载也不能漏渲染） */
export function contributedInvitationActions(kind: string): InvitationActionDefinition[] {
  return invitationActionContributions.filter((item) => !item.kinds || item.kinds.includes(kind))
}

/**
 * 横幅右侧的动作入口：铃铛这类"跟着登录账号走、不属于任何一个页面"的全局能力。
 * 底座不认识公告/消息这类业务概念，组件按扩展点注入 `.banner-actions`，
 * 自己取数、自己管状态；未装配业务模块时那一段直接不渲染。
 */
export interface BannerActionDefinition {
  key: string
  /** 横幅右侧自左向右顺序，未传取 100（底座的搜索/外观/机构/账号恒在其右） */
  order?: number
  component: Component
}

const bannerActionContributions: BannerActionDefinition[] = []

export function registerBannerActions(items: BannerActionDefinition[]) {
  for (const item of items) {
    if (!bannerActionContributions.some((existing) => existing.key === item.key)) {
      bannerActionContributions.push(item)
      bannerActionContributions.sort((a, b) => (a.order ?? 100) - (b.order ?? 100))
    }
  }
}

/** 底座现算要渲染的业务动作（登记晚于外壳加载也要出现，所以不取快照） */
export function contributedBannerActions(): BannerActionDefinition[] {
  return bannerActionContributions
}
