<template>
  <section class="education-page">
    <article class="panel-card education-panel">
      <article v-if="sectionError" class="education-empty">
        {{ sectionError }}
      </article>
      <template v-else>
        <div class="table-toolbar">
          <el-button
            type="primary"
            :disabled="!canWrite || blocked || !diffRows.length"
            :loading="saving"
            @click="save"
          >
            {{ saveLabel }}
          </el-button>
          <el-button :disabled="!changed" @click="discard">放弃修改</el-button>
          <el-button :disabled="!canWrite" @click="openCatalogDialog">新建分组</el-button>
          <el-button :icon="Refresh" circle :loading="loading" @click="loadNavigation" />
          <NoteHint label="这一页只改五列" :width="620">
            <p>
              能改的是 <b>分组名称 / 分组图标 / 顺序 / 页面属于哪个分组 / 这一页在侧边栏是否显示</b>。
              路径、权限码、节点类型仍然只读——改那些会把入口指到一个不存在的页面，或把人挡在 403，
              那属「菜单管理」。<b>页面里的标题、按钮和字段文案不在这一页改</b>，那是各页面自己的内容，
              改了不会生效；这一页只管"侧边栏怎么排、叫什么"。
            </p>
            <p>
              <b>图标只对分组有效</b>：侧边栏一个分组一枚图标，组里的页面只显示文字，所以页面行没有图标下拉。
            </p>
            <p>
              <b>隐藏一个页面</b>只是从侧边栏与 <b>Ctrl/⌘+K</b> 搜索里摘掉入口，权限不变，知道地址仍能直接打开。
              <b>分组不提供隐藏</b>：底座那边隐藏目录节点会把它下面的页面全部提升成顶层散页（图标一下变多），
              所以要藏一整组就把组里的页面逐个隐藏；组里没有可见页面时这一组自动不显示。
            </p>
            <p>
              「系统设置」那一段在本页<b>只读</b>：它的用户/角色/机构三条链路目前不分机构范围，
              挪进业务分组等于绕开「不给非平台角色开系统设置页」这条硬约束。
            </p>
            <p>
              <b>新建分组会立即写库</b>，并同批授权给所有在职角色——漏授权的表现是那一组的入口整块消失且不报错。
              本页不提供删除分组（空分组本来就不显示），要删请到「菜单管理」。
            </p>
            <p>
              保存后你这边侧边栏立即刷新，其他人下次进入系统时生效（导航树是每次进系统现拉的）。
              这套结构<b>全平台一份、不分机构</b>：A 机构改了顺序，B 机构看到的也是改后的。
            </p>
          </NoteHint>
        </div>

        <TableFillBox v-slot="{ height }">
          <div v-loading="loading" class="nav-grid" :style="{ height: `${height}px` }">
            <section class="nav-pane">
              <div class="nav-pane-head">
                <b>目录与页面</b>
                <span class="nav-pane-sub">↑↓ 调顺序 · 「挪到」改所属分组 · 拖拽只是快捷入口</span>
              </div>
              <div class="nav-pane-body">
                <template v-for="node in topLevelNodes" :key="node.id">
                  <!-- 底座段：整棵子树只读，只给一行说明，不给输入框 -->
                  <div v-if="node.locked" class="nav-locked">
                    <span class="nav-lock-tag">底座段 · 只读</span>
                    <b>{{ node.menuName }}</b>
                    <span class="nav-count">
                      {{ pagesUnder(node.id).length }} 页 · 排序 {{ node.sortNo }}
                    </span>
                  </div>

                  <!-- 顶层散页：它自己就是一个图标 -->
                  <div
                    v-else-if="node.menuType !== 'catalog'"
                    class="nav-row nav-row-loose"
                    :class="{ 'is-dirty': isDirty(node) }"
                    draggable="true"
                    @dragstart="draggingId = node.id"
                    @dragend="clearDrop"
                  >
                    <el-input v-model="node.menuName" class="nav-name" maxlength="128" />
                    <el-select
                      :model-value="node.parentId ?? TOP"
                      class="nav-parent"
                      @change="reparent(node, $event as string)"
                    >
                      <el-option :value="TOP" label="（顶层散页·不建议）" />
                      <el-option
                        v-for="catalog in unlockedCatalogs"
                        :key="catalog.id"
                        :value="catalog.id"
                        :label="catalog.menuName"
                      />
                    </el-select>
                    <span class="nav-readonly">{{ node.path || '—' }}</span>
                    <span class="nav-ops">
                      <el-button :icon="ArrowUp" size="small" text @click="move(node, -1)" />
                      <el-button :icon="ArrowDown" size="small" text @click="move(node, 1)" />
                      <el-button size="small" text @click="toggleVisible(node)">
                        {{ node.visible ? '显示' : '隐藏' }}
                      </el-button>
                    </span>
                  </div>

                  <div
                    v-else
                    class="nav-catalog"
                    :class="{ 'is-dirty': isDirty(node), 'is-drop': dropTargetId === node.id }"
                    @dragover.prevent
                    @dragenter="dropTargetId = node.id"
                    @drop="dropInto(node.id)"
                  >
                    <div class="nav-catalog-head">
                      <el-icon class="nav-row-icon">
                        <component :is="resolveMenuIcon(node.icon) || MenuIcon" />
                      </el-icon>
                      <el-select-v2
                        v-model="node.icon"
                        :options="iconOptions"
                        class="nav-icon"
                        placeholder="图标"
                        filterable
                        clearable
                      />
                      <el-input v-model="node.menuName" class="nav-name" maxlength="128" />
                      <span class="nav-count">
                        {{ childrenOf(node.id).length }} 页 · 排序 {{ node.sortNo }} · 已授
                        {{ node.grantedRoleCount }} 个角色
                      </span>
                      <span class="nav-ops">
                        <el-button :icon="ArrowUp" size="small" text @click="move(node, -1)" />
                        <el-button :icon="ArrowDown" size="small" text @click="move(node, 1)" />
                      </span>
                    </div>
                    <div
                      v-for="page in childrenOf(node.id)"
                      :key="page.id"
                      class="nav-row"
                      :class="{ 'is-dirty': isDirty(page) }"
                      draggable="true"
                      @dragstart="draggingId = page.id"
                      @dragend="clearDrop"
                    >
                      <el-input v-model="page.menuName" class="nav-name" maxlength="128" />
                      <el-select
                        :model-value="page.parentId ?? TOP"
                        class="nav-parent"
                        @change="reparent(page, $event as string)"
                      >
                        <el-option :value="TOP" label="（顶层散页·不建议）" />
                        <el-option
                          v-for="catalog in unlockedCatalogs"
                          :key="catalog.id"
                          :value="catalog.id"
                          :label="catalog.menuName"
                        />
                      </el-select>
                      <span class="nav-readonly" :title="page.permissionCode || '仅登录'">
                        {{ page.path || '—' }}
                      </span>
                      <span class="nav-ops">
                        <el-button :icon="ArrowUp" size="small" text @click="move(page, -1)" />
                        <el-button :icon="ArrowDown" size="small" text @click="move(page, 1)" />
                        <el-button size="small" text @click="toggleVisible(page)">
                          {{ page.visible ? '显示' : '隐藏' }}
                        </el-button>
                      </span>
                    </div>
                    <div v-if="!childrenOf(node.id).length" class="nav-row nav-row-empty">
                      空分组：这一组不会出现在侧边栏
                    </div>
                  </div>
                </template>
              </div>
            </section>

            <div class="nav-side">
              <section class="nav-pane nav-pane-preview">
                <div class="nav-pane-head">
                  <b>侧边栏预览</b>
                  <span class="nav-pane-sub">{{ previewGroups.length }} 个图标 / 两层</span>
                </div>
                <div class="nav-pane-body">
                  <div class="nav-rail">
                    <div v-for="group in previewGroups" :key="group.key" class="nav-rail-dot">
                      <el-icon>
                        <component :is="group.component || resolveMenuIcon(group.icon) || MenuIcon" />
                      </el-icon>
                      <span>{{ group.name.slice(0, 4) }}</span>
                    </div>
                  </div>
                  <ul class="nav-full">
                    <li v-for="group in previewGroups" :key="`full-${group.key}`">
                      <b>{{ group.name }}</b>
                      <ul v-if="group.pages.length">
                        <li v-for="page in group.pages" :key="page.id">{{ page.menuName }}</li>
                      </ul>
                    </li>
                  </ul>
                </div>
              </section>

              <section class="nav-pane">
                <div class="nav-pane-head">
                  <b>校验</b>
                  <span class="nav-pane-sub">有拦截项时「保存」不可点</span>
                </div>
                <div class="nav-pane-body">
                  <ul class="nav-issues">
                    <li v-for="(issue, index) in issues" :key="index" :class="issue.level">
                      {{ issue.text }}
                    </li>
                  </ul>
                </div>
              </section>

              <section class="nav-pane">
                <div class="nav-pane-head">
                  <b>将写入的差异</b>
                  <span class="nav-pane-sub">{{ diffRows.length }} 处</span>
                </div>
                <div class="nav-pane-body">
                  <table v-if="diffRows.length" class="nav-diff">
                    <thead>
                      <tr>
                        <th>节点</th>
                        <th>列</th>
                        <th>原值</th>
                        <th>新值</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="(row, index) in diffRows" :key="index">
                        <td>{{ row.node }}</td>
                        <td>{{ row.column }}</td>
                        <td class="old">{{ row.oldText }}</td>
                        <td class="new">{{ row.newText }}</td>
                      </tr>
                    </tbody>
                  </table>
                  <p v-else class="nav-diff-empty">没有改动</p>
                </div>
              </section>
            </div>
          </div>
        </TableFillBox>
      </template>
    </article>

    <el-dialog v-model="catalogDialog" title="新建分组" width="480px">
      <el-alert
        v-if="catalogError"
        :title="catalogError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />
      <el-form
        ref="catalogFormRef"
        :model="catalogForm"
        :rules="catalogRules"
        label-position="top"
        class="education-form"
        scroll-to-error
        @submit.prevent
      >
        <el-form-item label="分组名称" prop="menuName">
          <el-input v-model="catalogForm.menuName" maxlength="128" placeholder="如「招生与续费」" />
        </el-form-item>
        <el-form-item label="分组图标">
          <el-select-v2
            v-model="catalogForm.icon"
            :options="iconOptions"
            placeholder="选一个（不选会用一个通用图标）"
            filterable
            clearable
          />
        </el-form-item>
      </el-form>
      <p class="nav-dialog-hint">
        保存即写库，并自动授给所有在职角色；建完是空分组，把页面从左边拖进来或改「挪到」即可归组。
      </p>
      <template #footer>
        <el-button @click="catalogDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCatalog">创建分组</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import type { Component } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { ArrowDown, ArrowUp, Menu as MenuIcon, Refresh } from '@element-plus/icons-vue'
