<template>
  <section class="education-page endpoint-chain">
    <div class="table-toolbar">
      <el-button :icon="Refresh" circle :loading="loading" @click="load" />
      <el-input
        v-model="keyword"
        placeholder="搜按钮 / 权限码 / 接口 / 服务 / 表名"
        clearable
        class="chain-search"
      />
      <div class="education-context-text">
        这里看一个按钮背后会经过哪些代码与数据表；链路是代码里的声明依赖，不等于某一次请求的实际读写。
      </div>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />

    <div class="dashboard-grid education-stat-grid">
      <article class="stat-card">
        <div class="stat-label">接口</div>
        <div class="stat-value">{{ chain?.endpointCount ?? 0 }}</div>
        <div class="stat-hint">当前启用的模块</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">带权限码</div>
        <div class="stat-value">{{ chain?.permissionCount ?? 0 }}</div>
        <div class="stat-hint">其余接口未声明权限校验</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">涉及数据表</div>
        <div class="stat-value">{{ tableCount }}</div>
        <div class="stat-hint">按 Mapper 泛型去重</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">可追溯按钮</div>
        <div class="stat-value">{{ actionCount }}</div>
        <div class="stat-hint">来自各模块的按钮登记</div>
      </article>
    </div>

    <div class="chain-layout">
      <div class="panel-card chain-list">
        <div v-for="group in groups" :key="group.key" class="chain-group">
          <div class="chain-group-head">
            {{ group.label }}
            <span>{{ group.endpoints.length }}</span>
          </div>
          <button
            v-for="endpoint in group.endpoints"
            :key="endpointKey(endpoint)"
            type="button"
            class="chain-endpoint-item"
            :class="{ 'is-active': selectedKey === endpointKey(endpoint) }"
            @click="selectedKey = endpointKey(endpoint)"
          >
            <span class="chain-method" :data-method="endpoint.method">{{ primaryMethod(endpoint.method) }}</span>
            <span class="chain-path">{{ endpoint.path }}</span>
            <span class="chain-summary">{{ endpoint.summary || '（未写说明）' }}</span>
            <span v-if="endpoint.permissionCode" class="chain-perm">{{ endpoint.permissionCode }}</span>
          </button>
        </div>
        <div v-if="!groups.length" class="chain-empty">
          没有匹配的接口，换个关键词试试——直接搜表名可以反查哪些接口会碰这张表。
        </div>
      </div>

      <div class="chain-detail">
        <div v-if="!selected" class="panel-card chain-empty-large">
          在左侧选一个接口，看它的按钮、服务和可能涉及的数据表。
        </div>

        <template v-else>
          <article class="panel-card chain-head">
            <div class="chain-title-row">
              <strong class="chain-title">{{ selected.summary || selected.path }}</strong>
              <el-tag size="small" effect="plain">{{ moduleLabel(selected.moduleKey) }}</el-tag>
              <el-tag v-if="selected.permissionCode" size="small" type="warning" effect="plain">
                {{ selected.permissionCode }}
              </el-tag>
              <el-tag v-else size="small" type="info" effect="plain">无权限码</el-tag>
            </div>
            <div class="chain-endpoint-line">
              <span class="chain-method" :data-method="selected.method">{{ primaryMethod(selected.method) }}</span>
              <code>{{ selected.path }}</code>
            </div>
            <div v-if="selected.description" class="chain-description">{{ selected.description }}</div>
            <div class="chain-meta">
              控制器 <code>{{ selected.controllerClass }}#{{ selected.controllerMethod }}</code>
            </div>
          </article>

          <article v-if="selected.actions.length" class="panel-card chain-actions">
            <div class="chain-section-title">界面按钮</div>
            <div class="chain-action-row">
              <el-tooltip
                v-for="action in selected.actions"
                :key="action.actionKey"
                :content="action.description || action.label"
                placement="top"
              >
                <el-tag :type="action.sensitive ? 'danger' : 'primary'" effect="plain">
                  {{ action.label }}
                  <span v-if="action.sensitive" class="chain-sensitive">高危</span>
                </el-tag>
              </el-tooltip>
            </div>
          </article>

          <article class="panel-card chain-flow">
            <div class="chain-section-title">链路</div>
            <div class="chain-steps">
              <div class="chain-step">
                <span class="chain-step-no">1</span>
                <div class="chain-step-body">
                  <div class="chain-step-label">接口</div>
                  <div class="chain-step-value">
                    <code>{{ selected.method }} {{ selected.path }}</code>
                  </div>
                </div>
              </div>

              <div class="chain-step">
                <span class="chain-step-no">2</span>
                <div class="chain-step-body">
                  <div class="chain-step-label">控制器</div>
                  <div class="chain-step-value">
                    <code>{{ selected.controllerClass }}#{{ selected.controllerMethod }}</code>
                  </div>
                </div>
              </div>

              <div class="chain-step">
                <span class="chain-step-no">3</span>
                <div class="chain-step-body">
                  <div class="chain-step-label">服务（{{ selected.services.length }}）</div>
                  <div v-if="selected.services.length" class="chain-service-list">
                    <div v-for="service in selected.services" :key="service.className" class="chain-service">
                      <code>{{ service.className }}</code>
                      <span v-if="service.interfaceName !== service.className" class="chain-muted">
                        声明为 {{ service.interfaceName }}
                      </span>
                      <el-tag size="small" effect="plain">{{ moduleLabel(service.moduleKey) }}</el-tag>
                    </div>
                  </div>
                  <div v-else class="chain-muted">控制器没有注入以 Service 结尾的依赖</div>
                </div>
              </div>

              <div class="chain-step">
                <span class="chain-step-no">4</span>
                <div class="chain-step-body">
                  <div class="chain-step-label">可能涉及的数据表（{{ selected.tables.length }}）</div>
                  <div v-if="selected.tables.length" class="chain-table-list">
                    <div v-for="table in selected.tables" :key="table.tableName" class="chain-table">
                      <code class="chain-table-name">{{ table.tableName }}</code>
                      <span class="chain-table-comment">{{ table.tableComment || '（无表注释）' }}</span>
                      <span class="chain-muted">{{ table.mapperClass }}</span>
                    </div>
                  </div>
                  <div v-else class="chain-muted">没有解析到 Mapper 对应的表</div>
                </div>
              </div>
            </div>
          </article>

          <article class="panel-card chain-note">{{ chain?.note }}</article>
        </template>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { fetchEndpointChains, type EndpointChainEndpoint } from '@cornerstone/core/api/system'

