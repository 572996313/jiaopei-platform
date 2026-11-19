<template>
  <section class="education-page permission-overview">
    <div class="table-toolbar">
      <el-button :icon="Refresh" circle :loading="loading" @click="loadAll" />
      <el-button @click="openActionCatalog">按钮目录</el-button>
      <div class="education-context-text">
        权限码来自后端 @RequiresPermission 注解扫描,这里把它与受控接口、持有角色、菜单节点、列表按钮的关系一次看全。
        数据实时聚合,页面只读;要改授权去「角色管理-授权页面与按钮」或「菜单管理」。
      </div>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="education-alert" />

    <template v-else>
      <div class="dashboard-grid education-stat-grid">
        <article class="stat-card">
          <div class="stat-label">生效权限码</div>
          <div class="stat-value">{{ usageCards.length }}</div>
          <div class="stat-hint">后端注解扫描出的受控权限码总数</div>
        </article>
        <article class="stat-card">
          <div class="stat-label">受控接口</div>
          <div class="stat-value">{{ totalEndpoints }}</div>
          <div class="stat-hint">这些权限码保护住的 Controller 接口条数</div>
        </article>
        <article class="stat-card">
          <div class="stat-label">未挂菜单节点的码</div>
          <div class="stat-value" :class="{ 'stat-value-danger': unattachedCards.length > 0 }">
            {{ unattachedCards.length }}
          </div>
          <div class="stat-hint">有接口受控但菜单树里没有承载节点,只能靠角色预设权限授予</div>
        </article>
        <article class="stat-card">
          <div class="stat-label">未登记权限码</div>
          <div class="stat-value" :class="{ 'stat-value-danger': orphanCards.length > 0 }">
            {{ orphanCards.length }}
          </div>
          <div class="stat-hint">菜单节点/角色/按钮配置引用了,后端却没有接口使用,属于要清的死码</div>
        </article>
      </div>

      <div class="permission-overview-layout">
        <div class="panel-card permission-overview-tree">
          <el-input v-model="keyword" placeholder="搜索权限码/中文名/接口路径" clearable class="permission-search" />
          <div class="permission-filters">
            <button
              v-for="view in treeViews"
              :key="view.key"
              type="button"
              class="permission-filter"
              :class="{ 'is-active': viewKey === view.key, 'is-empty': view.count === 0 && view.key !== 'menu' }"
              @click="switchView(view)"
            >
              {{ view.label }}({{ view.count }})
            </button>
          </div>
          <el-tree
            v-if="treeData.length"
            ref="treeRef"
            class="permission-tree"
            :data="treeData"
            node-key="key"
            :props="{ label: 'name', children: 'children' }"
            :filter-node-method="matchNode"
            default-expand-all
            highlight-current
            :expand-on-click-node="false"
            @node-click="handleNodeClick"
          >
            <template #default="{ data }">
              <span class="tree-row" :class="{ 'is-plain': !data.code, 'is-button': data.nodeKind === 'button' }">
                <span class="tree-name">
                  <span v-if="data.code" class="kind-dot" :class="kindOf(data.code).key" />
                  {{ data.name }}
                </span>
                <code v-if="data.code" class="tree-code">{{ data.code }}</code>
                <span v-if="data.count !== null" class="tree-count">{{ data.count }}</span>
              </span>
            </template>
          </el-tree>
          <div v-else class="permission-tree-empty">没有匹配的权限码</div>
        </div>

        <div class="permission-overview-detail">
          <div v-if="!selected" class="panel-card permission-empty">
            在左侧选择一个权限码查看详情:它管哪些接口、哪些角色持有、码挂在页面还是按钮上。
          </div>

          <article v-else class="panel-card permission-detail">
            <header class="permission-head">
              <div class="permission-title-row">
                <strong class="permission-title">{{ selected.label }}</strong>
                <span class="permission-code">
                  {{ selected.code }}
                  <button type="button" class="permission-copy" @click="copyCode">复制</button>
                </span>
                <el-tag v-if="!selected.registered" type="danger" size="small">未登记</el-tag>
                <el-tag v-else-if="!selected.menuNodes.length" type="warning" size="small">未挂菜单节点</el-tag>
                <el-tag v-if="kindOf(selected.code).key !== 'other'" :type="kindOf(selected.code).tagType" size="small">
                  {{ kindOf(selected.code).text }}
                </el-tag>
              </div>
              <div class="permission-meta">
                <span>受控接口 <b>{{ selected.endpoints.length }}</b></span>
                <span>持有角色 <b>{{ selected.roles.length }}</b></span>
                <span>挂载节点 <b>{{ selected.menuNodes.length }}</b></span>
                <span v-if="selected.configButtons.length">列表按钮 <b>{{ selected.configButtons.length }}</b></span>
              </div>
            </header>

            <section class="permission-section">
              <h3 class="permission-section-head">
                谁能用
                <small>授权有两条路:角色「预设权限」勾选、角色「分配菜单」勾选节点</small>
              </h3>
              <div v-if="selected.roles.length" class="permission-roles">
                <div v-for="role in selected.roles" :key="role.roleId" class="permission-role">
                  <div class="permission-role-name">{{ role.roleName }}</div>
                  <code class="permission-role-code">{{ role.roleCode }}</code>
                  <div class="permission-role-src">
                    <el-tag v-if="role.fromPreset" size="small" type="primary" effect="plain">预设权限</el-tag>
                    <el-tag v-if="role.fromMenuNode" size="small" type="success" effect="plain">菜单节点</el-tag>
                  </div>
                </div>
              </div>
              <p v-else class="permission-muted">当前没有角色持有该码,接口对所有账号返回 403。</p>
              <p class="permission-note">
                {{
                  selected.menuNodes.length
                    ? '撤销授权要两边都撤干净:只取消勾选菜单、预设权限里还留着,或反之,权限照样生效。'
                    : '该码在菜单树里没有承载节点,只能从角色「预设权限」授予与回收。'
                }}
              </p>
            </section>

            <section class="permission-section">
              <h3 class="permission-section-head">
                挂在哪
                <small>页面节点=进得来且读得到,按钮节点=一个写动作</small>
              </h3>
              <div v-if="selected.menuNodes.length" class="permission-nodes">
                <div v-for="node in selected.menuNodes" :key="node.menuId" class="permission-node">
                  <span class="permission-node-crumb">
                    <template v-for="(step, index) in node.chain" :key="step">
                      <span v-if="index" class="permission-node-sep">/</span>{{ step }}
                    </template>
                  </span>
                  <el-tag :type="node.menuType === 'menu' ? 'success' : 'warning'" size="small" effect="plain">
                    {{ node.menuType === 'button' ? '按钮' : node.menuType === 'catalog' ? '目录' : '页面' }}
                  </el-tag>
                  <code v-if="node.path" class="permission-node-route">{{ node.path }}</code>
                </div>
                <p v-if="selected.pageCount > 1" class="permission-note">
                  该码覆盖 {{ selected.pageCount }} 个页面节点,勾选任意一个即授予,回收时要逐个取消。
                </p>
              </div>
              <p v-else class="permission-muted">无挂载节点。</p>
            </section>

            <section v-if="selected.configButtons.length" class="permission-section">
              <h3 class="permission-section-head">
                触发的列表按钮
                <small>列表配置中声明该权限码的按钮,按配置显隐</small>
              </h3>
              <div class="permission-buttons">
                <span v-for="btn in selected.configButtons" :key="`${btn.resourceCode}-${btn.action}`" class="permission-button">
                  {{ btn.resourceName }} · {{ btn.label }}
                  <code>{{ btn.action }}</code>
                  <small>{{ btn.position === 'toolbar' ? '工具栏' : '行内' }}</small>
                </span>
              </div>
            </section>

            <section class="permission-section">
              <h3 class="permission-section-head">
                管哪些接口
                <small>后端注解扫描结果</small>
                <span class="permission-section-count">{{ filteredEndpoints.length }} 条</span>
              </h3>
              <div v-if="selected.endpoints.length" class="permission-methods">
                <button
                  v-for="item in methodFilters"
                  :key="item.method"
                  type="button"
                  class="permission-filter"
                  :class="{ 'is-active': method === item.method }"
                  @click="method = item.method"
                >
                  {{ item.label }}({{ item.count }})
                </button>
              </div>
              <div v-if="visibleEndpoints.length" class="permission-apis">
                <div v-for="api in visibleEndpoints" :key="`${api.method}:${api.path}`" class="permission-api">
                  <el-tag class="permission-api-method" :type="methodTagType(api.method)" size="small">
                    {{ api.method }}
                  </el-tag>
                  <code class="permission-api-path">{{ api.path }}</code>
                  <span class="permission-api-summary">{{ api.summary || '接口说明待补' }}</span>
                </div>
                <button
                  v-if="hiddenEndpointCount > 0 || expanded"
                  type="button"
                  class="permission-more"
                  @click="expanded = !expanded"
                >
                  {{ expanded ? '收起' : `展开其余 ${hiddenEndpointCount} 条` }}
                </button>
              </div>
              <p v-else class="permission-muted">
                {{
                  selected.registered
                    ? '该筛选方式下没有接口。'
                    : '后端没有任何接口使用该权限码,请检查菜单节点或按钮配置里的权限码拼写。'
                }}
              </p>
            </section>
          </article>
        </div>
      </div>
    </template>

    <!-- 按钮授权目录：代码侧登记 + sys_menu 节点现状，正常缺口为 0 -->
    <el-drawer v-model="catalogVisible" title="按钮授权目录" size="640px">
      <el-alert
        v-if="catalogError"
        :title="catalogError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />

      <el-alert
        v-if="actionCatalog && actionCatalog.gaps.length > 0"
        :title="`${actionCatalog.gaps.length} 处配置缺口`"
        type="warning"
        show-icon
        :closable="false"
        class="education-alert"
      >
        <div v-for="(gap, index) in actionCatalog.gaps" :key="index" class="permission-catalog-gap">
          {{ gap.message }}
          <small>{{ gap.pageMenuCode }} / {{ gap.grantMenuCode || '-' }}</small>
        </div>
      </el-alert>

      <div v-loading="catalogLoading" class="permission-catalog">
        <article
          v-for="page in actionCatalog?.pages || []"
          :key="page.menuId"
          class="permission-catalog-page"
        >
          <header class="permission-catalog-head">
            <strong>{{ page.menuName }}</strong>
            <code>{{ page.menuCode }}</code>
            <span class="permission-catalog-count">{{ page.actions.length }} 项</span>
          </header>
          <p v-if="page.actions.length === 0" class="permission-muted">
            只读页：勾选页面即可进入查看，没有独立操作。
          </p>
          <div v-for="action in page.actions" :key="action.actionKey" class="permission-catalog-action">
            <span class="permission-catalog-label">{{ action.label }}</span>
            <el-tag v-if="action.sensitive" size="small" type="danger" effect="plain">敏感</el-tag>
            <el-tag v-if="action.mode === 'linked'" size="small" effect="plain">同码联动</el-tag>
            <el-tag v-if="action.mode === 'uiOnly'" size="small" type="info" effect="plain"
              >跟随页面</el-tag
            >
            <code class="permission-catalog-code">{{ action.permissionCode || '无需权限码' }}</code>
          </div>
        </article>
        <div v-if="actionCatalog && actionCatalog.pages.length === 0" class="permission-muted">
          还没有登记任何页面动作。
        </div>
      </div>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  fetchActionCatalog,
  fetchPermissionOverview,
  fetchSystemMenusTree,
  type ActionCatalog,
  type PermissionEndpointInfo,
  type PermissionOverview,
  type PermissionRoleRef,
  type SystemMenuTreeNode,
} from '@cornerstone/core/api/system'
import { permissionLabel } from '@cornerstone/core/constants/permissionLabels'
import { toMessage } from './shared'

