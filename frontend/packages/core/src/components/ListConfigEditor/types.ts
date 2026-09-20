/**
 * 列表配置可视化编辑器 —— 类型定义与 JSON↔可视化状态双向转换。
 *
 * 可视化状态使用「行结构」描述配置(与后端校验的 JSON 结构一一对应):
 *  - tagTypeMap / valueMap / options 这类「对象/数组」在可视化里编辑为 key-value 行,
 *    序列化时再转回 JSON 对象,保证与后端 ListConfigServiceImpl 校验完全兼容。
 *  - 后端已知字段全覆盖;未知/高级字段请切到「JSON 模式」编辑(表单模式会忽略它们)。
 */

export interface KeyValuePair {
  key: string
  value: string
}

/** 列配置(columnsJson 每项) */
export interface ColumnRow {
  prop: string
  label: string
  type: string
  width?: number | null
  minWidth?: number | null
  showOverflowTooltip?: boolean
  dictType?: string
  tagTypeMap?: KeyValuePair[]
  trueLabel?: string
  falseLabel?: string
  valueMap?: KeyValuePair[]
  renderer?: string
}

/** 查询条件(queryJson 每项) */
export interface QueryRow {
  field: string
  label: string
  type: string
  placeholder?: string
  defaultValue?: string
  dictType?: string
  options?: KeyValuePair[]
  optionsLoader?: string
}

/** 按钮配置(actionsJson 每项) */
export interface ActionRow {
  action: string
  label: string
  position: string
  buttonType?: string
  permission?: string
  actionType?: string
}

/** 弹窗表单字段(formJson.fields 每项) */
export interface FormFieldRow {
  field: string
  label: string
  type: string
  placeholder?: string
  tip?: string
  gridSpan?: number
  showOn?: string[]
  disabledOn?: string[]
  options?: KeyValuePair[]
  optionsLoader?: string
  dictType?: string
  required?: boolean
  defaultValue?: string
  maxlength?: number
  min?: number
  max?: number
  clearable?: boolean
  rows?: number
  onChange?: string
}

/** 弹窗表单整页(formJson) */
export interface FormConfigState {
  labelWidth?: string
  gridColumns?: number
  fields: FormFieldRow[]
}

/** 页面壳配置(pageJson) */
export interface PageShellState {
  title: string
  description: string
  showCount: boolean
  countFormat: string
  emptyText: string
}

/** 弹窗壳配置(dialogJson) */
export interface DialogShellState {
  width: string
  createTitle: string
  editTitle: string
  submitText: string
  cancelText: string
  createMessage: string
  updateMessage: string
  reloadAfterSubmit: boolean
}

/** 整份配置的可视化状态(供 ListConfigManagementView 持有) */
export interface ListConfigFormState {
  columns: ColumnRow[]
  query: QueryRow[]
  actions: ActionRow[]
  form: FormConfigState
  page: PageShellState
  dialog: DialogShellState
}

/* ---------------- 可选项常量(与后端 ListConfigServiceImpl 校验集一一对齐) ---------------- */

export const COLUMN_TYPE_OPTIONS = [
  { value: 'text', label: 'text(纯文本,默认)' },
  { value: 'datetime', label: 'datetime(日期时间)' },
  { value: 'dict', label: 'dict(字典标签,按字典翻译)' },
  { value: 'boolean', label: 'boolean(是/否)' },
  { value: 'tags', label: 'tags(标签组,数组字段)' },
  { value: 'map', label: 'map(值映射,原样翻译)' },
]

export const QUERY_TYPE_OPTIONS = [
  { value: 'input', label: 'input(输入框,默认)' },
  { value: 'select', label: 'select(下拉选择)' },
  { value: 'dict-select', label: 'dict-select(字典下拉)' },
  { value: 'date', label: 'date(日期)' },
]

export const ACTION_POSITION_OPTIONS = [
  { value: 'toolbar', label: 'toolbar(表格上方工具栏)' },
  { value: 'row', label: 'row(表格行内操作)' },
]

