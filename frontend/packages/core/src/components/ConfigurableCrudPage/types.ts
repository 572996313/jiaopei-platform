/**
 * ConfigurableCrudPage 页面级配置 schema。
 * 与后端 sys_list_config 的 columns_json / query_json / actions_json / form_json 对应,
 * 新增的 page_json / dialog_json 与 actions[].actionType 为「阶段 A」扩展字段:
 * 后端尚未加列时前端读取缺省 → 走当前默认行为,向后兼容。
 * parsePageConfig 将四段配置解析收敛为单一配置对象,供组件与插件消费(向 usePageConfig 打底)。
 */

import type { ListConfigSummary } from '@cornerstone/core/api/system'
import type {
  ActionHandler,
  FormFieldConfig,
  FormMode,
  FormPageConfig,
  ListActionConfig,
  ListColumnConfig,
  QueryFieldConfig,
} from '../ConfigurableList/types'

/** 页面壳配置(存 page_json):空态等描述性文案(页头 h1 已取菜单名,卡片不再渲染标题/计数) */
export interface PageShellConfig {
  /** 标题下方的描述性文案(说明"怎么生效"这一类),缺省不显示 */
  description?: string
  /** 表格空态文案,缺省 '暂无数据' */
  emptyText?: string
}

/** 弹窗壳配置(存 dialog_json):宽度 / 标题 / 按钮文案 / 提交后行为 */
export interface DialogShellConfig {
  /** 弹窗宽度,缺省 '640px' */
  width?: string
  /** 新增标题,缺省 '新增' */
  createTitle?: string
  /** 编辑标题,缺省 '编辑' */
  editTitle?: string
  /** 提交按钮文案,缺省 '保存' */
  submitText?: string
  /** 取消按钮文案,缺省 '取消' */
  cancelText?: string
  /** 新增成功提示,占位符 {name}={resourceName},缺省 '{name}已创建' */
  createMessage?: string
  /** 编辑成功提示,缺省 '{name}已更新' */
  updateMessage?: string
  /** 提交成功后是否重新拉取列表,缺省 true */
  reloadAfterSubmit?: boolean
}

/** 按钮的内置动作类型:引擎据此选择默认处理;custom 表示完全由宿主覆写 */
export type CrudActionType =
  'create' | 'edit' | 'delete' | 'toggle-status' | 'reset-password' | 'assign-roles' | 'custom'

/** 动作配置:列表按钮配置原样 + 阶段 A 扩展 actionType */
export interface CrudActionConfigExt extends ListActionConfig {
  /** 内置动作类型,缺省 custom;引擎按类型给默认 handler,宿主可覆写 */
  actionType?: CrudActionType
}

/** 解析后的列表配置(与 ConfigurableList 内部 parseConfig 一致的结构) */
export interface ParsedListConfig {
  resourceCode: string
  resourceName?: string
  /** 分页查询接口路径(相对 /api),如 /system/users/page */
  endpoint: string
  pageSize?: number
  columns: ListColumnConfig[]
  queryFields?: QueryFieldConfig[]
}

/** 页面级完整配置:四段基础配置 + 阶段 A 扩展(page/dialog/actionType) */
export interface CrudPageConfig {
  resourceCode: string
  /** 列表配置(columns/query/endpoint/pageSize) */
  list: ParsedListConfig
  /** 表单配置(编辑/新增弹窗),未配置时为空 */
  form: FormPageConfig | null
  /** 页面壳配置(读 page_json,缺省默认值) */
  page: Required<PageShellConfig>
  /** 弹窗壳配置(读 dialog_json,缺省默认值) */
  dialog: Required<DialogShellConfig>
  /** 动作配置(actions_json 原样 + actionType 扩展) */
  actions: CrudActionConfigExt[]
  /** 原始 ListConfigSummary,保留供插件读取未解析字段 */
  raw: ListConfigSummary
}

/** 页面删除/启停动作的定制项(其余动作的多选校验/联动等走宿主函数) */
export interface CrudActionExtras {
  delete?: {
    disabled?: (row?: Record<string, any>) => boolean
    confirmTitle?: string
    /** {name} 为行展示名 */
    confirmMessage?: string
    successMessage?: (row?: Record<string, any>) => string
  }
  toggleStatus?: {
    disabled?: (row?: Record<string, any>) => boolean
    confirmMessage?: (row?: Record<string, any>) => string
    successMessage?: (row?: Record<string, any>) => string
  }
}

/**
 * 宿主页面注入 ConfigurableCrudPage 的资源 CRUD 实现画像。
 * 引擎据此组装内置动作(create/edit 打开表单调起 useCrudModal;delete/toggle-status 走确认样板)
 * 与提交流程;assign-roles / assign-menus / reset-password 等业务强相关动作走 extraActions 手写。
 */