/** 左侧树的一行:菜单节点原样展示,筛选视图下退化为权限码平铺 */
interface PermissionTreeNode {
  key: string
  name: string
  code?: string
  count: number | null
  nodeKind?: 'catalog' | 'menu' | 'button'
  children?: PermissionTreeNode[]
}

/** el-tree 节点点击回调里的节点对象(只用展开/收起三件) */
interface TreeEventNode {
  expanded: boolean
  expand: () => void
  collapse: () => void
}

/** 详情卡片:已登记码与未登记码统一成同一形态渲染 */interface PermissionCard {
  code: string
  registered: boolean
  label: string
  endpoints: PermissionEndpointInfo[]
  roles: PermissionRoleRef[]
  menuNodes: Array<{ menuId: string; menuName: string; menuType: string; path: string; chain: string[] }>
  configButtons: PermissionOverview['permissions'][number]['configButtons']
  pageCount: number
}

const ENDPOINT_PREVIEW = 8

const overview = ref<PermissionOverview | null>(null)
const menuTree = ref<SystemMenuTreeNode[]>([])
const menuTreeFailed = ref(false)
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const viewKey = ref('menu')
const selectedCode = ref('')
const method = ref('ALL')
const expanded = ref(false)
const treeRef = ref<{ filter: (value: string) => void } | null>(null)

