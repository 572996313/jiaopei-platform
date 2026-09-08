import { defineComponent, h, type PropType, type VNode } from 'vue'
import { ElTag, ElTooltip } from 'element-plus'
import { useDictStore } from '@/stores/dict'
import { permissionLabel } from '@/constants/permissionLabels'
import type { ListColumnConfig, QueryOption } from './types'

/**
 * 通用列表的渲染上下文:由宿主页面注入的数据与映射函数,
 * 代码侧渲染器按名字引用配置(context 提供运行时数据)。
 */
export interface RenderContext {
  /** 机构 ID → 名称 */
  formatInstitution?: (id?: string | null) => string
  /** 校区 ID → 名称 */
  formatCampus?: (id?: string | null) => string
  /** 机构下拉选项(查询条件 optionsLoader=institutions 使用) */
  institutionOptions?: QueryOption[]
}

type ElTagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

type CustomRenderer = (row: Record<string, any>, context?: RenderContext) => VNode

function renderTag(type: string, label: string): VNode {
  return h(ElTag, { type: type as ElTagType, size: 'small' }, { default: () => label })
}

/**
 * 代码侧预置渲染器注册表:配置通过 renderer 名字引用。
 * 用于无法用通用类型描述的自定义单元格(如多字段合成、带 tooltip 的标签组)。
 */
const customRenderers: Record<string, CustomRenderer> = {
  institutionName: (row, context) => h('span', context?.formatInstitution?.(row.institutionId) ?? '-'),
  campusName: (row, context) => h('span', context?.formatCampus?.(row.defaultCampusId) ?? '-'),
  permissionTags: (row) =>
    renderTagList(
      (Array.isArray(row.permissionCodes) ? row.permissionCodes : []).map((code: string) => ({
        key: code,
        label: permissionLabel(code),
        tooltip: code,
      })),
    ),
  passwordStatus: (row) => {
    const needChange = Boolean(row.forceChangePassword || row.passwordResetRequired)
    return renderTag(needChange ? 'warning' : 'success', needChange ? '需强制改密' : '正常')
  },
}

/**
 * 查询条件下拉的选项加载器注册表:配置通过 optionsLoader 名字引用,
 * 优先使用宿主注入的数据(context),避免重复请求。
 */
export const optionLoaders: Record<string, (context?: RenderContext) => QueryOption[]> = {
  institutions: (context) => context?.institutionOptions ?? [],
}

function renderTagList(items: Array<{ key: string; label: string; tooltip?: string }>): VNode {
  if (!items.length) {
    return h('span', '-')
  }
  const tags = items.map((item) =>
    h(
      ElTag,
      { key: item.key, size: 'small' },
      {
        default: () =>
          item.tooltip
            ? h(ElTooltip, { content: item.tooltip, placement: 'top' }, { default: () => h('span', item.label) })
            : item.label,
      },
    ),
  )
  return h('div', { class: 'system-chip-list' }, tags)
}

function formatDateTime(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  const date = new Date(String(value))
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }
  const pad = (input: number) => String(input).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function text(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return String(value)
}

/**
 * 按列配置渲染单元格:renderer 优先,其次按 type 分派,缺省纯文本。
 */
export function renderCell(
  column: ListColumnConfig,
  row: Record<string, any>,
  context?: RenderContext,
): VNode {
  if (column.renderer) {
    const renderer = customRenderers[column.renderer]
    if (renderer) {
      return renderer(row, context)
    }
  }

  const value = row[column.prop]
  switch (column.type) {
    case 'datetime':
      return h('span', formatDateTime(value))
    case 'dict': {
      const dictStore = useDictStore()
      const raw = value === null || value === undefined || value === '' ? null : String(value)
      const label = raw === null ? '-' : dictStore.labelOf(column.dictType ?? '', raw) ?? raw
      const tagType = raw === null ? undefined : column.tagTypeMap?.[raw]
      if (!tagType) {
        return h('span', label)
      }
      return renderTag(tagType, label)
    }
    case 'boolean':
      return h('span', value ? column.trueLabel ?? '是' : column.falseLabel ?? '否')
    case 'tags': {
      const items = Array.isArray(value) ? value : []
      return renderTagList(items.map((item) => ({ key: String(item), label: String(item) })))
    }
    case 'map': {
      const mapped = column.valueMap?.[String(value)]
      return h('span', mapped ?? text(value))
    }
    default:
      return h('span', text(value))
  }
}

/**
 * 单元格渲染组件:稳定的组件身份,供表格列的 #default 插槽使用,
 * 避免在模板里用内联函数式 :is 导致每次渲染都重挂载(tooltip 闪烁)。
 */
export const ConfigurableCell = defineComponent({
  name: 'ConfigurableCell',
  props: {
    column: { type: Object as PropType<ListColumnConfig>, required: true },
    row: { type: Object as PropType<Record<string, any>>, required: true },
    context: { type: Object as PropType<RenderContext | undefined>, default: undefined },
  },
  setup(cellProps) {
    return () => renderCell(cellProps.column, cellProps.row, cellProps.context)
  },
})

/**
 * 列/查询条件里引用的全部字典类型,组件加载配置后批量预热字典缓存。
 */
export function collectDictTypes(config: { columns: ListColumnConfig[]; queryFields?: Array<{ type?: string; dictType?: string }> }): string[] {
  const codes = new Set<string>()
  for (const column of config.columns) {
    if (column.type === 'dict' && column.dictType) {
      codes.add(column.dictType)
    }
  }
  for (const field of config.queryFields ?? []) {
    if (field.type === 'dict-select' && field.dictType) {
      codes.add(field.dictType)
    }
  }
  return Array.from(codes)
}
