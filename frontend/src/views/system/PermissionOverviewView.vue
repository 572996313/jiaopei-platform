<template>
  <section class="education-page">
    <article class="panel-card education-panel">
      <div class="education-table-head">
        <div class="panel-title">功能权限总览<span class="panel-count">共 {{ overview?.permissions.length || 0 }} 个权限码</span></div>
        <div class="table-actions">
          <span class="education-context-text">权限码与受控接口/持有角色/列表按钮/菜单节点的关系总览,数据实时聚合自代码与配置。</span>
          <el-button :loading="loading" @click="loadAll">刷新</el-button>
        </div>
      </div>

      <el-alert
        v-if="error"
        :title="error"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />

      <template v-else-if="overview">
        <el-alert
          v-if="overview.unregistered.length"
          :title="`发现 ${overview.unregistered.length} 个未登记权限码(后端没有任何接口使用):${overview.unregistered.map((item) => item.code).join(', ')}`"
          type="warning"
          show-icon
          :closable="false"
          class="education-alert"
        />

        <div class="dashboard-grid education-stat-grid">
          <article class="stat-card">
            <div class="stat-label">权限码</div>
            <div class="stat-value">{{ overview.permissions.length }}</div>
            <div class="stat-hint">来自后端 @RequiresPermission 注解扫描</div>
          </article>
          <article class="stat-card">
            <div class="stat-label">受控接口</div>
            <div class="stat-value">{{ totalEndpoints }}</div>
            <div class="stat-hint">被权限码保护的后端接口总数</div>
          </article>
          <article class="stat-card">
            <div class="stat-label">悬空码</div>
            <div class="stat-value" :class="{ 'stat-value-danger': danglingCount > 0 }">{{ danglingCount }}</div>
            <div class="stat-hint">无任何接口使用,配置了也不产生门禁</div>
          </article>
          <article class="stat-card">
            <div class="stat-label">未登记码</div>
            <div class="stat-value" :class="{ 'stat-value-danger': overview.unregistered.length > 0 }">
              {{ overview.unregistered.length }}
            </div>
            <div class="stat-hint">被引用但后端注解中不存在</div>
          </article>
        </div>

        <div class="permission-overview-layout">
          <div class="panel-card permission-overview-tree">
            <el-input v-model="keyword" placeholder="搜索权限码/名称/接口路径" clearable class="permission-search" />
            <el-tree
              :data="treeData"
              node-key="key"
              :props="{ label: 'label', children: 'children' }"
              default-expand-all
              highlight-current
              :expand-on-click-node="false"
              @node-click="handleNodeClick"
            />
          </div>

          <div class="permission-overview-detail">
            <template v-if="selectedCards.length">
              <article v-for="card in selectedCards" :key="card.code" class="panel-card permission-detail-card">
                <div class="permission-card-head">
                  <el-tag v-if="!card.registered" type="danger">未登记</el-tag>
                  <el-tag v-else-if="card.dangling" type="warning">悬空</el-tag>
                  <el-tag v-else-if="!card.menuNodeNames.length" type="info">未挂菜单节点</el-tag>
                  <strong class="permission-card-title">{{ card.label }}</strong>
                  <code class="permission-card-code">{{ card.code }}</code>
                </div>

                <div class="permission-card-section">
                  <span class="permission-section-label">持有角色</span>
                  <template v-if="card.roles.length">
                    <el-tag
                      v-for="role in card.roles"
                      :key="role.roleId"
                      size="small"
                      class="permission-tag"
                    >{{ role.roleName }}({{ role.roleCode }})</el-tag>
                  </template>
                  <span v-else class="education-context-text">暂无角色持有该权限码</span>
                </div>

                <div v-if="card.menuNodeNames.length" class="permission-card-section">
                  <span class="permission-section-label">菜单按钮节点</span>
                  <el-tag
                    v-for="name in card.menuNodeNames"
                    :key="name"
                    size="small"
                    type="info"
                    class="permission-tag"
                  >{{ name }}</el-tag>
                </div>

                <div class="permission-card-section">
                  <span class="permission-section-label">列表按钮</span>
                  <template v-if="card.configButtons.length">
                    <el-tag
                      v-for="btn in card.configButtons"
                      :key="`${btn.resourceCode}-${btn.action}`"
                      size="small"
                      type="success"
                      class="permission-tag"
                    >{{ btn.resourceName }}·{{ btn.label }}({{ btn.position === 'toolbar' ? '上方' : '行内' }})</el-tag>
                  </template>
                  <span v-else class="education-context-text">没有列表页按钮使用该权限码</span>
                </div>

                <div class="permission-card-section">
                  <span class="permission-section-label">受控接口</span>
                  <el-table v-if="card.endpoints.length" :data="card.endpoints" size="small" border>
                    <el-table-column label="方法" width="90">
                      <template #default="{ row }">
                        <el-tag size="small" :type="methodTagType(row.method)">{{ row.method }}</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="path" label="路径" min-width="240" />
                    <el-table-column label="说明" min-width="150">
                      <template #default="{ row }">{{ row.summary || '-' }}</template>
                    </el-table-column>
                  </el-table>
                  <span v-else class="education-context-text">
                    后端没有任何接口使用该权限码,请检查按钮配置或菜单节点上的权限码拼写。
                  </span>
                </div>
              </article>
            </template>
            <div v-else class="panel-card permission-detail-card education-empty">
              在左侧选择功能节点或权限码查看详情;也可在搜索框中按权限码、名称或接口路径过滤。
            </div>
          </div>
        </div>
      </template>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  fetchPermissionOverview,
  fetchSystemMenusTree,
  type PermissionEndpointInfo,
  type PermissionOverview,
  type PermissionRoleRef,
  type SystemMenuTreeNode,
} from '@/api/system'
import { permissionLabel } from '@/constants/permissionLabels'
import { menuTypeLabel, toMessage } from './shared'

