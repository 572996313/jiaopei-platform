<template>
  <section class="education-page document-lineage">
    <div class="table-toolbar">
      <el-button :icon="Refresh" circle :loading="loading" @click="load" />
      <el-input
        v-model="keyword"
        placeholder="搜单据 / 表名 / 关联字段 / 说明"
        clearable
        class="lineage-search"
      />
      <el-checkbox v-model="onlyPending">只看待确认</el-checkbox>
      <el-checkbox v-model="onlyRelated">只看有上下游</el-checkbox>
      <div class="education-context-text">
        所有表都在这里：模块登记的单据显示业务关系，未登记的表标为「仅表」，数据库外键自动补成「引用」。
      </div>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />

    <div class="dashboard-grid education-stat-grid">
      <article class="stat-card">
        <div class="stat-label">节点</div>
        <div class="stat-value">{{ lineage?.documentCount ?? 0 }}</div>
        <div class="stat-hint">覆盖 {{ tableCount }} 张表</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">关系</div>
        <div class="stat-value">{{ lineage?.edgeCount ?? 0 }}</div>
        <div class="stat-hint">声明关系 + 外键引用</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">待确认</div>
        <div class="stat-value">{{ lineage?.pendingCount ?? 0 }}</div>
        <div class="stat-hint">业务口径尚未拍板</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">仅表</div>
        <div class="stat-value">{{ standaloneCount }}</div>
        <div class="stat-hint">未登记业务关系</div>
      </article>
    </div>

    <div class="lineage-layout">
      <aside class="panel-card lineage-list">
        <div v-for="group in groups" :key="group.key" class="lineage-group">
          <div class="lineage-group-head">
            {{ group.label }}
            <span>{{ group.nodes.length }}</span>
          </div>
          <button
            v-for="node in group.nodes"
            :key="node.code"
            type="button"
            class="lineage-node-item"
            :class="{ 'is-active': selected?.code === node.code }"
            @click="selectedCode = node.code"
          >
            <span class="lineage-node-name">{{ node.name }}</span>
            <code class="lineage-node-table">{{ node.primaryTable }}</code>
            <el-tag v-if="!node.declared" size="small" type="info" effect="plain">仅表</el-tag>
            <el-tag v-else-if="isPending(node)" size="small" type="warning" effect="plain">待确认</el-tag>
          </button>
        </div>
        <div v-if="!groups.length" class="lineage-empty">
          没有匹配的单据，换个关键词试试——直接搜表名也可以。
        </div>
      </aside>

      <div class="lineage-detail">
        <div v-if="!selected" class="panel-card lineage-empty-large">
          当前模块没有登记业务单据。实现模块的 <code>DocumentLineageContributor</code> 后会自动出现。
        </div>

        <template v-else>
          <article class="panel-card lineage-head">
            <div class="lineage-title-row">
              <strong class="lineage-title">{{ selected.name }}</strong>
              <el-tag size="small" effect="plain">{{ moduleLabel(selected.moduleKey) }}</el-tag>
              <el-tag v-if="!selected.declared" size="small" type="info" effect="plain">
                仅表 · 未登记关系
              </el-tag>
              <el-tag v-if="!selected.confirmed" size="small" type="warning" effect="plain">
                业务口径待确认
              </el-tag>
              <el-button
                v-if="selected.pagePath"
                link
                type="primary"
                size="small"
                @click="goPage(selected.pagePath)"
              >
                去页面
              </el-button>
            </div>
            <div class="lineage-code">{{ selected.code }}</div>
            <div v-if="selected.description" class="lineage-description">
              {{ selected.description }}
            </div>
            <div class="lineage-tables">
              <span class="lineage-tables-label">数据表</span>
              <el-tooltip
                v-for="table in tablesOf(selected)"
                :key="table"
                content="在接口链路里反查这张表"
                placement="top"
              >
                <el-tag size="small" class="lineage-table-tag" @click="goTable(table)">
                  {{ table }}
                </el-tag>
              </el-tooltip>
            </div>
            <el-alert
              v-if="selected.remark"
              :title="selected.remark"
              type="warning"
              :closable="false"
              show-icon
              class="lineage-remark"
            />
            <el-alert
              v-if="!selected.declared"
              title="这张表还没有登记业务上下游关系；如果它属于某个单据，请在模块的 DocumentLineageContributor 里补充。"
              type="info"
              :closable="false"
              show-icon
              class="lineage-remark"
            />
          </article>

          <div class="lineage-flow">
            <article class="panel-card lineage-column">
              <div class="lineage-column-title">
                上游 · 根据这些单据生成
                <span>{{ upstream.length }}</span>
              </div>
              <button
                v-for="item in upstream"
                :key="edgeKey(item.edge)"
                type="button"
                class="lineage-edge-card"
                @click="selectedCode = item.node.code"
              >
                <div class="lineage-edge-head">
                  <strong>{{ item.node.name }}</strong>
                  <el-tag size="small" effect="plain">{{ item.edge.relation }}</el-tag>
                  <el-tag v-if="!item.edge.declared" size="small" type="info" effect="plain">
                    外键
                  </el-tag>
                  <el-tag v-if="!item.edge.confirmed" size="small" type="warning" effect="plain">
                    待确认
                  </el-tag>
                </div>
                <div class="lineage-edge-meta">
                  关联字段 <code>{{ item.edge.linkFields.join('、') }}</code>
                </div>
                <div v-if="item.edge.triggerLabel" class="lineage-edge-meta">
                  触发 {{ item.edge.triggerLabel }}
                </div>
                <div v-if="item.edge.triggerEndpoint" class="lineage-edge-endpoint">
                  {{ item.edge.triggerEndpoint }}
                </div>
                <div v-if="item.edge.description" class="lineage-edge-desc">
                  {{ item.edge.description }}
                </div>
              </button>
              <div v-if="!upstream.length" class="lineage-muted">没有已登记的上游单据</div>
            </article>

            <article class="panel-card lineage-column lineage-current">
              <div class="lineage-column-title">当前单据</div>
              <div class="lineage-current-card">
                <strong>{{ selected.name }}</strong>
                <code>{{ selected.primaryTable }}</code>
                <span class="lineage-muted">
                  上游 {{ upstream.length }} · 下游 {{ downstream.length }}
                </span>
              </div>
              <div class="lineage-muted">
                点左右两栏的单据可切换当前单据，顺着关系一跳一跳走；不会一次展开整张图。
              </div>
            </article>

            <article class="panel-card lineage-column">
              <div class="lineage-column-title">
                下游 · 生成这些单据
                <span>{{ downstream.length }}</span>
              </div>
              <button
                v-for="item in downstream"
                :key="edgeKey(item.edge)"
                type="button"
                class="lineage-edge-card"
                @click="selectedCode = item.node.code"
              >
                <div class="lineage-edge-head">
                  <strong>{{ item.node.name }}</strong>
                  <el-tag size="small" effect="plain">{{ item.edge.relation }}</el-tag>
                  <el-tag v-if="!item.edge.declared" size="small" type="info" effect="plain">
                    外键
                  </el-tag>
                  <el-tag v-if="!item.edge.confirmed" size="small" type="warning" effect="plain">
                    待确认
                  </el-tag>
                </div>
                <div class="lineage-edge-meta">
                  关联字段 <code>{{ item.edge.linkFields.join('、') }}</code>
                </div>
                <div v-if="item.edge.triggerLabel" class="lineage-edge-meta">
                  触发 {{ item.edge.triggerLabel }}
                </div>
                <div v-if="item.edge.triggerEndpoint" class="lineage-edge-endpoint">
                  {{ item.edge.triggerEndpoint }}
                </div>
                <div v-if="item.edge.description" class="lineage-edge-desc">
                  {{ item.edge.description }}
                </div>
              </button>
              <div v-if="!downstream.length" class="lineage-muted">没有已登记的下游单据</div>
            </article>
          </div>

          <article class="panel-card lineage-note">{{ lineage?.note }}</article>
        </template>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import {
  fetchDocumentLineage,
  type DocumentLineage,
  type DocumentLineageEdge,
  type DocumentLineageNode,
} from '@cornerstone/core/api/system'