const MODULE_LABELS: Record<string, string> = {
  core: '底座',
  organization: '组织',
  education: '教务',
  other: '其他',
}
const MODULE_ORDER = ['core', 'organization', 'education', 'other']

const route = useRoute()
const loading = ref(false)
const error = ref('')
// 数据字典点「看接口」会带 ?table=xxx 过来，直接按表名反查
const keyword = ref(typeof route.query.table === 'string' ? route.query.table : '')
const chain = ref<Awaited<ReturnType<typeof fetchEndpointChains>> | null>(null)
const selectedKey = ref('')

const moduleLabel = (key: string) => MODULE_LABELS[key] || key

/** 一个方法可能挂多个 HTTP 动词（如 GET,POST），列表里只展示第一个，完整值在详情头部 */
const primaryMethod = (method: string) => method.split(',')[0] || 'ANY'

const endpointKey = (endpoint: EndpointChainEndpoint) =>
  `${endpoint.method} ${endpoint.path} ${endpoint.controllerMethod}`

/** 搜索覆盖实施人员可能知道的所有入口：按钮名、权限码、路径、服务、表名 */
const filteredEndpoints = computed<EndpointChainEndpoint[]>(() => {
  const endpoints = chain.value?.endpoints ?? []
  const word = keyword.value.trim().toLowerCase()
  if (!word) {
    return endpoints
  }
  return endpoints.filter((endpoint) => {
    const haystack = [
      endpoint.method,
      endpoint.path,
      endpoint.summary,
      endpoint.description,
      endpoint.permissionCode,
      endpoint.controllerClass,
      endpoint.controllerMethod,
      ...endpoint.services.flatMap((service) => [service.interfaceName, service.className]),
      ...endpoint.tables.flatMap((table) => [table.tableName, table.tableComment, table.mapperClass]),
      ...endpoint.actions.flatMap((action) => [action.label, action.description, action.actionKey]),
    ]
      .filter(Boolean)
      .join('\n')
      .toLowerCase()
    return haystack.includes(word)
  })
})