/** 按钮授权目录（代码侧登记 + 节点现状），按需加载 */
const catalogVisible = ref(false)
const catalogLoading = ref(false)
const catalogError = ref('')
const actionCatalog = ref<ActionCatalog | null>(null)

/** 权限码 → 菜单节点信息(取类型与路由,并还原面包屑);菜单树没加载成功时为空 */
const menuIndex = computed(() => {
  const index = new Map<string, { menuType: string; path: string; chain: string[] }>()
  const walk = (nodes: SystemMenuTreeNode[], chain: string[]) => {
    for (const node of nodes) {
      const next = [...chain, node.menuName]
      index.set(node.menuCode, { menuType: node.menuType || '', path: node.path || '', chain: next })
      if (node.children?.length) walk(node.children, next)
    }
  }
  walk(menuTree.value, [])
  return index
})

const cards = computed<PermissionCard[]>(() => {
  const data = overview.value
  if (!data) return []
  const list: PermissionCard[] = data.permissions.map((usage) => toCard(usage.code, true, usage, usage.endpoints))
  for (const orphan of data.unregistered) {
    list.push(toCard(orphan.code, false, orphan, []))
  }
  return list
})

function toCard(
  code: string,
  registered: boolean,
  usage: PermissionOverview['permissions'][number] | PermissionOverview['unregistered'][number],
  endpoints: PermissionEndpointInfo[]
): PermissionCard {
  const nodes = usage.menuNodes.map((node) => {
    const meta = menuIndex.value.get(node.menuCode)
    return {
      menuId: node.menuId,
      menuName: node.menuName,
      menuType: meta?.menuType || 'menu',
      path: meta?.path || '',
      chain: meta?.chain || [node.menuName],
    }
  })
  return {
    code,
    registered,
    label: displayName(code, nodes[0]?.menuName),
    endpoints,
    roles: usage.roles,
    menuNodes: nodes,
    configButtons: usage.configButtons,
    pageCount: nodes.filter((node) => node.menuType === 'menu').length,
  }
}