interface OverviewTreeNode {
  key: string
  label: string
  permCode?: string
  children?: OverviewTreeNode[]
}

interface PermissionCard {
  code: string
  registered: boolean
  label: string
  endpoints: PermissionEndpointInfo[]
  roles: PermissionRoleRef[]
  menuNodeNames: string[]
  configButtons: Array<{ resourceCode: string; resourceName: string; action: string; label: string; position: string }>
  dangling: boolean
}

const overview = ref<PermissionOverview | null>(null)
const menuTree = ref<SystemMenuTreeNode[]>([])
const menuTreeFailed = ref(false)
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const selectedKeys = ref<string[]>([])

const totalEndpoints = computed(() =>
  (overview.value?.permissions || []).reduce((sum, item) => sum + item.endpoints.length, 0),
)
const danglingCount = computed(() => (overview.value?.permissions || []).filter((item) => item.dangling).length)

/** 权限码中文名:菜单按钮节点名优先(角色勾选后以菜单管理维护为准),前端常量表兜底 */
function displayName(code: string, menuNodeName?: string) {
  return menuNodeName || permissionLabel(code)
}

function buildCards(data: PermissionOverview): Map<string, PermissionCard> {
  const cards = new Map<string, PermissionCard>()
  for (const usage of data.permissions) {
    cards.set(usage.code, {
      code: usage.code,
      registered: true,
      label: displayName(usage.code, usage.menuNodes[0]?.menuName),
      endpoints: usage.endpoints,
      roles: usage.roles,
      menuNodeNames: usage.menuNodes.map((node) => node.menuName),
      configButtons: usage.configButtons,
      dangling: usage.dangling,
    })
  }
  for (const item of data.unregistered) {
    cards.set(item.code, {
      code: item.code,
      registered: false,
      label: item.menuNodes[0]?.menuName || item.code,
      endpoints: [],
      roles: item.roles,
      menuNodeNames: item.menuNodes.map((node) => node.menuName),
      configButtons: item.configButtons,
      dangling: false,
    })
  }
  return cards
}

const allCards = computed(() => (overview.value ? buildCards(overview.value) : new Map<string, PermissionCard>()))

function toTreeNodes(nodes: SystemMenuTreeNode[]): OverviewTreeNode[] {
  return nodes.map((node) => ({
    key: `menu:${node.id}`,
    label:
      node.menuType === 'button'
        ? `${node.menuName}(${node.permissionCode || '无权限码'})`
        : `${node.menuName}(${menuTypeLabel(node.menuType)})`,
    permCode: node.permissionCode || undefined,
    children: node.children?.length ? toTreeNodes(node.children) : undefined,
  }))
}