const MODULE_LABELS: Record<string, string> = {
  core: '底座',
  organization: '组织',
  education: '教务',
  other: '其他',
}
const MODULE_ORDER = ['core', 'organization', 'education', 'other']

interface EdgeHit {
  edge: DocumentLineageEdge
  node: DocumentLineageNode
}

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const error = ref('')
const lineage = ref<DocumentLineage | null>(null)
// 数据字典/接口链路带 ?table= 过来时直接按表名反查
const keyword = ref(typeof route.query.table === 'string' ? route.query.table : '')
const onlyPending = ref(false)
const onlyRelated = ref(false)
const selectedCode = ref(typeof route.query.code === 'string' ? route.query.code : '')

const moduleLabel = (key: string) => MODULE_LABELS[key] || key

const nodeByCode = computed(() => {
  const map = new Map<string, DocumentLineageNode>()
  for (const node of lineage.value?.documents ?? []) {
    map.set(node.code, node)
  }
  return map
})

/** 节点自身待确认，或有任一条相邻关系待确认，都算「待确认」 */
function isPending(node: DocumentLineageNode) {
  if (!node.confirmed) {
    return true
  }
  return (lineage.value?.edges ?? []).some(
    (edge) => (edge.from === node.code || edge.to === node.code) && !edge.confirmed,
  )
}

