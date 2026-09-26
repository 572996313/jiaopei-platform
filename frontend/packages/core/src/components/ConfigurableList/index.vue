<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import DictSelect from '@cornerstone/core/components/DictSelect.vue'
import { requestGet } from '@cornerstone/core/api/request'
import type { PageResult } from '@cornerstone/core/api/common'
import type { ListConfigSummary } from '@cornerstone/core/api/system'
import { useDictStore } from '@cornerstone/core/stores/dict'
import { useListConfigStore } from '@cornerstone/core/stores/listConfig'
import { useDataSourceStore } from '@cornerstone/core/stores/dataSource'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import { useTableSort } from '@cornerstone/core/composables/use-table-sort'
import {
  ConfigurableCell,
  collectDictTypes,
  resolveOptionLoader,
  type RenderContext,
} from './renderers'
import type {
  ActionHandler,
  ListActionConfig,
  ListColumnConfig,
  ListPageConfig,
  QueryFieldConfig,
  QueryOption,
} from './types'

const props = withDefaults(
  defineProps<{
    /** 列表配置资源编码,对应 sys_list_config.resource_code,如 system-users */
    resourceCode: string
    /** 渲染上下文:宿主页面注入的数据与映射函数(机构名称、下拉选项、业务数据目录等) */
    context?: RenderContext
    /** 代码侧按钮动作注册表:配置里 action 名对应的处理器(label/disabled 支持行级动态) */
    actions?: Record<string, ActionHandler>
    /** 操作列宽,不传时按行按钮 label 自动估算(仅行按钮存在时) */
    actionsWidth?: number
    /** 空态文案(表格无数据时展示),缺省 '暂无数据' */
    emptyText?: string
  }>(),
  { actionsWidth: 0, emptyText: '暂无数据' }
)

const emit = defineEmits<{
  loaded: [payload: { total: number; records: Record<string, any>[] }]
}>()

const listConfigStore = useListConfigStore()
const dataSourceStore = useDataSourceStore()
const dictStore = useDictStore()
const auth = useAuthStore()

