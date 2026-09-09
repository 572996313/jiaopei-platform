/**
 * 可配置列表的配置结构(与后端 sys_list_config 的 columns_json / query_json 对应)。
 * 配置只声明"渲染什么",具体渲染实现由 ConfigurableList 内置渲染器与
 * renderers.ts 的代码侧注册表(render 语义)承担。
 */

export type ListColumnType = 'text' | 'datetime' | 'dict' | 'boolean' | 'tags' | 'map'

export interface ListColumnConfig {
  /** 行数据字段名 */
  prop: string
  /** 列标题 */
  label: string
  width?: number
  minWidth?: number
  showOverflowTooltip?: boolean
  /** 渲染类型,缺省 text */
  type?: ListColumnType
  /** type=dict 时的字典类型编码 */
  dictType?: string
  /** type=dict 时 value → el-tag type 的映射,如 {"active":"success"};缺省渲染纯文本 */
  tagTypeMap?: Record<string, string>
  /** type=boolean 时的展示文案 */
  trueLabel?: string
  falseLabel?: string
  /** type=map 时的静态值映射,如 {"auth":"认证"} */
  valueMap?: Record<string, string>
  /** 代码侧预置渲染器名称(renderers.ts 注册表),优先于 type */
  renderer?: string
}

export type ListQueryFieldType = 'input' | 'select' | 'dict-select' | 'date'

/** 按钮位置:表格上方工具栏 / 行内操作列 */
export type ListActionPosition = 'toolbar' | 'row'

/** 按钮样式,与 Element Plus button type 对齐 */
export type ListButtonType = 'primary' | 'default' | 'text' | 'danger'

export interface ListActionConfig {
  /** 代码侧动作名,宿主页面通过 actions prop 注册同名处理器 */
  action: string
  /** 静态文案兜底;行内动作可在注册表里用 label(row) 覆盖(如 停用/启用) */
  label: string
  /** 渲染位置,每个按钮独立配置,toolbar=表格上方工具栏,row=行内操作列 */
  position: ListActionPosition
  /** 按钮样式,toolbar 缺省 default,row 缺省 text */
  buttonType?: ListButtonType
  /** 权限码,当前用户无该权限时按钮隐藏(不渲染) */
  permission?: string
}

/**
 * 按钮动作处理器(宿主注入):handler 触发点击行为,label/disabled 可选,
 * 用于静态配置无法表达的动态文案(如 停用/启用)与禁用条件(如系统内置禁删)。
 */
export interface ActionHandler {
  handler: (row?: Record<string, any>) => void
  label?: (row?: Record<string, any>) => string
  disabled?: (row?: Record<string, any>) => boolean
}

export interface QueryOption {
  label: string
  value: string
}

export interface QueryFieldConfig {
  /** 后端分页接口支持的查询参数名(后端按白名单校验) */
  field: string
  /** 控件标签 */
  label: string
  /** 控件类型,缺省 input */
  type?: ListQueryFieldType
  /** type=dict-select 时的字典类型编码 */
  dictType?: string
  placeholder?: string
  defaultValue?: string
  /** type=select 时的静态选项 */
  options?: QueryOption[]
  /** type=select 时走代码侧选项加载器(renderers.ts 注册表),如 institutions */
  optionsLoader?: string
}

export interface ListPageConfig {
  resourceCode: string
  resourceName?: string
  /** 分页查询接口路径(相对 /api),如 /system/users/page */
  endpoint: string
  pageSize?: number
  columns: ListColumnConfig[]
  queryFields?: QueryFieldConfig[]
  /** 按钮配置;未配置(空)时组件回退 #actions 插槽模式 */
  actions?: ListActionConfig[]
}