function hasEdges(node: DocumentLineageNode) {
  return (lineage.value?.edges ?? []).some(
    (edge) => edge.from === node.code || edge.to === node.code,
  )
}

const filteredNodes = computed<DocumentLineageNode[]>(() => {
  const word = keyword.value.trim().toLowerCase()
  return (lineage.value?.documents ?? []).filter((node) => {
    if (onlyPending.value && !isPending(node)) {
      return false
    }
    if (onlyRelated.value && !hasEdges(node)) {
      return false
    }
    if (!word) {
      return true
    }
    return [
      node.name,
      node.code,
      node.primaryTable,
      node.description,
      node.remark,
      ...node.relatedTables,
    ]
      .filter(Boolean)
      .join('\n')
      .toLowerCase()
      .includes(word)
  })
})

const groups = computed(() => {
  const byModule = new Map<string, DocumentLineageNode[]>()
  for (const node of filteredNodes.value) {
    const list = byModule.get(node.moduleKey) ?? []
    list.push(node)
    byModule.set(node.moduleKey, list)
  }
  const keys = [...byModule.keys()].sort((left, right) => {
    const leftIndex = MODULE_ORDER.indexOf(left)
    const rightIndex = MODULE_ORDER.indexOf(right)
    return (leftIndex < 0 ? 99 : leftIndex) - (rightIndex < 0 ? 99 : rightIndex)
  })
  return keys.map((key) => ({ key, label: moduleLabel(key), nodes: byModule.get(key) ?? [] }))
})

const selected = computed<DocumentLineageNode | null>(() => {
  const nodes = lineage.value?.documents ?? []
  return nodes.find((node) => node.code === selectedCode.value) ?? nodes[0] ?? null
})

const upstream = computed<EdgeHit[]>(() => {
  const current = selected.value
  if (!current) {
    return []
  }
  return (lineage.value?.edges ?? [])
    .filter((edge) => edge.to === current.code)
    .map((edge) => ({ edge, node: nodeByCode.value.get(edge.from) }))
    .filter((hit): hit is EdgeHit => Boolean(hit.node))
    .sort((left, right) => left.node.name.localeCompare(right.node.name, 'zh-CN'))
})

const downstream = computed<EdgeHit[]>(() => {
  const current = selected.value
  if (!current) {
    return []
  }
  return (lineage.value?.edges ?? [])
    .filter((edge) => edge.from === current.code)
    .map((edge) => ({ edge, node: nodeByCode.value.get(edge.to) }))
    .filter((hit): hit is EdgeHit => Boolean(hit.node))
    .sort((left, right) => left.node.name.localeCompare(right.node.name, 'zh-CN'))
})