const usageCards = computed(() => cards.value.filter((card) => card.registered))
const unattachedCards = computed(() => usageCards.value.filter((card) => !card.menuNodes.length))
const orphanCards = computed(() => cards.value.filter((card) => !card.registered))
const totalEndpoints = computed(() =>
  usageCards.value.reduce((sum, card) => sum + card.endpoints.length, 0)
)

const selected = computed(() => cards.value.find((card) => card.code === selectedCode.value) || null)

/** 权限码中文名:登记表优先(一个码可挂多个页面,节点名只说明挂在哪),其次节点名,最后权限码本身 */
function displayName(code: string, menuNodeName?: string) {
  const registered = permissionLabel(code)
  return registered === code ? menuNodeName || code : registered
}

/** 权限性质按码尾段判:底座不认业务名词,读/写/管理以外的统一不出标签 */
function kindOf(code: string) {
  const tail = code.split(':').pop() || ''
  if (tail === 'read') {
    return { key: 'read', text: '读取', tagType: 'success' as const }
  }
  if (tail === 'write') {
    return { key: 'write', text: '写入', tagType: 'warning' as const }
  }
  if (tail === 'manage') {
    return { key: 'manage', text: '读写共用', tagType: 'danger' as const }
  }
  return { key: 'other', text: '', tagType: 'info' as const }
}