import NoteHint from '@cornerstone/core/components/NoteHint.vue'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import {
  createNavigationCatalog,
  fetchNavigation,
  saveNavigation,
} from '@cornerstone/core/api/system'
import type { NavigationNode } from '@cornerstone/core/api/system'
import { MENU_ICON_OPTIONS, resolveMenuIcon } from '@cornerstone/core/constants/menuIcons'
import { contributedNavItems } from '@cornerstone/core/extension/module-extensions'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { toMessage, useSystemPermissions } from './shared'

/** 「挪到」下拉里代表"不挂分组"的哨兵值：el-option 不能用 null 当 value */
const TOP = '__top__'

/** 只有这五列会被写回 sys_menu，其余列（路径/权限码/类型/编码）在本页只读 */
const WRITABLE_COLUMNS = [
  ['menuName', '名称'],
  ['icon', '图标'],
  ['sortNo', '顺序'],
  ['parentId', '所属分组'],
  ['visible', '侧边栏可见'],
] as const

interface DraftNode extends NavigationNode {
  sortNo: number
  visible: boolean
}

const auth = useAuthStore()
const permissions = useSystemPermissions()
const canWrite = computed(() => permissions.value.canManageNavigation)

const iconOptions = MENU_ICON_OPTIONS

const loading = ref(false)
const saving = ref(false)
const sectionError = ref('')
const nodes = ref<DraftNode[]>([])
const origin = ref(new Map<string, DraftNode>())
const draggingId = ref('')
const dropTargetId = ref('')