export const ACTION_BUTTON_TYPE_OPTIONS = [
  { value: 'primary', label: 'primary(主色强调)' },
  { value: 'default', label: 'default(普通)' },
  { value: 'text', label: 'text(文字按钮)' },
  { value: 'danger', label: 'danger(红色危险)' },
]

export const ACTION_TYPE_OPTIONS = [
  { value: 'create', label: 'create(打开新增弹窗)' },
  { value: 'edit', label: 'edit(打开编辑弹窗)' },
  { value: 'delete', label: 'delete(确认后删除)' },
  { value: 'toggle-status', label: 'toggle-status(启/停切换)' },
  { value: 'reset-password', label: 'reset-password(重置密码)' },
  { value: 'assign-roles', label: 'assign-roles(分配角色)' },
  { value: 'custom', label: 'custom(由页面代码实现,默认)' },
]

export const FORM_FIELD_TYPE_OPTIONS = [
  { value: 'input', label: 'input(单行输入)' },
  { value: 'password', label: 'password(密码输入)' },
  { value: 'textarea', label: 'textarea(多行文本)' },
  { value: 'number', label: 'number(数字输入)' },
  { value: 'select', label: 'select(下拉选择)' },
  { value: 'dict-select', label: 'dict-select(字典下拉)' },
  { value: 'switch', label: 'switch(开关)' },
  { value: 'checkbox-group', label: 'checkbox-group(多选勾选)' },
  { value: 'radio', label: 'radio(单选)' },
  { value: 'permission-groups', label: 'permission-groups(权限树勾选)' },
]

export const RENDERER_OPTIONS = [
  { value: 'institutionName', label: 'institutionName(机构名称)' },
  { value: 'campusName', label: 'campusName(校区名称)' },
  { value: 'permissionTags', label: 'permissionTags(权限标签组)' },
  { value: 'passwordStatus', label: 'passwordStatus(密码状态标签)' },
]

export const OPTIONS_LOADER_OPTIONS = [
  { value: 'institutions', label: 'institutions(机构列表)' },
  { value: 'campuses', label: 'campuses(校区列表)' },
  { value: 'roles', label: 'roles(角色列表)' },
  { value: 'status', label: 'status(启用/停用)' },
]

export const GRID_SPAN_OPTIONS = [
  { value: 1, label: '1(半行,默认)' },
  { value: 2, label: '2(整行)' },
]

/* ---------------- JSON ↔ 可视化状态转换 ---------------- */

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function readString(obj: Record<string, unknown>, key: string): string | undefined {
  const value = obj[key]
  return typeof value === 'string' && value ? value : undefined
}

function readNumber(obj: Record<string, unknown>, key: string): number | undefined {
  const value = obj[key]
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
}

function readBool(obj: Record<string, unknown>, key: string): boolean | undefined {
  const value = obj[key]
  return typeof value === 'boolean' ? value : undefined
}

function readPairs(obj: Record<string, unknown>, key: string): KeyValuePair[] | undefined {
  const value = obj[key]
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return undefined
  }
  return Object.entries(value as Record<string, unknown>).map(([k, v]) => ({
    key: k,
    value: String(v ?? ''),
  }))
}

function readStringArray(obj: Record<string, unknown>, key: string): string[] | undefined {
  const value = obj[key]
  if (!Array.isArray(value)) {
    return undefined
  }
  return value.filter((item): item is string => typeof item === 'string')
}

/** options 数组(查询/表单下拉的静态选项)→ 行结构 */
function readOptionPairs(value: unknown): KeyValuePair[] | undefined {
  if (!Array.isArray(value)) {
    return undefined
  }
  return value
    .map((item) =>
      isRecord(item) ? { key: String(item.value ?? ''), value: String(item.label ?? '') } : null
    )
    .filter((item): item is KeyValuePair => item !== null)
}