function permissionTreeNodes(items: Array<{ code: string; label: string }>): OverviewTreeNode[] {
  return items.map((item) => ({
    key: `perm:${item.code}`,
    label: `${item.label}(${item.code})`,
    permCode: item.code,
  }))
}

/** 左侧功能树:菜单树原样展示;已登记但未挂菜单节点的码、真未登记的码收进尾部虚拟分组 */
const treeData = computed<OverviewTreeNode[]>(() => {
  const groups: OverviewTreeNode[] = []
  if (menuTreeFailed.value) {
    groups.push({
      key: 'all-registered',
      label: `全部权限码(${overview.value?.permissions.length || 0})`,
      children: permissionTreeNodes(
        (overview.value?.permissions || []).map((usage) => ({
          code: usage.code,
          label: displayName(usage.code, usage.menuNodes[0]?.menuName),
        })),
      ),
    })
  } else {
    groups.push(...toTreeNodes(menuTree.value))
    // 有后端接口与注解、但 sys_menu 里没有挂任何节点的码(如教务/组织的 read/write),
    // 正常权限,只是暂无树归属,单独成组便于发现
    const unattached = (overview.value?.permissions || []).filter((usage) => usage.menuNodes.length === 0)
    if (unattached.length) {
      groups.push({
        key: 'registered-unattached-group',
        label: `已登记·未挂菜单权限码(${unattached.length})`,
        children: permissionTreeNodes(
          unattached.map((usage) => ({
            code: usage.code,
            label: displayName(usage.code, usage.menuNodes[0]?.menuName),
          })),
        ),
      })
    }
  }

  const orphans = (overview.value?.unregistered || []).map((item) => ({
    key: `perm:${item.code}`,
    label: `${displayName(item.code, item.menuNodes[0]?.menuName)}(${item.code})`,
    permCode: item.code,
  }))
  groups.push({
    key: 'unregistered-group',
    label: `未登记权限码(${orphans.length})`,
    children: orphans,
  })
  return groups
})

function collectCodes(node: OverviewTreeNode): string[] {
  if (node.permCode) {
    return [node.permCode]
  }
  return (node.children ?? []).flatMap(collectCodes)
}

function handleNodeClick(node: OverviewTreeNode) {
  keyword.value = ''
  selectedKeys.value = collectCodes(node)
}

const selectedCards = computed<PermissionCard[]>(() => {
  const cards = allCards.value
  const trimmed = keyword.value.trim().toLowerCase()
  if (trimmed) {
    return Array.from(cards.values()).filter(
      (card) =>
        card.code.toLowerCase().includes(trimmed) ||
        card.label.toLowerCase().includes(trimmed) ||
        card.endpoints.some((endpoint) => endpoint.path.toLowerCase().includes(trimmed)),
    )
  }
  return selectedKeys.value.map((code) => cards.get(code)).filter((card): card is PermissionCard => Boolean(card))
})

function methodTagType(method: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  if (method.startsWith('GET')) {
    return 'primary'
  }
  if (method.startsWith('POST')) {
    return 'success'
  }
  if (method.startsWith('PUT')) {
    return 'warning'
  }
  if (method.startsWith('DELETE')) {
    return 'danger'
  }
  return 'info'
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    overview.value = await fetchPermissionOverview()
  } catch (err) {
    error.value = toMessage(err, '权限总览加载失败')
    return
  } finally {
    loading.value = false
  }

  try {
    menuTree.value = await fetchSystemMenusTree()
    menuTreeFailed.value = false
  } catch {
    // 菜单树加载失败(如无菜单管理权限)时降级为权限码平铺树
    menuTree.value = []
    menuTreeFailed.value = true
  }
}

onMounted(() => {
  void loadAll()
})
</script>

<style scoped>
.permission-overview-layout {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 12px;
  align-items: start;
}

.permission-overview-tree {
  max-height: 640px;
  overflow: auto;
}

.permission-search {
  margin-bottom: 10px;
}

.permission-overview-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.permission-detail-card {
  margin: 0;
}

.permission-card-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.permission-card-title {
  font-size: 15px;
}

.permission-card-code {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.permission-card-section {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 10px;
}

.permission-section-label {
  flex: 0 0 84px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 24px;
}

.permission-tag {
  margin: 0 6px 6px 0;
}

.stat-value-danger {
  color: var(--el-color-danger);
}
</style>