const catalogDialog = ref(false)
const creating = ref(false)
const catalogError = ref('')
const catalogFormRef = ref<FormInstance | null>(null)
const catalogForm = reactive({ menuName: '', icon: '' })

const catalogRules: FormRules = {
  menuName: [{ required: true, whitespace: true, message: '请填写分组名称', trigger: 'blur' }],
}

function toDraft(node: NavigationNode): DraftNode {
  return { ...node, sortNo: node.sortNo ?? 0, visible: node.visible !== false }
}

function bySort(a: DraftNode, b: DraftNode) {
  return a.sortNo - b.sortNo || a.menuCode.localeCompare(b.menuCode)
}

function find(id: string | null) {
  return id === null ? undefined : nodes.value.find((node) => node.id === id)
}

const topLevelNodes = computed(() => nodes.value.filter((node) => !node.parentId).sort(bySort))
const unlockedCatalogs = computed(() =>
  nodes.value.filter((node) => node.menuType === 'catalog' && !node.locked)
)

const childrenOf = (parentId: string) =>
  nodes.value
    .filter((node) => node.parentId === parentId && node.menuType === 'menu')
    .sort(bySort)

/** 某个分组下的全部页面（含子分组里的，底座「系统设置」自己就是三层） */
function pagesUnder(parentId: string): DraftNode[] {
  const direct = childrenOf(parentId)
  const nested = nodes.value.filter(
    (node) => node.parentId === parentId && node.menuType === 'catalog'
  )
  return [...direct, ...nested.flatMap((node) => pagesUnder(node.id))]
}