/** key-value 行 → JSON 对象(过滤掉 key 为空的脏行) */
function pairsToRecord(pairs: KeyValuePair[] | undefined): Record<string, string> | undefined {
  const cleaned = (pairs ?? []).filter((item) => item.key.trim())
  if (cleaned.length === 0) {
    return undefined
  }
  return Object.fromEntries(cleaned.map((item) => [item.key.trim(), item.value]))
}

/** options 行 → JSON 数组 */
function optionPairsToJson(pairs: KeyValuePair[] | undefined) {
  const cleaned = (pairs ?? []).filter((item) => item.key.trim())
  if (cleaned.length === 0) {
    return undefined
  }
  return cleaned.map((item) => ({ label: item.value, value: item.key.trim() }))
}

/* ----- 列配置 ----- */

export function parseColumns(json: string): ColumnRow[] {
  try {
    const list: unknown = JSON.parse(json)
    if (!Array.isArray(list)) {
      return []
    }
    return list.map((item) => {
      const obj = isRecord(item) ? item : {}
      return {
        prop: obj.prop ? String(obj.prop) : '',
        label: obj.label ? String(obj.label) : '',
        type: typeof obj.type === 'string' ? obj.type : 'text',
        width: readNumber(obj, 'width'),
        minWidth: readNumber(obj, 'minWidth'),
        showOverflowTooltip: readBool(obj, 'showOverflowTooltip'),
        dictType: readString(obj, 'dictType'),
        tagTypeMap: readPairs(obj, 'tagTypeMap'),
        trueLabel: readString(obj, 'trueLabel'),
        falseLabel: readString(obj, 'falseLabel'),
        valueMap: readPairs(obj, 'valueMap'),
        renderer: readString(obj, 'renderer'),
      }
    })
  } catch {
    return []
  }
}

export function columnsToJson(rows: ColumnRow[]): string {
  const list = rows
    .filter((row) => row.prop.trim() || row.label.trim())
    .map((row) => {
      const item: Record<string, unknown> = {
        prop: row.prop.trim(),
        label: row.label.trim(),
      }
      if (row.type && row.type !== 'text') {
        item.type = row.type
      }
      if (row.width != null) {
        item.width = row.width
      }
      if (row.minWidth != null) {
        item.minWidth = row.minWidth
      }
      if (row.showOverflowTooltip === true) {
        item.showOverflowTooltip = true
      }
      if (row.dictType && row.dictType.trim()) {
        item.dictType = row.dictType.trim()
      }
      const tagTypeMap = pairsToRecord(row.tagTypeMap)
      if (tagTypeMap) {
        item.tagTypeMap = tagTypeMap
      }
      if (row.trueLabel && row.trueLabel.trim()) {
        item.trueLabel = row.trueLabel.trim()
      }
      if (row.falseLabel && row.falseLabel.trim()) {
        item.falseLabel = row.falseLabel.trim()
      }
      const valueMap = pairsToRecord(row.valueMap)
      if (valueMap) {
        item.valueMap = valueMap
      }
      if (row.renderer && row.renderer.trim()) {
        item.renderer = row.renderer.trim()
      }
      return item
    })
  return JSON.stringify(list)
}

/* ----- 查询条件 ----- */

export function parseQuery(json: string): QueryRow[] {
  try {
    const list: unknown = JSON.parse(json)
    if (!Array.isArray(list)) {
      return []
    }
    return list.map((item) => {
      const obj = isRecord(item) ? item : {}
      return {
        field: obj.field ? String(obj.field) : '',
        label: obj.label ? String(obj.label) : '',
        type: typeof obj.type === 'string' ? obj.type : 'input',
        placeholder: readString(obj, 'placeholder'),
        defaultValue: readString(obj, 'defaultValue'),
        dictType: readString(obj, 'dictType'),
        options: readOptionPairs(obj.options),
        optionsLoader: readString(obj, 'optionsLoader'),
      }
    })
  } catch {
    return []
  }
}