export interface CrudPageOptions {
  /** 资源显示名,用于默认 toast 文案与占位符 */
  resourceName?: string
  /** 打开弹窗后、表单渲染前,宿主重置表单与联动缓存(create: row=null;edit: row=选中行) */
  onOpenReset?: (mode: FormMode, row: Record<string, any> | null) => void | Promise<void>
  /** 提交前自检(如“至少勾选一个权限码”),返回非空字符串则中止并展示 */
  beforeSubmit?: (form: Record<string, any>, mode: FormMode) => string | null
  /** 组装弹窗回填初值(create 也走此函数,宿主给默认值);缺省原样返回选中行/null */
  buildInitial?: (mode: FormMode, row: Record<string, any> | null) => Record<string, any>
  /** 组装新增 payload */
  buildCreatePayload?: (form: Record<string, any>) => Record<string, any>
  /** 组装编辑 payload(id 由选中行提供) */
  buildUpdatePayload?: (form: Record<string, any>, row: Record<string, any>) => Record<string, any>
  /** 新增接口调用 */
  createApi?: (payload: any) => Promise<unknown>
  /** 编辑接口调用 */
  updateApi?: (id: string, payload: any) => Promise<unknown>
  /** 删除接口调用(内置 delete 动作使用) */
  deleteApi?: (id: string) => Promise<unknown>
  /** 启停接口调用(内置 toggle-status 动作使用) */
  toggleStatusApi?: (row: Record<string, any>) => Promise<unknown>
  /** 业务强相关动作(assign-roles / reset-password / assign-menus 等),完全宿主实现 */
  extraActions?: Record<string, ActionHandler>
  /** 内置 delete/toggle-status 的定制(确认文案/禁用条件/成功文案) */
  actionExtras?: CrudActionExtras
  /** 错误→中文展示,缺省用 Error.message 或 fallback */
  toMessage?: (error: unknown, fallback: string) => string
}

/** page_json / dialog_json / actionType 的默认值,后端未配置时按此回退,行为与现状一致 */
export const DEFAULT_PAGE_SHELL: Required<PageShellConfig> = {
  description: '',
  emptyText: '暂无数据',
}