async function loadNavigation() {
  loading.value = true
  sectionError.value = ''
  try {
    applyServerNodes((await fetchNavigation()).map(toDraft))
  } catch (error) {
    sectionError.value = toMessage(error, '导航结构加载失败')
  } finally {
    loading.value = false
  }
}

function applyServerNodes(list: DraftNode[]) {
  nodes.value = list
  origin.value = new Map(list.map((node) => [node.id, { ...node }]))
}

/** 只把新建的节点并进草稿，不动已改的行：否则"改了名字再建分组"会把改动冲掉 */
function mergeNewNodes(list: DraftNode[]) {
  const known = new Set(nodes.value.map((node) => node.id))
  for (const node of list) {
    if (known.has(node.id)) {
      continue
    }
    nodes.value.push(node)
    origin.value.set(node.id, { ...node })
  }
}

function normKey(value: unknown) {
  return value === null || value === undefined ? '' : String(value)
}

function isDirty(node: DraftNode) {
  const before = origin.value.get(node.id)
  return !!before && WRITABLE_COLUMNS.some(([key]) => normKey(before[key]) !== normKey(node[key]))
}

const changed = computed(() => nodes.value.some(isDirty))

const dirtyNodes = computed(() => nodes.value.filter(isDirty))

function labelOf(id: string | null) {
  if (!id) {
    return '（顶层）'
  }
  return find(id)?.menuName || origin.value.get(id)?.menuName || id
}

const diffRows = computed(() => {
  const rows: Array<{ node: string; column: string; oldText: string; newText: string }> = []
  for (const node of nodes.value) {
    const before = origin.value.get(node.id)
    if (!before) {
      continue
    }
    for (const [key, column] of WRITABLE_COLUMNS) {
      const oldValue = before[key]
      const newValue = node[key]
      if (normKey(oldValue) === normKey(newValue)) {
        continue
      }
      rows.push({
        node: node.menuName || node.menuCode,
        column,
        oldText: renderValue(key, oldValue),
        newText: renderValue(key, newValue),
      })
    }
  }
  return rows
})

function renderValue(key: string, value: unknown) {
  if (key === 'parentId') {
    return labelOf(value as string | null)
  }
  if (key === 'visible') {
    return value ? '显示' : '隐藏'
  }
  return normKey(value) || '（空）'
}