const treeViews = computed(() => [
  { key: 'menu', label: '菜单树', count: usageCards.value.length },
  { key: 'unattached', label: '未挂节点', count: unattachedCards.value.length },
  { key: 'orphan', label: '未登记', count: orphanCards.value.length },
])

function switchView(view: { key: string; count: number }) {
  if (view.key !== 'menu' && view.count === 0) return
  viewKey.value = view.key
}

function codeNodes(list: PermissionCard[]): PermissionTreeNode[] {
  return list.map((card) => ({
    key: `perm:${card.code}`,
    name: card.label,
    code: card.code,
    count: card.endpoints.length,
  }))
}

function toTreeNodes(nodes: SystemMenuTreeNode[]): PermissionTreeNode[] {
  return nodes.map((node) => ({
    key: `menu:${node.id}`,
    name: node.menuName,
    code: node.permissionCode || undefined,
    count: node.permissionCode ? countByCode.value.get(node.permissionCode) ?? 0 : null,
    nodeKind: node.menuType as 'catalog' | 'menu' | 'button',
    children: node.children?.length ? toTreeNodes(node.children) : undefined,
  }))
}

const countByCode = computed(
  () => new Map(usageCards.value.map((card) => [card.code, card.endpoints.length] as const))
)

const treeData = computed<PermissionTreeNode[]>(() => {
  if (viewKey.value === 'unattached') return codeNodes(unattachedCards.value)
  if (viewKey.value === 'orphan') return codeNodes(orphanCards.value)
  if (menuTreeFailed.value) return codeNodes(usageCards.value)
  return [...toTreeNodes(menuTree.value), ...tailGroups.value]
})

/** 树尾两个虚拟分组:正常都应为空,非空即配置缺口 */
const tailGroups = computed<PermissionTreeNode[]>(() => {
  const groups: PermissionTreeNode[] = []
  if (unattachedCards.value.length) {
    groups.push({
      key: 'group-unattached',
      name: '已登记·未挂菜单节点',
      count: null,
      nodeKind: 'catalog',
      children: codeNodes(unattachedCards.value),
    })
  }
  groups.push({
    key: 'group-orphan',
    name: `未登记权限码(${orphanCards.value.length})`,
    count: null,
    nodeKind: 'catalog',
    children: codeNodes(orphanCards.value),
  })
  return groups
})

watch(keyword, (value) => treeRef.value?.filter(value))
watch(cards, () => {
  if (!selectedCode.value && usageCards.value.length) {
    selectedCode.value = usageCards.value[0].code
  }
})