const tableCount = computed(() => {
  const names = new Set<string>()
  for (const node of lineage.value?.documents ?? []) {
    names.add(node.primaryTable)
    for (const table of node.relatedTables) {
      names.add(table)
    }
  }
  return names.size
})

const standaloneCount = computed(
  () => (lineage.value?.documents ?? []).filter((node) => !node.declared).length,
)

const tablesOf = (node: DocumentLineageNode) => [node.primaryTable, ...node.relatedTables]

const edgeKey = (edge: DocumentLineageEdge) => `${edge.from}->${edge.to}:${edge.relation}`

function goPage(path: string) {
  void router.push(path)
}

function goTable(table: string) {
  void router.push({ path: '/system/endpoint-chain', query: { table } })
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await fetchDocumentLineage()
    lineage.value = data
    const queryCode = typeof route.query.code === 'string' ? route.query.code : ''
    if (queryCode && data.documents.some((node) => node.code === queryCode)) {
      selectedCode.value = queryCode
    } else if (!data.documents.some((node) => node.code === selectedCode.value)) {
      selectedCode.value = data.documents[0]?.code ?? ''
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '业务链路加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<style scoped>
.document-lineage {
  gap: 12px;
}

.lineage-search {
  width: 320px;
  flex-shrink: 0;
}

.lineage-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 12px;
  align-items: stretch;
  /* 视口填充：basis 必须写 0，写 auto 会按内容长高、左右两块就永远不滚 */
  flex: 1 1 0;
  min-height: 320px;
}

.lineage-list {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: auto;
  padding: 10px;
  gap: 4px;
}

.lineage-group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 6px 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.lineage-group-head span {
  font-weight: 400;
}

.lineage-node-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-areas:
    'name tag'
    'table tag';
  gap: 2px 8px;
  width: 100%;
  padding: 7px 8px;
  text-align: left;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
}

.lineage-node-item:hover {
  background: var(--el-fill-color-light);
}

.lineage-node-item.is-active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}

.lineage-node-name {
  grid-area: name;
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lineage-node-table {
  grid-area: table;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lineage-node-item :deep(.el-tag) {
  grid-area: tag;
  align-self: center;
}

.lineage-empty,
.lineage-empty-large {
  padding: 24px 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.lineage-detail {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  overflow: hidden;
}

.lineage-head {
  flex-shrink: 0;
  padding: 12px 14px;
}

.lineage-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.lineage-title {
  font-size: 16px;
}

.lineage-code {
  margin-top: 4px;
  font-family: monospace;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.lineage-description {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}

.lineage-tables {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
}

.lineage-tables-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.lineage-table-tag {
  cursor: pointer;
  font-family: monospace;
}

.lineage-remark {
  margin-top: 10px;
}

.lineage-flow {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 210px minmax(0, 1fr);
  gap: 10px;
  flex: 1 1 0;
  min-height: 0;
}

.lineage-column {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  overflow: auto;
  padding: 12px;
}

.lineage-column-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.lineage-column-title span {
  font-weight: 400;
}

.lineage-current {
  justify-content: flex-start;
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}

.lineage-current-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 8px;
}

.lineage-current-card code {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.lineage-edge-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px;
  text-align: left;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  cursor: pointer;
}

.lineage-edge-card:hover {
  border-color: var(--el-color-primary-light-5);
}

.lineage-edge-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 13px;
}

.lineage-edge-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.lineage-edge-meta code {
  color: var(--el-text-color-regular);
}

.lineage-edge-endpoint {
  font-family: monospace;
  font-size: 11px;
  color: var(--el-color-primary);
  word-break: break-all;
}

.lineage-edge-desc {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}

.lineage-muted {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.lineage-note {
  flex-shrink: 0;
  padding: 10px 14px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

@media (max-width: 1200px) {
  .lineage-layout {
    grid-template-columns: 280px minmax(0, 1fr);
  }

  .lineage-flow {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