export const DEFAULT_DIALOG: Required<DialogShellConfig> = {
  width: '640px',
  createTitle: '新增{name}',
  editTitle: '编辑{name}',
  submitText: '保存',
  cancelText: '取消',
  createMessage: '{name}已创建',
  updateMessage: '{name}已更新',
  reloadAfterSubmit: true,
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function readString(obj: Record<string, unknown>, key: string, fallback: string): string {
  const value = obj[key]
  return typeof value === 'string' && value ? value : fallback
}

function readBool(obj: Record<string, unknown>, key: string, fallback: boolean): boolean {
  const value = obj[key]
  return typeof value === 'boolean' ? value : fallback
}

/** 解析 page_json(页面壳),结构非法时回退默认值,绝不抛错打断页面 */
export function parsePageShell(raw: string | null | undefined): Required<PageShellConfig> {
  const page: PageShellConfig = {}
  if (raw && raw.trim()) {
    try {
      const obj: unknown = JSON.parse(raw)
      if (isPlainObject(obj)) {
        page.description = readString(obj, 'description', '')
        page.emptyText = readString(obj, 'emptyText', DEFAULT_PAGE_SHELL.emptyText)
      }
    } catch {
      // page_json 非法:按默认值渲染,配置管理页会提示修正
    }
  }
  return {
    description: page.description || '',
    emptyText: page.emptyText || DEFAULT_PAGE_SHELL.emptyText,
  }
}

/** 解析 dialog_json(弹窗壳),结构非法时回退默认值 */
export function parseDialogShell(raw: string | null | undefined): Required<DialogShellConfig> {
  const dialog: DialogShellConfig = {}
  if (raw && raw.trim()) {
    try {
      const obj: unknown = JSON.parse(raw)
      if (isPlainObject(obj)) {
        dialog.width = readString(obj, 'width', '')
        dialog.createTitle = readString(obj, 'createTitle', '')
        dialog.editTitle = readString(obj, 'editTitle', '')
        dialog.submitText = readString(obj, 'submitText', '')
        dialog.cancelText = readString(obj, 'cancelText', '')
        dialog.createMessage = readString(obj, 'createMessage', '')
        dialog.updateMessage = readString(obj, 'updateMessage', '')
        dialog.reloadAfterSubmit = readBool(
          obj,
          'reloadAfterSubmit',
          DEFAULT_DIALOG.reloadAfterSubmit
        )
      }
    } catch {
      // dialog_json 非法:按默认值渲染
    }
  }
  return {
    width: dialog.width || DEFAULT_DIALOG.width,
    createTitle: dialog.createTitle || DEFAULT_DIALOG.createTitle,
    editTitle: dialog.editTitle || DEFAULT_DIALOG.editTitle,
    submitText: dialog.submitText || DEFAULT_DIALOG.submitText,
    cancelText: dialog.cancelText || DEFAULT_DIALOG.cancelText,
    createMessage: dialog.createMessage || DEFAULT_DIALOG.createMessage,
    updateMessage: dialog.updateMessage || DEFAULT_DIALOG.updateMessage,
    reloadAfterSubmit: dialog.reloadAfterSubmit ?? DEFAULT_DIALOG.reloadAfterSubmit,
  }
}

/** 解析 actions_json:给每个动作补 actionType(缺省 custom),返回空数组当未配置处理 */
export function parseActions(actions: ListActionConfig[] | undefined): CrudActionConfigExt[] {
  if (!Array.isArray(actions)) {
    return []
  }
  return actions.map((action) => ({
    ...action,
    actionType:
      (action as CrudActionConfigExt).actionType ?? inferActionType(action.action, action.label),
  }))
}

/**
 * 根据动作名/文案推断内置类型(阶段 A 前端兜底:后端尚未把 actionType 入配置前,
 * 约定式的动作名命中规则;后端加列后该规则降级为仅未显式声明时的兜底)。
 */
export function inferActionType(action: string, label?: string): CrudActionType {
  const lowerAction = action.toLowerCase()
  const lowerLabel = label?.toLowerCase() ?? ''
  if (/(^|[-_])(add|create|new)([-_]|$)/.test(lowerAction)) {
    return 'create'
  }
  if (/(^|[-_])(edit|update|modify)([-_]|$)/.test(lowerAction)) {
    return 'edit'
  }
  if (/(^|[-_])(delete|remove|del)([-_]|$)/.test(lowerAction)) {
    return 'delete'
  }
  if (
    lowerAction.includes('toggle') ||
    (lowerAction.includes('status') && (lowerLabel.includes('停用') || lowerLabel.includes('启用')))
  ) {
    return 'toggle-status'
  }
  if (lowerAction.includes('reset') && lowerAction.includes('password')) {
    return 'reset-password'
  }
  if (
    lowerAction.includes('assign') &&
    (lowerAction.includes('role') || lowerAction.includes('roles'))
  ) {
    return 'assign-roles'
  }
  return 'custom'
}

/** 统一解析入口:把 ListConfigSummary 解析成前端消费的单一配置对象(阶段 A 收拢点) */
export function parsePageConfig(summary: ListConfigSummary): CrudPageConfig {
  let columns: unknown
  let queryFields: unknown
  let actions: unknown
  try {
    columns = JSON.parse(summary.columnsJson)
    queryFields = summary.queryJson ? JSON.parse(summary.queryJson) : []
    actions = summary.actionsJson ? JSON.parse(summary.actionsJson) : undefined
  } catch {
    throw new Error(`列表配置 ${summary.resourceCode} 的 JSON 解析失败,请在列表配置中修正`)
  }
  if (!Array.isArray(columns) || columns.length === 0) {
    throw new Error(`列表配置 ${summary.resourceCode} 的列定义为空`)
  }

  let form: FormPageConfig | null = null
  if (summary.formJson && summary.formJson.trim()) {
    try {
      const parsed: unknown = JSON.parse(summary.formJson)
      if (isPlainObject(parsed) && Array.isArray(parsed.fields) && parsed.fields.length > 0) {
        form = {
          resourceCode: summary.resourceCode,
          labelWidth:
            typeof parsed.labelWidth === 'string' || typeof parsed.labelWidth === 'number'
              ? (parsed.labelWidth as string | number)
              : undefined,
          gridColumns: typeof parsed.gridColumns === 'number' ? parsed.gridColumns : undefined,
          fields: parsed.fields as FormFieldConfig[],
        }
      }
    } catch {
      // form_json 非法:按未配置处理,ConfigurableForm 内部会重新解析并报错提示
    }
  }

  return {
    resourceCode: summary.resourceCode,
    list: {
      resourceCode: summary.resourceCode,
      resourceName: summary.resourceName,
      endpoint: summary.endpoint,
      pageSize: summary.pageSize,
      columns: columns as ListColumnConfig[],
      queryFields: Array.isArray(queryFields) ? (queryFields as QueryFieldConfig[]) : [],
    },
    form,
    page: parsePageShell(summary.pageJson),
    dialog: parseDialogShell(summary.dialogJson),
    actions: parseActions(Array.isArray(actions) ? (actions as ListActionConfig[]) : undefined),
    raw: summary,
  }
}