const config = ref<ListPageConfig | null>(null)
const configError = ref('')
const loading = ref(false)
const rows = ref<Record<string, any>[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const queryModel = reactive<Record<string, string>>({})
const tableRef = ref<{ clearSort: () => void } | null>(null)
/** 表头排序(单列、服务端):空 sortField 表示走接口默认排序 */
const sort = useTableSort({
  onSorted: () => {
    page.value = 1
    void fetchPage()
  },
  clearTableSort: () => tableRef.value?.clearSort(),
})

// 竞态守卫:快速切换查询/翻页时丢弃过期响应
let fetchToken = 0

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function parseConfig(summary: ListConfigSummary): ListPageConfig {
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
  return {
    resourceCode: summary.resourceCode,
    resourceName: summary.resourceName,
    endpoint: summary.endpoint,
    pageSize: summary.pageSize,
    columns: columns as ListColumnConfig[],
    queryFields: Array.isArray(queryFields) ? (queryFields as QueryFieldConfig[]) : [],
    // undefined = 未配置按钮,回退 #actions 插槽;空数组 = 明确无按钮
    actions: Array.isArray(actions) ? (actions as ListActionConfig[]) : undefined,
  }
}

function initQueryModel(pageConfig: ListPageConfig) {
  Object.keys(queryModel).forEach((key) => delete queryModel[key])
  for (const field of pageConfig.queryFields ?? []) {
    queryModel[field.field] = field.defaultValue ?? ''
  }
}

async function loadConfig() {
  configError.value = ''
  config.value = null
  rows.value = []
  total.value = 0
  try {
    const summary = await listConfigStore.getOf(props.resourceCode)
    const parsed = parseConfig(summary)
    config.value = parsed
    pageSize.value = parsed.pageSize && parsed.pageSize > 0 ? parsed.pageSize : 10
    page.value = 1
    sort.resetSort()
    initQueryModel(parsed)
    const dictTypes = collectDictTypes(parsed)
    if (dictTypes.length) {
      await dictStore.ensureLoaded(dictTypes)
    }
    // 预加载查询区 optionsLoader 引用的配置化数据源(宿主未注入的编码)
    const loaderCodes = (parsed.queryFields ?? [])
      .map((field) => field.optionsLoader)
      .filter((code): code is string => Boolean(code))
    if (loaderCodes.length) {
      await dataSourceStore.preloadCodes(loaderCodes)
    }
    await fetchPage()
  } catch (error) {
    configError.value = toMessage(error, '列表配置加载失败')
  }
}

function buildQueryParams(): Record<string, unknown> {
  const params: Record<string, unknown> = {
    page: page.value,
    pageSize: pageSize.value,
    ...sort.sortParams(),
  }
  for (const [key, value] of Object.entries(queryModel)) {
    if (value !== '' && value !== null && value !== undefined) {
      params[key] = value
    }
  }
  return params
}

async function fetchPage() {
  const current = config.value
  if (!current) {
    return
  }
  const token = ++fetchToken
  loading.value = true
  try {
    const result = await requestGet<PageResult<Record<string, any>>>(
      current.endpoint,
      buildQueryParams()
    )
    if (token !== fetchToken) {
      return
    }
    rows.value = result.records || []
    total.value = result.total || 0
    emit('loaded', { total: total.value, records: rows.value })
  } catch (error) {
    if (token !== fetchToken) {
      return
    }
    rows.value = []
    total.value = 0
    configError.value = toMessage(error, '列表数据加载失败')
  } finally {
    if (token === fetchToken) {
      loading.value = false
    }
  }
}

function search() {
  page.value = 1
  void fetchPage()
}

function resetQuery() {
  if (!config.value) {
    return
  }
  initQueryModel(config.value)
  sort.resetSort()
  page.value = 1
  void fetchPage()
}

function onSizeChange() {
  page.value = 1
  void fetchPage()
}

function onPageChange() {
  void fetchPage()
}

function optionsFor(field: QueryFieldConfig): QueryOption[] {
  if (field.options?.length) {
    return field.options
  }
  if (field.optionsLoader) {
    return resolveOptionLoader(field.optionsLoader, props.context)
  }
  return []
}

/* ===== 按钮配置化渲染 ===== */

/** 配置了按钮(即使空数组)即进入配置模式,忽略 #actions 插槽 */
const hasActionsConfig = computed(() => config.value?.actions !== undefined)

/** 按权限码过滤后的按钮配置;无 permission 视为公开 */
const actionConfigs = computed<ListActionConfig[]>(() => {
  const configured = config.value?.actions ?? []
  return configured.filter((action) => {
    if (!action.permission) {
      return true
    }
    return auth.hasPermission(action.permission)
  })
})

const toolbarActions = computed(() =>
  actionConfigs.value.filter((action) => action.position === 'toolbar')
)
const rowActions = computed(() => actionConfigs.value.filter((action) => action.position === 'row'))

/** 行内最多直接平铺 3 个按钮,多余的收进「更多」下拉,保证不换行 */
const MAX_INLINE_ROW_ACTIONS = 3
const inlineRowActions = computed(() => rowActions.value.slice(0, MAX_INLINE_ROW_ACTIONS))
const overflowRowActions = computed(() => rowActions.value.slice(MAX_INLINE_ROW_ACTIONS))

/** 配置模式且未显式传宽时,按行按钮文案估算操作列宽(中文约 14px/字 + 内边距余量) */
const actionColumnWidth = computed(() => {
  if (props.actionsWidth) {
    return props.actionsWidth
  }
  if (!hasActionsConfig.value || rowActions.value.length === 0) {
    return undefined
  }
  const contentWidth = inlineRowActions.value.reduce(
    (sum, action) => sum + action.label.length * 14 + 40,
    0
  )
  const moreWidth = overflowRowActions.value.length ? '更多'.length * 14 + 56 : 0
  return Math.round(contentWidth + moreWidth + 24)
})

/** toolbar 缺省 default;text 样式在工具栏无意义,归一到 default */
function toolbarButtonType(action: ListActionConfig): 'primary' | 'default' | 'danger' {
  if (action.buttonType === 'primary' || action.buttonType === 'danger') {
    return action.buttonType
  }
  return 'default'
}

/** row 缺省 text 样式;el-button text 模式只有 primary/默认/danger 语义,归一到这些 */
function rowButtonType(action: ListActionConfig): 'primary' | 'default' | 'danger' | undefined {
  if (action.buttonType === 'danger') {
    return 'danger'
  }
  if (action.buttonType === 'primary') {
    return 'primary'
  }
  return undefined
}

function actionLabel(action: ListActionConfig, row?: Record<string, any>) {
  const handler = props.actions?.[action.action]
  const dynamic = handler?.label?.(row)
  return dynamic ?? action.label
}

function actionDisabled(action: ListActionConfig, row?: Record<string, any>) {
  const handler = props.actions?.[action.action]
  return handler?.disabled?.(row) ?? false
}

function fireAction(action: ListActionConfig, row?: Record<string, any>) {
  const handler = props.actions?.[action.action]
  if (!handler) {
    console.warn(`[ConfigurableList] 按钮动作 "${action.action}" 未在宿主 actions 注册表中找到`)
    return
  }
  handler.handler(row)
}

/** 「更多」下拉 command 分发:按动作名找回配置再触发 */
function fireActionByName(name: string, row?: Record<string, any>) {
  const action = rowActions.value.find((item) => item.action === name)
  if (action) {
    fireAction(action, row)
  }
}

watch(
  () => props.resourceCode,
  () => {
    void loadConfig()
  }
)

onMounted(() => {
  void loadConfig()
})

defineExpose({
  /** 重新拉取当前页(新增/编辑/删除后由宿主调用) */
  reload: () => fetchPage(),
})
</script>

<template>
  <div class="configurable-list">
    <el-alert
      v-if="configError"
      :title="configError"
      type="error"
      show-icon
      :closable="false"
      class="education-alert"
    />

    <template v-else-if="config">
      <!-- 主操作按钮:放查询条件上方,条件与表格贴着看(与其余列表页同口径) -->
      <div v-if="toolbarActions.length" class="table-toolbar configurable-list-toolbar">
        <el-button
          v-for="action in toolbarActions"
          :key="action.action"
          :type="toolbarButtonType(action)"
          @click="fireAction(action)"
        >
          {{ action.label }}
        </el-button>
      </div>

      <el-form
        v-if="config.queryFields && config.queryFields.length"
        :inline="true"
        class="configurable-list-query"
        @submit.prevent
      >
        <el-form-item v-for="field in config.queryFields" :key="field.field" :label="field.label">
          <el-input
            v-if="!field.type || field.type === 'input'"
            v-model="queryModel[field.field]"
            class="configurable-list-control"
            :placeholder="field.placeholder || `输入${field.label}`"
            clearable
            @keyup.enter="search"
          />
          <el-select
            v-else-if="field.type === 'select'"
            v-model="queryModel[field.field]"
            class="configurable-list-control"
            :placeholder="field.placeholder || '请选择'"
            clearable
          >
            <el-option
              v-for="option in optionsFor(field)"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <DictSelect
            v-else-if="field.type === 'dict-select'"
            v-model="queryModel[field.field]"
            class="configurable-list-control"
            :type-code="field.dictType || ''"
            :placeholder="field.placeholder || '请选择'"
          />
          <el-date-picker
            v-else-if="field.type === 'date'"
            v-model="queryModel[field.field]"
            type="date"
            value-format="YYYY-MM-DD"
            class="configurable-list-control"
            :placeholder="field.placeholder || '选择日期'"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <TableFillBox v-slot="{ height }">
        <el-table
          ref="tableRef"
          :data="rows"
          border
          stripe
          row-key="id"
          highlight-current-row
          v-loading="loading"
          :height="height"
          @sort-change="sort.onSortChange"
        >
          <template #empty>
            <el-empty :description="props.emptyText" :image-size="70" />
          </template>
          <el-table-column
            v-for="column in config.columns"
            :key="column.prop"
            :prop="column.prop"
            :label="column.label"
            :width="column.width"
            :min-width="column.minWidth"
            :fixed="column.fixed"
            :sortable="column.sortable ? 'custom' : false"
            :show-overflow-tooltip="column.showOverflowTooltip !== false"
          >
            <template #default="{ row }">
              <ConfigurableCell :column="column" :row="row" :context="context" />
            </template>
          </el-table-column>
          <!-- 配置模式:按 actions 渲染行按钮(超过 3 个收进「更多」);未配置时回退 #actions 插槽 -->
          <el-table-column
            v-if="rowActions.length > 0"
            label="操作"
            :width="actionColumnWidth"
            fixed="right"
          >
            <template #default="{ row }">
              <div class="system-operation-group">
                <el-button
                  v-for="action in inlineRowActions"
                  :key="action.action"
                  text
                  :type="rowButtonType(action)"
                  :disabled="actionDisabled(action, row)"
                  @click="fireAction(action, row)"
                >
                  {{ actionLabel(action, row) }}
                </el-button>
                <el-dropdown
                  v-if="overflowRowActions.length"
                  trigger="click"
                  @command="(name: string) => fireActionByName(name, row)"
                >
                  <el-button text>
                    更多 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item
                        v-for="action in overflowRowActions"
                        :key="action.action"
                        :command="action.action"
                        :disabled="actionDisabled(action, row)"
                      >
                        {{ actionLabel(action, row) }}
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            v-else-if="$slots.actions"
            label="操作"
            :width="actionsWidth"
            fixed="right"
          >
            <template #default="{ row }">
              <div class="system-operation-group">
                <slot name="actions" :row="row" />
              </div>
            </template>
          </el-table-column>
        </el-table>
      </TableFillBox>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        class="configurable-list-pagination"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </template>
  </div>
</template>

<style scoped>
/* 视口填充:列表占满宿主卡片剩余高度,表格内部滚动 */
.configurable-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1 1 auto;
  min-height: 0;
}

.configurable-list-toolbar {
  display: flex;
  justify-content: flex-start;
  gap: 10px;
  margin-bottom: 4px;
}

.configurable-list-query {
  margin-bottom: 4px;
}

.configurable-list-query :deep(.el-form-item) {
  margin-bottom: 12px;
}

.configurable-list-control {
  width: 200px;
}

.configurable-list-pagination {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>
