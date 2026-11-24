<template>
  <section class="education-page schema-dictionary">
    <div class="table-toolbar">
      <el-button :icon="Refresh" circle :loading="loading" @click="load" />
      <el-input
        v-model="keyword"
        placeholder="搜表名 / 字段名 / 中文说明"
        clearable
        class="schema-search"
      />
      <div class="education-context-text">
        这里看表结构与字段含义；枚举值维护在「字典管理」，不在这里。结构只读，加字段/改类型一律走 Flyway（找开发）。
      </div>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />

    <div class="dashboard-grid education-stat-grid">
      <article class="stat-card">
        <div class="stat-label">表</div>
        <div class="stat-value">{{ dictionary?.tableCount ?? 0 }}</div>
        <div class="stat-hint">当前库 {{ dictionary?.database || '-' }}</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">字段</div>
        <div class="stat-value">{{ dictionary?.columnCount ?? 0 }}</div>
        <div class="stat-hint">含主键与审计字段</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">缺中文注释</div>
        <div class="stat-value" :class="{ 'stat-value-danger': (dictionary?.missingCommentCount ?? 0) > 0 }">
          {{ dictionary?.missingCommentCount ?? 0 }}
        </div>
        <div class="stat-hint">按约定应为 0，非 0 即违规</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">已登记维护入口</div>
        <div class="stat-value">{{ maintenanceCount }}</div>
        <div class="stat-hint">表 → 可改页面的登记数</div>
      </article>
    </div>

    <div class="schema-layout">
      <div class="panel-card schema-list">
        <div v-for="group in groups" :key="group.key" class="schema-group">
          <div class="schema-group-head">
            {{ group.label }}
            <span>{{ group.tables.length }}</span>
          </div>
          <button
            v-for="table in group.tables"
            :key="table.tableName"
            type="button"
            class="schema-table-item"
            :class="{ 'is-active': selected?.tableName === table.tableName }"
            @click="selectedName = table.tableName"
          >
            <span class="schema-table-name">{{ table.tableName }}</span>
            <span class="schema-table-comment">{{ table.tableComment || '（无表注释）' }}</span>
            <span v-if="!table.maintenance" class="schema-table-flag" title="没有登记维护入口">无入口</span>
          </button>
        </div>
        <div v-if="!groups.length" class="schema-empty">没有匹配的表，换个关键词试试</div>
      </div>

      <div class="schema-detail">
        <div v-if="!selected" class="panel-card schema-empty-large">
          在左侧选一张表，看它的字段、注释和去哪个页面维护。
        </div>

        <template v-else>
          <article class="panel-card schema-head">
            <div class="schema-head-main">
              <div class="schema-title-row">
                <strong class="schema-title">{{ selected.tableName }}</strong>
                <el-tag size="small" effect="plain">{{ moduleLabel(selected.moduleKey) }}</el-tag>
                <el-tag v-if="selected.maintenance" size="small" type="success" effect="plain">
                  可维护
                </el-tag>
                <el-tag v-else size="small" type="info" effect="plain">无登记入口</el-tag>
              </div>
              <div class="schema-comment">{{ selected.tableComment || '（这张表没有中文注释，属违规）' }}</div>
              <div class="schema-meta">
                <span>字段 <b>{{ selected.columns.length }}</b></span>
                <span v-if="selected.entityClass">实体 <code>{{ selected.entityClass }}</code></span>
                <span v-else>无对应实体（视图或遗留表）</span>
              </div>
            </div>
            <div class="schema-entry">
              <el-button size="small" plain @click="viewEndpoints(selected.tableName)">看接口</el-button>
              <template v-if="selected.maintenance">
                <el-button
                  v-if="selected.maintenance.path"
                  type="primary"
                  plain
                  @click="goMaintenance(selected.maintenance.path)"
                >
                  去维护：{{ selected.maintenance.label }}
                </el-button>
                <el-tag v-else type="info" effect="plain">{{ selected.maintenance.label }}</el-tag>
                <div class="schema-entry-remark">{{ selected.maintenance.remark }}</div>
              </template>
            </div>
          </article>

          <article class="panel-card schema-columns">
            <el-table :data="selected.columns" border height="100%" row-key="columnName" size="small">
              <el-table-column prop="columnName" label="字段名" width="190" fixed="left" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="schema-field-name">{{ row.columnName }}</span>
                  <el-tag v-if="row.primaryKey" size="small" type="warning" effect="plain">PK</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="中文说明" min-width="260" show-overflow-tooltip>
                <template #default="{ row }">
                  <span :class="{ 'schema-missing': !row.comment }">
                    {{ row.comment || '（缺注释）' }}
                  </span>
                  <!-- 实体 @Schema 与库注释措辞不同很常见（0/1 与 false/true、标点、补充说明），
                       只做浅色附注，不做成告警——171/814 的差异率说明逐个标红只会变噪音 -->
                  <div
                    v-if="row.schemaDescription && row.schemaDescription !== row.comment"
                    class="schema-annotation"
                  >
                    注解：{{ row.schemaDescription }}
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="columnType" label="类型" width="150" show-overflow-tooltip />
              <el-table-column label="可空" width="70" align="center">
                <template #default="{ row }">{{ row.nullable ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column prop="defaultValue" label="默认值" width="110" show-overflow-tooltip />
              <el-table-column label="Java 字段" width="170" show-overflow-tooltip>
                <template #default="{ row }">
                  <code v-if="row.javaField">{{ row.javaField }}</code>
                  <span v-else class="schema-muted">—</span>
                </template>
              </el-table-column>
              <el-table-column label="枚举值" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">
                  <span v-if="row.allowableValues?.length">
                    {{ row.allowableValues.join(' / ') }}
                  </span>
                  <span v-else class="schema-muted">—</span>
                </template>
              </el-table-column>
            </el-table>
          </article>
        </template>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { fetchSchemaDictionary, type SchemaTable } from '@cornerstone/core/api/system'

