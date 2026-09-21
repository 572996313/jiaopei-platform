import type { Component, VNode } from 'vue'
import type { ListColumnConfig } from '@cornerstone/core/components/ConfigurableList/types'
import type { RenderContext } from '@cornerstone/core/components/ConfigurableList/renderers'
import type { PermissionLabelItem } from '@cornerstone/core/constants/permissionLabels'

/**
 * 底座 ↔ 业务模块的扩展点注册表。
 *
 * 提供四类扩展点：列表单元格渲染器（业务列的回显）、权限码中文名（业务域「查看/维护」标签与分组）、
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

/** 往某个页面的插槽位注入内容（如教务模块往校区页注入"课程/班型"卡片） */
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