export function queryToJson(rows: QueryRow[]): string {
  const list = rows
    .filter((row) => row.field.trim() || row.label.trim())
    .map((row) => {
      const item: Record<string, unknown> = {
        field: row.field.trim(),
        label: row.label.trim(),
      }
      if (row.type && row.type !== 'input') {
        item.type = row.type
      }
      if (row.placeholder && row.placeholder.trim()) {
        item.placeholder = row.placeholder.trim()
      }
      if (row.defaultValue && row.defaultValue.trim()) {
        item.defaultValue = row.defaultValue.trim()
      }
      if (row.dictType && row.dictType.trim()) {
        item.dictType = row.dictType.trim()
      }
      const options = optionPairsToJson(row.options)
      if (options) {
        item.options = options
      } else if (row.optionsLoader && row.optionsLoader.trim()) {
        item.optionsLoader = row.optionsLoader.trim()
      }
      return item
    })
  return JSON.stringify(list)
}

/* ----- 按钮 ----- */

export function parseActions(json: string | null | undefined): ActionRow[] {
  if (!json || !json.trim()) {
    return []
  }
  try {
    const list: unknown = JSON.parse(json)
    if (!Array.isArray(list)) {
      return []
    }
    return list.map((item) => {
      const obj = isRecord(item) ? item : {}
      return {
        action: obj.action ? String(obj.action) : '',
        label: obj.label ? String(obj.label) : '',
        position: typeof obj.position === 'string' ? obj.position : 'toolbar',
        buttonType: readString(obj, 'buttonType'),
        permission: readString(obj, 'permission'),
        actionType: readString(obj, 'actionType'),
      }
    })
  } catch {
    return []
  }
}

export function actionsToJson(rows: ActionRow[]): string | null {
  if (rows.length === 0) {
    return null // 未配置按钮,前端回退插槽模式(与后端语义一致:null=未配置
  }
  return JSON.stringify(
    rows
      .filter((row) => row.action.trim() || row.label.trim())
      .map((row) => {
        const item: Record<string, unknown> = {
          action: row.action.trim(),
          label: row.label.trim(),
        }
        if (row.position && row.position !== 'toolbar') {
          item.position = row.position
        }
        if (row.buttonType && row.buttonType.trim()) {
          item.buttonType = row.buttonType.trim()
        }
        if (row.permission && row.permission.trim()) {
          item.permission = row.permission.trim()
        }
        if (row.actionType && row.actionType.trim()) {
          item.actionType = row.actionType.trim()
        }
        return item
      })
  )
}

/* ----- 弹窗表单 ----- */

export function parseForm(json: string | null | undefined): FormConfigState {
  const state: FormConfigState = { fields: [] }
  if (!json || !json.trim()) {
    return state
  }
  try {
    const parsed: unknown = JSON.parse(json)
    if (!isRecord(parsed)) {
      return state
    }
    const labelWidth = parsed.labelWidth
    if (typeof labelWidth === 'string' || typeof labelWidth === 'number') {
      state.labelWidth = String(labelWidth)
    }
    const gridColumns = readNumber(parsed, 'gridColumns')
    if (gridColumns != null) {
      state.gridColumns = gridColumns
    }
    if (Array.isArray(parsed.fields)) {
      state.fields = parsed.fields.filter(isRecord).map((obj) => ({
        field: obj.field ? String(obj.field) : '',
        label: obj.label ? String(obj.label) : '',
        type: typeof obj.type === 'string' ? obj.type : 'input',
        placeholder: readString(obj, 'placeholder'),
        tip: readString(obj, 'tip'),
        gridSpan: readNumber(obj, 'gridSpan'),
        showOn: readStringArray(obj, 'showOn'),
        disabledOn: readStringArray(obj, 'disabledOn'),
        options: readOptionPairs(obj.options),
        optionsLoader: readString(obj, 'optionsLoader'),
        dictType: readString(obj, 'dictType'),
        required: readBool(obj, 'required'),
        defaultValue:
          obj.defaultValue === undefined || obj.defaultValue === null
            ? undefined
            : String(obj.defaultValue),
        maxlength: readNumber(obj, 'maxlength'),
        min: readNumber(obj, 'min'),
        max: readNumber(obj, 'max'),
        clearable: readBool(obj, 'clearable'),
        rows: readNumber(obj, 'rows'),
        onChange: readString(obj, 'onChange'),
      }))
    }
  } catch {
    // 忽略解析错误,由填报方在 JSON 模式下修正
  }
  return state
}