// ===== 交互：只碰这五列 =====

/**
 * 顶层顺序是一条序列：分组与散页共用同一组顺序号，所以↑↓ 在"未锁定的顶层节点"之间交换。
 * 「系统设置」被排除在可换序列外——它的顺序号是 100，业务分组永远排在它前面。
 */
function move(node: DraftNode, dir: -1 | 1) {
  const list = node.parentId
    ? nodes.value
        .filter((item) => item.parentId === node.parentId && !item.locked)
        .sort(bySort)
    : topLevelNodes.value.filter((item) => !item.locked)
  const current = list.findIndex((item) => item.id === node.id)
  const target = current + dir
  if (current < 0 || target < 0 || target >= list.length) {
    return
  }
  const a = list[current]
  const b = list[target]
  const swap = a.sortNo
  a.sortNo = b.sortNo
  b.sortNo = swap
}

/**
 * 落到目标位置末尾的顺序号。顶层是一条混合序列（分组与散页抢同一个位置），所以顶层要比全部
 * 未锁定的顶层节点；分组内只数页面。「系统设置」恒排在业务段之后，不参与抢位（与后端建组时同口径）。
 */
function tailSortNo(parentId: string | null, excludeId: string) {
  const siblings = parentId === null
    ? nodes.value.filter((node) => !node.parentId && !node.locked)
    : nodes.value.filter((node) => node.parentId === parentId && node.menuType !== 'catalog')
  return Math.max(0, ...siblings.filter((node) => node.id !== excludeId).map((node) => node.sortNo ?? 0)) + 10
}

function reparent(node: DraftNode, raw: string) {
  node.parentId = raw === TOP ? null : raw
  node.sortNo = tailSortNo(node.parentId, node.id)
}

function toggleVisible(node: DraftNode) {
  node.visible = !node.visible
}

function dropInto(catalogId: string) {
  const node = find(draggingId.value)
  clearDrop()
  if (!node || node.locked || node.id === catalogId || node.menuType === 'catalog') {
    return
  }
  reparent(node, catalogId)
}

/** 落点高亮与"正在拖的是谁"都只服务于这一次拖拽，拖完一律清掉 */
function clearDrop() {
  draggingId.value = ''
  dropTargetId.value = ''
}

function discard() {
  applyServerNodes(nodes.value.map((node) => ({ ...(origin.value.get(node.id) || node) })))
}

// ===== 校验：只拦"改完找不到这一页"与"整组入口消失" =====

interface Issue {
  level: 'block' | 'warn' | 'ok'
  text: string
}

const issues = computed<Issue[]>(() => {
  const out: Issue[] = []
  for (const catalog of nodes.value.filter((node) => node.menuType === 'catalog')) {
    if (catalog.locked) {
      continue
    }
    if (!catalog.menuName.trim()) {
      out.push({ level: 'block', text: `分组「${catalog.menuCode}」的名称是空的，保存会被拒绝。` })
    }
    const visiblePages = pagesUnder(catalog.id).filter((page) => page.visible)
    if (catalog.grantedRoleCount === 0 && visiblePages.length) {
      out.push({
        level: 'block',
        text: `「${catalog.menuName}」没有授权给任何在职角色：${visiblePages.length} 张页面的入口会从侧边栏整块消失、也不报错。请到「角色管理 → 分配菜单」勾上这个分组。`,
      })
    } else if (!visiblePages.length) {
      out.push({
        level: 'warn',
        text: `「${catalog.menuName}」里没有可见页面，这一组不会出现在侧边栏（空分组不渲染）。`,
      })
    }
  }
  for (const page of nodes.value.filter((node) => node.menuType === 'menu' && !node.locked)) {
    if (!page.menuName.trim()) {
      out.push({ level: 'block', text: `页面「${page.menuCode}」的名称是空的，保存会被拒绝。` })
    }
  }
  const loose = topLevelNodes.value.filter((node) => node.menuType === 'menu' && node.visible)
  if (loose.length) {
    out.push({
      level: 'warn',
      text: `有 ${loose.length} 张页面挂在顶层（${loose.map((node) => node.menuName).join('、')}）：顶层散页会让图标变多、来路面包屑变空，建议归到一个分组里。`,
    })
  }
  const byName = new Map<string, DraftNode[]>()
  for (const node of topLevelNodes.value) {
    const key = node.menuName.trim().toLowerCase()
    if (key) {
      byName.set(key, [...(byName.get(key) ?? []), node])
    }
  }
  for (const group of byName.values()) {
    if (group.length < 2 || group.every((node) => node.locked)) {
      continue
    }
    out.push({
      level: 'block',
      text: `顶层有两个同名入口「${group[0].menuName}」：侧边栏会并排出现两颗一样的图标，分不出哪个是哪个，请给其中一个换名字。`,
    })
  }
  if (!out.some((issue) => issue.level === 'block')) {
    out.unshift({ level: 'ok', text: '没有拦截项，可保存。' })
  }
  return out
})