/** 搜索命中:权限码、中文名、接口路径任一匹配;菜单节点靠子节点命中带出 */
function matchNode(value: string, data: PermissionTreeNode): boolean {
  const text = value.trim().toLowerCase()
  if (!text) return true
  if (data.name.toLowerCase().includes(text)) return true
  const code = data.code
  if (code) {
    if (code.toLowerCase().includes(text) || displayName(code).toLowerCase().includes(text)) return true
    const card = cards.value.find((item) => item.code === code)
    if (card?.endpoints.some((api) => api.path.toLowerCase().includes(text))) return true
  }
  return (data.children || []).some((child) => matchNode(value, child))
}

/** 带码的行是选择,目录/无码行只做展开收起 */
function handleNodeClick(data: PermissionTreeNode, node: TreeEventNode) {
  if (data.code) {
    selectCode(data.code)
    return
  }
  if (!data.children?.length) return
  if (node.expanded) node.collapse()
  else node.expand()
}

function selectCode(code: string) {
  selectedCode.value = code
  method.value = 'ALL'
  expanded.value = false
}

const filteredEndpoints = computed(() => {
  const list = selected.value?.endpoints || []
  return method.value === 'ALL' ? list : list.filter((api) => api.method === method.value)
})

const visibleEndpoints = computed(() =>
  expanded.value ? filteredEndpoints.value : filteredEndpoints.value.slice(0, ENDPOINT_PREVIEW)
)
const hiddenEndpointCount = computed(() => filteredEndpoints.value.length - ENDPOINT_PREVIEW)

/** 只有一种方法时不出筛选条:筛选不改变任何东西，只会占行 */
const methodFilters = computed(() => {
  const list = selected.value?.endpoints || []
  const methods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'ANY'].filter((name) =>
    list.some((api) => api.method === name)
  )
  if (methods.length < 2) return []
  return [
    { method: 'ALL', label: '全部', count: list.length },
    ...methods.map((name) => ({
      method: name,
      label: name,
      count: list.filter((api) => api.method === name).length,
    })),
  ]
})

function methodTagType(name: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  if (name.startsWith('GET')) return 'primary'
  if (name.startsWith('POST')) return 'success'
  if (name.startsWith('PUT')) return 'warning'
  if (name.startsWith('DELETE')) return 'danger'
  return 'info'
}

async function copyCode() {
  const code = selected.value?.code
  if (!code) return
  try {
    await navigator.clipboard.writeText(code)
    ElMessage.success(`已复制权限码 ${code}`)
  } catch {
    // 非安全上下文或浏览器不允许脚本写剪贴板:不静默失败,让用户能手动复制
    ElMessage.warning(`复制失败,请手动选中复制:${code}`)
  }
}