/** 按控件类型将可视化 defaultValue 字符串转回 JSON 里的原始类型 */
function toJsonDefaultValue(raw: string | undefined, type: string): unknown {
  if (raw === undefined || raw === null || raw === '') {
    return undefined
  }
  if (type === 'number') {
    const num = Number(raw)
    return Number.isFinite(num) ? num : undefined
  }
  if (type === 'switch') {
    return raw === 'true'
  }
  return raw
}

export function formToJson(state: FormConfigState): string | null {
  if (state.fields.length === 0) {
    return null
  }
  const form: Record<string, unknown> = {}
  if (state.labelWidth && state.labelWidth.trim()) {
    form.labelWidth = state.labelWidth.trim()
  }
  if (state.gridColumns != null) {
    form.gridColumns = state.gridColumns
  }
  form.fields = state.fields
    .filter((field) => field.field.trim() || field.label.trim())
    .map((field) => {
      const item: Record<string, unknown> = {
        field: field.field.trim(),
        label: field.label.trim(),
        type: field.type || 'input',
      }
      if (field.placeholder && field.placeholder.trim()) {
        item.placeholder = field.placeholder.trim()
      }
      if (field.tip && field.tip.trim()) {
        item.tip = field.tip.trim()
      }
      if (field.gridSpan != null && field.gridSpan !== 1) {
        item.gridSpan = field.gridSpan
      }
      if (field.showOn && field.showOn.length > 0) {
        item.showOn = field.showOn
      }
      if (field.disabledOn && field.disabledOn.length > 0) {
        item.disabledOn = field.disabledOn
      }
      const options = optionPairsToJson(field.options)
      if (options) {
        item.options = options
      } else if (field.optionsLoader && field.optionsLoader.trim()) {
        item.optionsLoader = field.optionsLoader.trim()
      }
      if (field.dictType && field.dictType.trim()) {
        item.dictType = field.dictType.trim()
      }
      if (field.required === true) {
        item.required = true
      }
      const defaultValue = toJsonDefaultValue(field.defaultValue, field.type)
      if (defaultValue !== undefined) {
        item.defaultValue = defaultValue
      }
      if (field.maxlength != null) {
        item.maxlength = field.maxlength
      }
      if (field.min != null) {
        item.min = field.min
      }
      if (field.max != null) {
        item.max = field.max
      }
      if (field.clearable === true) {
        item.clearable = true
      }
      if (field.rows != null) {
        item.rows = field.rows
      }
      if (field.onChange && field.onChange.trim()) {
        item.onChange = field.onChange.trim()
      }
      return item
    })
  return JSON.stringify(form)
}

/* ----- 页面壳 / 弹窗壳 ----- */

export function parsePageShell(json: string | null | undefined): PageShellState {
  const fallback = {
    title: '',
    description: '',
    showCount: true,
    countFormat: '共 {total} 条',
    emptyText: '暂无数据',
  }
  if (!json || !json.trim()) {
    return { ...fallback }
  }
  try {
    const parsed: unknown = JSON.parse(json)
    if (!isRecord(parsed)) {
      return { ...fallback }
    }
    return {
      title: readString(parsed, 'title') ?? '',
      description: readString(parsed, 'description') ?? '',
      showCount: typeof parsed.showCount === 'boolean' ? parsed.showCount : fallback.showCount,
      countFormat: readString(parsed, 'countFormat') ?? fallback.countFormat,
      emptyText: readString(parsed, 'emptyText') ?? fallback.emptyText,
    }
  } catch {
    return { ...fallback }
  }
}

