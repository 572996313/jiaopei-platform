<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import DictSelect from '@/components/DictSelect.vue'
import { requestGet } from '@/api/request'
import type { PageResult } from '@/api/common'
import type { ListConfigSummary } from '@/api/system'
import { useDictStore } from '@/stores/dict'
import { useListConfigStore } from '@/stores/listConfig'
import { ConfigurableCell, collectDictTypes, optionLoaders, type RenderContext } from './renderers'
import type { ListColumnConfig, ListPageConfig, QueryFieldConfig, QueryOption } from './types'

const props = withDefaults(
  defineProps<{
    /** 列表配置资源编码,对应 sys_list_config.resource_code,如 system-users */
    resourceCode: string
    /** 渲染上下文:宿主页面注入的数据与映射函数(机构/校区名称、机构下拉选项等) */
    context?: RenderContext
    /** 操作列宽度,使用 #actions 插槽时生效 */
    actionsWidth?: number
  }>(),
  { actionsWidth: 180 },
)

const emit = defineEmits<{
  loaded: [payload: { total: number; records: Record<string, any>[] }]
}>()

const listConfigStore = useListConfigStore()
const dictStore = useDictStore()

const config = ref<ListPageConfig | null>(null)
const configError = ref('')
const loading = ref(false)
const rows = ref<Record<string, any>[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const queryModel = reactive<Record<string, string>>({})

// 竞态守卫:快速切换查询/翻页时丢弃过期响应
let fetchToken = 0

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function parseConfig(summary: ListConfigSummary): ListPageConfig {
  let columns: unknown
  let queryFields: unknown
  try {
    columns = JSON.parse(summary.columnsJson)
    queryFields = summary.queryJson ? JSON.parse(summary.queryJson) : []
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
    initQueryModel(parsed)
    const dictTypes = collectDictTypes(parsed)
    if (dictTypes.length) {
      await dictStore.ensureLoaded(dictTypes)
    }
    await fetchPage()
  } catch (error) {
    configError.value = toMessage(error, '列表配置加载失败')
  }
}

function buildQueryParams(): Record<string, unknown> {
  const params: Record<string, unknown> = { page: page.value, pageSize: pageSize.value }
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
    const result = await requestGet<PageResult<Record<string, any>>>(current.endpoint, buildQueryParams())
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
    const loader = optionLoaders[field.optionsLoader]
    if (loader) {
      return loader(props.context)
    }
  }
  return []
}

watch(
  () => props.resourceCode,
  () => {
    void loadConfig()
  },
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
    <el-alert v-if="configError" :title="configError" type="error" show-icon :closable="false" class="education-alert" />

    <template v-else-if="config">
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

      <el-table :data="rows" border v-loading="loading">
        <el-table-column
          v-for="column in config.columns"
          :key="column.prop"
          :prop="column.prop"
          :label="column.label"
          :width="column.width"
          :min-width="column.minWidth"
          :show-overflow-tooltip="column.showOverflowTooltip"
        >
          <template #default="{ row }">
            <ConfigurableCell :column="column" :row="row" :context="context" />
          </template>
        </el-table-column>
        <el-table-column v-if="$slots.actions" label="操作" :width="actionsWidth" fixed="right">
          <template #default="{ row }">
            <div class="system-operation-group">
              <slot name="actions" :row="row" />
            </div>
          </template>
        </el-table-column>
      </el-table>

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