const blocked = computed(() => issues.value.some((issue) => issue.level === 'block'))

const saveLabel = computed(() => {
  if (blocked.value) {
    return '保存导航（有拦截项）'
  }
  return diffRows.value.length ? `保存导航（${diffRows.value.length} 处）` : '没有改动'
})

// ===== 预览：按 AppLayout 的算法走一遍（一个顶层节点=一组，空分组不渲染） =====

interface PreviewGroup {
  key: string
  name: string
  sortNo: number
  icon: string | null
  component?: Component
  pages: DraftNode[]
}

const previewGroups = computed<PreviewGroup[]>(() => {
  const groups: PreviewGroup[] = []
  for (const node of topLevelNodes.value) {
    if (node.menuType === 'catalog') {
      const pages = pagesUnder(node.id).filter((page) => page.visible)
      if (!pages.length || (!node.locked && node.grantedRoleCount === 0)) {
        continue
      }
      groups.push({
        key: node.id,
        name: node.menuName,
        sortNo: node.sortNo,
        icon: node.icon ?? null,
        pages,
      })
      continue
    }
    if (node.locked || !node.visible) {
      continue
    }
    groups.push({
      key: node.id,
      name: node.menuName,
      sortNo: node.sortNo,
      icon: node.icon ?? null,
      pages: [node],
    })
  }
  // 没有菜单节点的入口（工作台这类落地页）由模块登记，本页编排不到，如实摆出来
  for (const item of contributedNavItems().filter(
    (entry) => !entry.menuCode && !entry.menuCatalogCode
  )) {
    groups.push({
      key: `fixed-${item.key}`,
      name: item.label,
      sortNo: item.groupOrder ?? 100,
      icon: null,
      component: item.icon,
      pages: [],
    })
  }
  return groups.sort((a, b) => a.sortNo - b.sortNo)
})

// ===== 写库 =====

async function save() {
  const payload = dirtyNodes.value.map((node) => ({
    id: node.id,
    menuName: node.menuName.trim(),
    icon: node.icon || null,
    sortNo: node.sortNo,
    parentId: node.parentId,
    visible: node.visible,
  }))
  saving.value = true
  try {
    applyServerNodes((await saveNavigation(payload)).map(toDraft))
    await auth.loadCurrentMenus()
    ElMessage.success(`导航已保存（${payload.length} 处），你这边侧边栏已刷新`)
  } catch (error) {
    ElMessage.error(toMessage(error, '保存导航失败'))
  } finally {
    saving.value = false
  }
}

function openCatalogDialog() {
  catalogForm.menuName = ''
  catalogForm.icon = ''
  catalogError.value = ''
  catalogDialog.value = true
  // 上一次弹窗留下的红框不能带给新一次填写
  void nextTick(() => catalogFormRef.value?.clearValidate())
}

async function submitCatalog() {
  // 校验失败时 el-form 已经在各字段上标红并给出提示，这里不再补一条顶部总文案
  const valid = await catalogFormRef.value?.validate().catch(() => false)
  if (valid === false) {
    return
  }
  creating.value = true
  try {
    const result = await createNavigationCatalog({
      menuName: catalogForm.menuName.trim(),
      icon: catalogForm.icon || undefined,
    })
    mergeNewNodes(result.nodes.map(toDraft))
    await auth.loadCurrentMenus()
    catalogDialog.value = false
    ElMessage.success(
      `分组「${catalogForm.menuName.trim()}」已创建，已同步授权 ${result.grantedRoleIdCount} 个在职角色`
    )
  } catch (error) {
    catalogError.value = toMessage(error, '新建分组失败')
  } finally {
    creating.value = false
  }
}