const groups = computed(() => {
  const byModule = new Map<string, EndpointChainEndpoint[]>()
  for (const endpoint of filteredEndpoints.value) {
    const list = byModule.get(endpoint.moduleKey) ?? []
    list.push(endpoint)
    byModule.set(endpoint.moduleKey, list)
  }
  const keys = [...byModule.keys()].sort((left, right) => {
    const leftIndex = MODULE_ORDER.indexOf(left)
    const rightIndex = MODULE_ORDER.indexOf(right)
    return (leftIndex < 0 ? 99 : leftIndex) - (rightIndex < 0 ? 99 : rightIndex)
  })
  return keys.map((key) => ({ key, label: moduleLabel(key), endpoints: byModule.get(key) ?? [] }))
})

const selected = computed<EndpointChainEndpoint | null>(() => {
  const endpoints = filteredEndpoints.value
  if (!endpoints.length) {
    return null
  }
  return endpoints.find((endpoint) => endpointKey(endpoint) === selectedKey.value) ?? endpoints[0]
})

const tableCount = computed(() => {
  const names = new Set<string>()
  for (const endpoint of chain.value?.endpoints ?? []) {
    for (const table of endpoint.tables) {
      names.add(table.tableName)
    }
  }
  return names.size
})

const actionCount = computed(() => {
  const keys = new Set<string>()
  for (const endpoint of chain.value?.endpoints ?? []) {
    for (const action of endpoint.actions) {
      keys.add(action.actionKey)
    }
  }
  return keys.size
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    chain.value = await fetchEndpointChains()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '接口链路加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<style scoped>
.endpoint-chain {
  gap: 12px;
}

.chain-search {
  width: 300px;
  flex-shrink: 0;
}

.chain-layout {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 12px;
  align-items: stretch;
  /* 视口填充：basis 必须写 0，写 auto 会按内容长高、左右两块就永远不滚 */
  flex: 1 1 0;
  min-height: 320px;
}

.chain-list {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: auto;
  padding: 10px;
  gap: 4px;
}

.chain-group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 6px 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.chain-group-head span {
  font-weight: 400;
}

.chain-endpoint-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  grid-template-areas:
    'method path'
    'summary summary'
    'perm perm';
  gap: 2px 6px;
  width: 100%;
  padding: 6px 8px;
  text-align: left;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
}

.chain-endpoint-item:hover {
  background: var(--el-fill-color-light);
}

.chain-endpoint-item.is-active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}

.chain-method {
  display: inline-block;
  min-width: 42px;
  padding: 0 5px;
  font-family: monospace;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
  border-radius: 4px;
  background: var(--el-fill-color);
  color: var(--el-text-color-regular);
  flex-shrink: 0;
}

.chain-method[data-method='GET'] {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}

.chain-method[data-method='POST'] {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.chain-method[data-method='PUT'] {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
}

.chain-method[data-method='DELETE'] {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.chain-path {
  grid-area: path;
  font-family: monospace;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chain-summary {
  grid-area: summary;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chain-perm {
  grid-area: perm;
  font-size: 11px;
  color: var(--el-color-warning);
  font-family: monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chain-empty,
.chain-empty-large {
  padding: 24px 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.chain-detail {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  overflow: hidden;
}

.chain-head {
  flex-shrink: 0;
  padding: 12px 14px;
}

.chain-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.chain-title {
  font-size: 16px;
}

.chain-endpoint-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 13px;
}

.chain-description {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
}

.chain-meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.chain-actions {
  flex-shrink: 0;
  padding: 10px 14px;
}

.chain-section-title {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.chain-action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chain-sensitive {
  margin-left: 4px;
  font-size: 10px;
}

.chain-flow {
  flex: 1 1 0;
  min-height: 0;
  overflow: auto;
  padding: 10px 14px;
}

.chain-step {
  position: relative;
  display: flex;
  gap: 10px;
  padding-bottom: 12px;
}

.chain-step:not(:last-child)::before {
  content: '';
  position: absolute;
  top: 22px;
  bottom: 0;
  left: 10px;
  border-left: 1px dashed var(--el-border-color);
}

.chain-step-no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 21px;
  height: 21px;
  flex-shrink: 0;
  font-size: 11px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 50%;
}

.chain-step-body {
  min-width: 0;
  flex: 1;
}

.chain-step-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  margin-bottom: 4px;
}

.chain-step-value {
  font-size: 12px;
  word-break: break-all;
}

.chain-service-list,
.chain-table-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.chain-service,
.chain-table {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
}

.chain-table-name {
  min-width: 180px;
}

.chain-table-comment {
  color: var(--el-text-color-secondary);
}

.chain-muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.chain-note {
  flex-shrink: 0;
  padding: 8px 12px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-lighter);
}
</style>