const MODULE_LABELS: Record<string, string> = {
  core: '底座',
  organization: '组织',
  education: '教务',
  other: '其他',
}
const MODULE_ORDER = ['core', 'organization', 'education', 'other']

const router = useRouter()
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const dictionary = ref<Awaited<ReturnType<typeof fetchSchemaDictionary>> | null>(null)
const selectedName = ref('')

const moduleLabel = (key: string) => MODULE_LABELS[key] || key

/** 搜索在已加载数据上做：表名、表注释、字段名、字段注释四处全命中才算 */
const filteredTables = computed<SchemaTable[]>(() => {
  const tables = dictionary.value?.tables ?? []
  const word = keyword.value.trim().toLowerCase()
  if (!word) {
    return tables
  }
  return tables.filter((table) => {
    if (table.tableName.toLowerCase().includes(word)) {
      return true
    }
    if (table.tableComment.toLowerCase().includes(word)) {
      return true
    }
    return table.columns.some(
      (column) =>
        column.columnName.toLowerCase().includes(word) ||
        (column.comment || '').toLowerCase().includes(word)
    )
  })
})

const groups = computed(() => {
  const byModule = new Map<string, SchemaTable[]>()
  for (const table of filteredTables.value) {
    const list = byModule.get(table.moduleKey) ?? []
    list.push(table)
    byModule.set(table.moduleKey, list)
  }
  const keys = [...byModule.keys()].sort((left, right) => {
    const leftIndex = MODULE_ORDER.indexOf(left)
    const rightIndex = MODULE_ORDER.indexOf(right)
    return (leftIndex < 0 ? 99 : leftIndex) - (rightIndex < 0 ? 99 : rightIndex)
  })
  return keys.map((key) => ({ key, label: moduleLabel(key), tables: byModule.get(key) ?? [] }))
})

const selected = computed<SchemaTable | null>(() => {
  const tables = filteredTables.value
  if (!tables.length) {
    return null
  }
  return tables.find((table) => table.tableName === selectedName.value) ?? tables[0]
})

const maintenanceCount = computed(
  () => (dictionary.value?.tables ?? []).filter((table) => table.maintenance).length
)

async function load() {
  loading.value = true
  error.value = ''
  try {
    dictionary.value = await fetchSchemaDictionary()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '数据字典加载失败'
  } finally {
    loading.value = false
  }
}

function goMaintenance(path?: string | null) {
  if (!path) {
    ElMessage.info('该表没有登记管理页面，只能由开发维护')
    return
  }
  router.push(path)
}

/** 带着表名跳到接口链路，直接看"哪些接口会碰这张表" */
function viewEndpoints(tableName: string) {
  router.push({ path: '/system/endpoint-chain', query: { table: tableName } })
}

onMounted(() => {
  void load()
})
</script>

<style scoped>
.schema-dictionary {
  gap: 12px;
}

.schema-search {
  width: 260px;
  flex-shrink: 0;
}

.stat-value-danger {
  color: var(--el-color-danger);
}

.schema-layout {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 12px;
  align-items: stretch;
  /* 视口填充:basis 必须写 0,写 auto 会按内容长高、左清单右详情就永远不滚 */
  flex: 1 1 0;
  min-height: 320px;
}

.schema-list {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: auto;
  padding: 10px;
  gap: 4px;
}

.schema-group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 6px 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.schema-group-head span {
  font-weight: 400;
}

.schema-table-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-areas:
    'name flag'
    'comment comment';
  gap: 0 6px;
  width: 100%;
  padding: 6px 8px;
  text-align: left;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
}

.schema-table-item:hover {
  background: var(--el-fill-color-light);
}

.schema-table-item.is-active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}

.schema-table-name {
  grid-area: name;
  font-family: monospace;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.schema-table-comment {
  grid-area: comment;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.schema-table-flag {
  grid-area: flag;
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

.schema-empty,
.schema-empty-large {
  padding: 24px 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.schema-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}

.schema-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-shrink: 0;
  padding: 12px 14px;
}

.schema-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.schema-title {
  font-family: monospace;
  font-size: 16px;
}

.schema-comment {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.schema-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.schema-meta code,
.schema-entry-remark,
.schema-muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.schema-entry {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex-shrink: 0;
}

.schema-columns {
  flex: 1 1 0;
  min-height: 0;
  overflow: hidden;
  padding: 10px;
}

.schema-field-name {
  margin-right: 6px;
  font-family: monospace;
}

.schema-missing {
  color: var(--el-color-danger);
}

.schema-annotation {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  white-space: normal;
}
</style>