/** 按钮目录是独立接口（system:role:manage），按需加载，403 时只在抽屉里提示，不打断总览 */
async function openActionCatalog() {
  catalogVisible.value = true
  catalogLoading.value = true
  catalogError.value = ''
  try {
    actionCatalog.value = await fetchActionCatalog()
  } catch (error) {
    catalogError.value = toMessage(error, '按钮目录加载失败')
  } finally {
    catalogLoading.value = false
  }
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
.permission-overview {
  gap: 12px;
}

.permission-overview .education-stat-grid {
  gap: 12px;
}

.permission-overview .stat-card {
  padding: 8px 12px;
}

.permission-overview .stat-value {
  font-size: 20px;
}

.stat-value-danger {
  color: var(--el-color-danger);
}

.permission-overview-layout {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 12px;
  align-items: stretch;
  /* 视口填充:basis 必须写 0,写 auto 会按内容长高、左树右详情就永远不滚 */
  flex: 1 1 0;
  min-height: 320px;
}

.permission-overview-tree {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  padding: 14px;
}

.permission-search {
  margin-bottom: 10px;
  flex-shrink: 0;
}

.permission-filters,
.permission-methods {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.permission-filter {
  padding: 1px 9px;
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color);
  border-radius: 11px;
  cursor: pointer;
}

.permission-filter:hover {
  color: var(--el-color-primary);
}

.permission-filter.is-active {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}

.permission-filter.is-empty {
  color: var(--el-text-color-disabled);
  cursor: not-allowed;
}

.permission-tree {
  flex: 1 1 0;
  min-height: 0;
  overflow: auto;
  background: transparent;
}

.permission-tree-empty {
  padding: 20px 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.tree-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
  padding-right: 4px;
  font-size: 13px;
}

.tree-row.is-plain {
  color: var(--el-text-color-secondary);
}

.tree-row.is-button {
  color: var(--el-text-color-secondary);
}

.tree-name {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kind-dot {
  flex-shrink: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-text-color-disabled);
}

.kind-dot.read {
  background: var(--el-color-success);
}

.kind-dot.write {
  background: var(--el-color-warning);
}

.kind-dot.manage {
  background: var(--el-color-danger);
}

.tree-code {
  flex-shrink: 0;
  max-width: 132px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 0 4px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-radius: 3px;
}

.tree-count {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.permission-overview-detail {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow-y: auto;
}

.permission-empty {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 2;
}

.permission-detail {
  /* 不许被压回容器高度:否则内容超出部分会被下面的 overflow:hidden 裁掉,
     而外层滚动条又因为"内容没超出"而不出现,多出来的接口行就彻底看不见了 */
  flex: 0 0 auto;
  padding: 0;
  overflow: hidden;
}

.permission-head {
  padding: 16px 20px 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.permission-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.permission-title {
  font-size: 17px;
}

.permission-code {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.permission-copy {
  padding: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  background: none;
  border: none;
  cursor: pointer;
}

.permission-copy:hover {
  color: var(--el-color-primary);
}

.permission-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.permission-meta b {
  color: var(--el-text-color-primary);
}

.permission-section {
  padding: 12px 20px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.permission-section:last-child {
  border-bottom: none;
}

.permission-section-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.permission-section-head small {
  font-weight: 400;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.permission-section-count {
  margin-left: auto;
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.permission-roles {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.permission-role {
  min-width: 190px;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.permission-role-name {
  font-size: 13px;
  font-weight: 600;
}

.permission-role-code {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.permission-role-src {
  display: flex;
  gap: 6px;
  margin-top: 6px;
}

.permission-nodes .permission-node + .permission-node {
  margin-top: 8px;
}

.permission-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  font-size: 13px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.permission-node-crumb {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.permission-node-sep {
  margin: 0 4px;
  color: var(--el-text-color-placeholder);
}

.permission-node-route {
  margin-left: auto;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.permission-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.permission-button {
  padding: 6px 10px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
}

.permission-button code {
  color: var(--el-text-color-secondary);
}

.permission-button small {
  margin-left: 4px;
  color: var(--el-text-color-placeholder);
}

.permission-apis {
  font-size: 13px;
}

.permission-api {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.permission-api:last-of-type {
  border-bottom: none;
}

.permission-api-method {
  flex: 0 0 56px;
  justify-content: center;
}

.permission-api-path {
  flex: 0 1 auto;
  min-width: 0;
  font-size: 12px;
  word-break: break-all;
}

.permission-api-summary {
  margin-left: auto;
  max-width: 38%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.permission-more {
  margin-top: 10px;
  padding: 0;
  font-size: 12px;
  color: var(--el-color-primary);
  background: none;
  border: none;
  cursor: pointer;
}

.permission-more:hover {
  text-decoration: underline;
}

.permission-note {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}

.permission-muted {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.permission-catalog {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.permission-catalog-page {
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.permission-catalog-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 6px;
}

.permission-catalog-head code {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.permission-catalog-count {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.permission-catalog-action {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 13px;
}

.permission-catalog-label {
  flex: 1 1 auto;
}

.permission-catalog-code {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.permission-catalog-gap {
  margin-top: 4px;
  font-size: 12px;
}

.permission-catalog-gap small {
  margin-left: 6px;
  color: var(--el-text-color-placeholder);
}
</style>