export function pageShellToJson(state: PageShellState): string | null {
  const item: Record<string, unknown> = {}
  if (state.title && state.title.trim()) {
    item.title = state.title.trim()
  }
  if (state.description && state.description.trim()) {
    item.description = state.description.trim()
  }
  if (state.showCount !== true) {
    item.showCount = false
  }
  if (state.countFormat && state.countFormat !== '共 {total} 条') {
    item.countFormat = state.countFormat.trim()
  }
  if (state.emptyText && state.emptyText !== '暂无数据') {
    item.emptyText = state.emptyText.trim()
  }
  return Object.keys(item).length === 0 ? null : JSON.stringify(item)
}

export function parseDialogShell(json: string | null | undefined): DialogShellState {
  const fallback = {
    width: '640px',
    createTitle: '',
    editTitle: '',
    submitText: '保存',
    cancelText: '取消',
    createMessage: '',
    updateMessage: '',
    reloadAfterSubmit: true,
  }
  if (!json || !json.trim()) {
    return { ...fallback }
  }
  try {
    const parsed: unknown = JSON.parse(json)
    if (!isRecord(parsed)) {
      return { ...fallback }
    }
    return {
      width: readString(parsed, 'width') ?? fallback.width,
      createTitle: readString(parsed, 'createTitle') ?? '',
      editTitle: readString(parsed, 'editTitle') ?? '',
      submitText: readString(parsed, 'submitText') ?? fallback.submitText,
      cancelText: readString(parsed, 'cancelText') ?? fallback.cancelText,
      createMessage: readString(parsed, 'createMessage') ?? '',
      updateMessage: readString(parsed, 'updateMessage') ?? '',
      reloadAfterSubmit:
        typeof parsed.reloadAfterSubmit === 'boolean'
          ? parsed.reloadAfterSubmit
          : fallback.reloadAfterSubmit,
    }
  } catch {
    return { ...fallback }
  }
}

export function dialogShellToJson(state: DialogShellState): string | null {
  const item: Record<string, unknown> = {}
  if (state.width && state.width !== '640px') {
    item.width = state.width.trim()
  }
  if (state.createTitle && state.createTitle.trim()) {
    item.createTitle = state.createTitle.trim()
  }
  if (state.editTitle && state.editTitle.trim()) {
    item.editTitle = state.editTitle.trim()
  }
  if (state.submitText && state.submitText !== '保存') {
    item.submitText = state.submitText.trim()
  }
  if (state.cancelText && state.cancelText !== '取消') {
    item.cancelText = state.cancelText.trim()
  }
  if (state.createMessage && state.createMessage.trim()) {
    item.createMessage = state.createMessage.trim()
  }
  if (state.updateMessage && state.updateMessage.trim()) {
    item.updateMessage = state.updateMessage.trim()
  }
  if (state.reloadAfterSubmit !== true) {
    item.reloadAfterSubmit = false
  }
  return Object.keys(item).length === 0 ? null : JSON.stringify(item)
}

/** 新配置的默认示例(自带 1 列 + 1 查询 + 空按钮,让新手照着改) */
export function createBlankFormState(): ListConfigFormState {
  return {
    columns: [
      { prop: 'id', label: 'ID', type: 'text', width: 120 },
      { prop: 'name', label: '名称', type: 'text', minWidth: 160 },
    ],
    query: [{ field: 'keyword', label: '关键字', type: 'input', placeholder: '按名称搜索' }],
    actions: [],
    form: { gridColumns: 2, fields: [] },
    page: {
      title: '',
      description: '',
      showCount: true,
      countFormat: '共 {total} 条',
      emptyText: '暂无数据',
    },
    dialog: {
      width: '640px',
      createTitle: '',
      editTitle: '',
      submitText: '保存',
      cancelText: '取消',
      createMessage: '',
      updateMessage: '',
      reloadAfterSubmit: true,
    },
  }
}