onMounted(loadNavigation)
</script>

<style scoped>
.nav-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 12px;
  min-height: 0;
}

.nav-side {
  display: grid;
  grid-template-rows: minmax(150px, 1.2fr) minmax(120px, 1fr) minmax(140px, 1fr);
  gap: 12px;
  min-height: 0;
}

.nav-pane {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--card-bg);
}

.nav-pane-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 10px;
  border-bottom: 1px solid var(--line);
  font-size: 13px;
  color: var(--text-primary);
}

.nav-pane-sub {
  color: var(--text-muted);
  font-size: 12px;
}

.nav-pane-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: 8px 10px;
}

.nav-catalog {
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 4px 8px 6px;
  margin-bottom: 8px;
  background: var(--page-bg);
}

.nav-catalog.is-drop {
  border-color: var(--accent);
  background: var(--tint-bg);
}

.nav-catalog-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nav-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 0 2px 22px;
  cursor: grab;
}

.nav-row-loose {
  border: 1px dashed var(--line);
  border-radius: 6px;
  padding: 4px 8px;
  margin-bottom: 8px;
  background: var(--soft-warn-bg);
  color: var(--soft-warn-ink);
}

.nav-row.is-dirty {
  box-shadow: inset 2px 0 0 var(--accent);
}

.nav-row-empty {
  color: var(--text-faint);
  font-size: 12px;
  cursor: default;
}

.nav-locked {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 5px 8px;
  margin-bottom: 8px;
  color: var(--text-secondary);
  background: var(--subtle-bg);
}

.nav-lock-tag {
  font-size: 11px;
  color: var(--soft-info-ink);
  background: var(--soft-info-bg);
  border-radius: 4px;
  padding: 0 4px;
}

.nav-icon {
  width: 118px;
  flex: none;
}

.nav-name {
  width: 150px;
  flex: none;
}

.nav-parent {
  width: 130px;
  flex: none;
}

.nav-row-icon {
  flex: none;
  color: var(--text-secondary);
}

.nav-count {
  flex: 1 1 auto;
  min-width: 120px;
  text-align: right;
  color: var(--text-muted);
  font-size: 12px;
}

.nav-readonly {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-faint);
  font-size: 12px;
}

.nav-ops {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: none;
}

.nav-rail {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding-bottom: 8px;
}

.nav-rail-dot {
  width: 46px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  font-size: 11px;
  color: var(--text-secondary);
  background: var(--subtle-bg);
  border-radius: 6px;
  padding: 4px 0;
}

.nav-full {
  margin: 0;
  padding-left: 16px;
  font-size: 12px;
  color: var(--text-secondary);
}

.nav-full ul {
  margin: 2px 0 6px;
  padding-left: 16px;
}

.nav-issues {
  margin: 0;
  padding-left: 0;
  list-style: none;
  font-size: 12px;
  line-height: 1.6;
}

.nav-issues li {
  margin-bottom: 6px;
}

.nav-issues .block {
  color: var(--soft-danger-ink);
}

.nav-issues .warn {
  color: var(--soft-warn-ink);
}

.nav-issues .ok {
  color: var(--soft-success-ink);
}

.nav-diff {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.nav-diff th,
.nav-diff td {
  border-bottom: 1px solid var(--line);
  padding: 2px 4px;
  text-align: left;
  vertical-align: top;
}

.nav-diff .old {
  color: var(--text-faint);
  text-decoration: line-through;
}

.nav-diff .new {
  color: var(--soft-success-ink);
}

.nav-diff-empty {
  margin: 0;
  color: var(--text-faint);
  font-size: 12px;
}

.nav-dialog-hint {
  margin: 0;
  color: var(--text-muted);
  font-size: 12px;
}
</style>
