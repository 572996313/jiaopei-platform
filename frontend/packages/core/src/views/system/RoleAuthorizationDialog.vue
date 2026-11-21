<template>
  <el-dialog
    :model-value="modelValue"
    title="授权页面与按钮"
    width="920px"
    top="6vh"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="education-dialog-context">
      <div><strong>角色:</strong>{{ role?.roleName || '-' }}</div>
      <div><strong>角色编码:</strong>{{ role?.roleCode || '-' }}</div>
      <div><strong>机构:</strong>{{ institutionStore.currentInstitutionName }}</div>
      <div class="role-auth-summary">
        已授权 {{ grantedPageCount }} 个页面、{{ grantedActionCount }} 项操作
      </div>
    </div>

    <div v-if="dataScopes.length" class="role-auth-scopes">
      <div
        v-for="scope in dataScopes"
        :key="scope.dimensionKey"
        class="role-auth-scope"
      >
        <div class="role-auth-scope-head">
          <span class="role-auth-scope-title">{{ scope.dimensionLabel }}</span>
          <span v-if="!scope.configured" class="role-auth-scope-hint">
            未配置时等同{{ scope.allOptionLabel }}
          </span>
        </div>
        <el-radio-group v-model="scope.scopeMode" size="small">
          <el-radio value="ALL">{{ scope.allOptionLabel }}</el-radio>
          <el-radio value="PART">{{ scope.partOptionLabel }}</el-radio>
        </el-radio-group>
        <el-select
          v-if="scope.scopeMode === 'PART'"
          v-model="scope.values"
          multiple
          filterable
          collapse-tags
          class="role-auth-scope-select"
          :placeholder="`请选择${scope.partOptionLabel}`"
        >
          <el-option
            v-for="option in scope.options"
            :key="option.value"
            :label="option.name || option.code || option.value"
            :value="option.value"
            :disabled="option.selectable === false && !scope.values.includes(option.value)"
          />
        </el-select>
      </div>
    </div>

    <el-alert
      v-if="errorText"
      :title="errorText"
      type="error"
      show-icon
      :closable="false"
      class="education-alert"
    />

    <el-alert
      v-if="catalog && catalog.gaps.length > 0"
      :title="`按钮授权目录存在 ${catalog.gaps.length} 处配置缺口，请联系平台管理员在菜单管理中补齐`"
      type="warning"
      show-icon
      :closable="false"
      class="education-alert"
    />

    <div v-loading="loading" class="role-auth-layout">
      <aside class="role-auth-pages">
        <el-input v-model="pageKeyword" placeholder="搜索页面名称" clearable size="small" />
        <div class="role-auth-page-list">
          <template v-for="group in filteredPageGroups" :key="group.id">
            <div v-if="!isStandaloneGroup(group)" class="role-auth-group" :title="group.name">
              {{ group.name }}
            </div>
            <div
              v-for="page in group.pages"
              :key="page.menuId"
              class="role-auth-page-item"
              :class="{
                'is-active': page.menuId === activePageId,
                'is-child': !isStandaloneGroup(group),
              }"
              role="button"
              tabindex="0"
              @click="activePageId = page.menuId"
              @keydown.enter="activePageId = page.menuId"
            >
              <el-checkbox
                :model-value="isPageChecked(page)"
                :disabled="isPageCsvLocked(page)"
                @click.stop
                @change="togglePage(page)"
              />
              <span class="role-auth-page-name" :title="page.menuName">{{ page.menuName }}</span>
              <el-tag v-if="isPageCsvLocked(page)" size="small" type="warning" effect="plain">锁定</el-tag>
              <el-tag v-else-if="pageStateTag(page)" size="small" effect="plain">
                {{ pageStateTag(page) }}
              </el-tag>
            </div>
          </template>
          <div v-if="filteredPageGroups.length === 0" class="role-auth-empty">没有匹配的页面</div>
        </div>
      </aside>

      <section class="role-auth-actions">
        <template v-if="activePage">
          <header class="role-auth-actions-head">
            <div>
              <div class="role-auth-actions-title">{{ activePage.menuName }}</div>
              <div class="role-auth-actions-sub">{{ activePageHint }}</div>
            </div>
            <el-checkbox
              :model-value="isPageChecked(activePage)"
              :disabled="isPageCsvLocked(activePage)"
              @change="togglePage(activePage)"
            >
              授予本页访问
            </el-checkbox>
          </header>

          <div v-if="activePage.actions.length === 0" class="role-auth-empty">
            本页没有可配置的操作；勾选左侧页面即可进入并查看。
          </div>

          <div v-else class="role-auth-action-list">
            <div
              v-for="action in activePage.actions"
              :key="action.actionKey"
              class="role-auth-action"
              :class="{ 'is-locked': isActionCsvLocked(action) }"
            >
              <el-checkbox
                :model-value="isActionChecked(action, activePage)"
                :disabled="
                  !isPageChecked(activePage) ||
                  action.mode === 'uiOnly' ||
                  isActionCsvLocked(action)
                "
                @change="toggleAction(action)"
              >
                <span class="role-auth-action-label">{{ action.label }}</span>
              </el-checkbox>
              <div v-if="action.description" class="role-auth-action-desc">
                {{ action.description }}
              </div>
              <div class="role-auth-action-tags">
                <el-tag v-if="action.sensitive" size="small" type="danger" effect="plain">
                  敏感
                </el-tag>
                <el-tooltip
                  v-if="action.mode === 'linked'"
                  content="与其他页面共用一项权限，勾选任意一个会同时开启这些操作"
                  placement="top"
                >
                  <el-tag size="small" effect="plain">同码联动</el-tag>
                </el-tooltip>
                <el-tag v-if="action.mode === 'uiOnly'" size="small" type="info" effect="plain">
                  跟随页面
                </el-tag>
                <el-tag
                  v-if="isActionCsvLocked(action)"
                  size="small"
                  type="warning"
                  effect="plain"
                >
                  由历史预设权限授予
                </el-tag>
              </div>
              <div v-if="isActionCsvLocked(action)" class="role-auth-action-locked">
                该权限来自角色历史上直接配置的权限码，请使用列表中的「高级视图」清理。
              </div>
              <div v-else-if="action.gap" class="role-auth-action-locked">
                {{ action.gap }}
              </div>
            </div>
          </div>
        </template>
        <div v-else class="role-auth-empty">请选择左侧页面</div>
      </section>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="role-auth-session-tip"
      title="保存后，持有该角色的在线用户会被立即退出登录，重新登录后按新的授权显示菜单与按钮。"
    />

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="loading" @click="submit">
        保存授权
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchActionCatalog,
  fetchRoleAuthorization,
  updateRoleAuthorization,
} from '@cornerstone/core/api/system'
import type {
  ActionCatalog,
  ActionCatalogAction,
  ActionCatalogPage,
  RoleDataScopeView,
  SystemRoleSummary,
} from '@cornerstone/core/api/system'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import { toMessage } from './shared'

const props = defineProps<{
  modelValue: boolean
  role: SystemRoleSummary | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  /** 保存成功后通知宿主刷新角色列表 */
  saved: []
}>()

const institutionStore = useInstitutionStore()
const institutionId = computed(() => institutionStore.currentInstitutionId)

const loading = ref(false)
const saving = ref(false)
const errorText = ref('')
const catalog = ref<ActionCatalog | null>(null)
/** 当前勾选的节点 id（页面、按钮与目录；目录只在打开时存在，保存时由后端补齐） */
const grantedIds = ref<string[]>([])
const presetCodes = ref<string[]>([])
/** 角色在当前机构下的数据范围（一期只有校区一个维度） */
const dataScopes = ref<RoleDataScopeView[]>([])
const activePageId = ref('')
const pageKeyword = ref('')

const pages = computed(() => catalog.value?.pages ?? [])

const allActions = computed(() =>
  pages.value.flatMap((page) => page.actions.map((action) => ({ page, action })))
)

/** 同一权限码下的动作共用一个授权节点，勾一个即全开（Phase 0 的同码联动口径） */
const actionsByPermission = computed(() => {
  const groups = new Map<string, ActionCatalogAction[]>()
  for (const { action } of allActions.value) {
    if (action.mode === 'uiOnly' || !action.permissionCode) {
      continue
    }
    const list = groups.get(action.permissionCode) ?? []
    list.push(action)
    groups.set(action.permissionCode, list)
  }
  return groups
})

const lockedCodes = computed(() => new Set(presetCodes.value))

interface ActionCatalogPageGroup {
  id: string
  name: string
  pages: ActionCatalogPage[]
}

/**
 * 左侧页面按侧边栏的顶层分组排列（后端已按「分组 sortNo → 组内页面 sortNo」排好），
 * 这里只把连续的同组页面切成一段；关键字过滤后空组自然消失。
 */
const filteredPageGroups = computed<ActionCatalogPageGroup[]>(() => {
  const keyword = pageKeyword.value.trim().toLowerCase()
  const groups: ActionCatalogPageGroup[] = []
  for (const page of pages.value) {
    if (
      keyword &&
      !page.menuName.toLowerCase().includes(keyword) &&
      !page.menuCode.toLowerCase().includes(keyword)
    ) {
      continue
    }
    const groupId = page.groupId || page.menuId
    const current = groups[groups.length - 1]
    if (current && current.id === groupId) {
      current.pages.push(page)
      continue
    }
    groups.push({
      id: groupId,
      name: page.groupName || page.menuName,
      pages: [page],
    })
  }
  return groups
})

/** 顶层散页自成一组：不再重复渲染与页面同名的组标题 */
function isStandaloneGroup(group: ActionCatalogPageGroup) {
  return group.pages.length === 1 && group.pages[0].menuId === group.id
}

const activePage = computed<ActionCatalogPage | null>(() => {
  const found = pages.value.find((page) => page.menuId === activePageId.value)
  return found ?? pages.value[0] ?? null
})

const grantedPageCount = computed(
  () => pages.value.filter((page) => isPageChecked(page)).length
)

const grantedActionCount = computed(
  () => allActions.value.filter(({ page, action }) => isActionChecked(action, page)).length
)

function isCodeCsvLocked(code?: string | null) {
  return Boolean(code && lockedCodes.value.has(code))
}

function isPageCsvLocked(page: ActionCatalogPage) {
  return isCodeCsvLocked(page.readPermissionCode)
}

function isPageChecked(page: ActionCatalogPage) {
  return grantedIds.value.includes(page.menuId) || isPageCsvLocked(page)
}

function isActionCsvLocked(action: ActionCatalogAction) {
  return action.mode !== 'uiOnly' && (action.grantedByCsv === true || isCodeCsvLocked(action.permissionCode))
}

function isActionChecked(action: ActionCatalogAction, page: ActionCatalogPage) {
  if (action.mode === 'uiOnly') {
    return isPageChecked(page)
  }
  if (isActionCsvLocked(action)) {
    return true
  }
  return Boolean(action.grantMenuId && grantedIds.value.includes(action.grantMenuId))
}

function pageStateTag(page: ActionCatalogPage) {
  if (!isPageChecked(page)) {
    return ''
  }
  const checkedActions = page.actions.filter((action) => isActionChecked(action, page)).length
  if (page.actions.length === 0) {
    return '只读'
  }
  if (page.actions.some((action) => action.grantMenuId === page.menuId)) {
    return '读写同码'
  }
  return checkedActions > 0 ? `可操作 ${checkedActions} 项` : '只读'
}

const activePageHint = computed(() => {
  const page = activePage.value
  if (!page) {
    return ''
  }
  if (!isPageChecked(page)) {
    return '页面未授权：勾选「授予本页访问」后才能配置操作。'
  }
  if (page.actions.some((action) => action.grantMenuId === page.menuId)) {
    return '本页读写同码：勾选页面即获得全部操作，取消任一操作会同时失去页面访问。'
  }
  return '勾选操作即授予对应按钮；保存后未勾选的按钮在该角色侧隐藏，接口同时拒绝。'
})

function togglePage(page: ActionCatalogPage) {
  if (isPageCsvLocked(page)) {
    return
  }
  const next = new Set(grantedIds.value)
  if (next.has(page.menuId)) {
    next.delete(page.menuId)
    // 页面撤销后，仅服务本页的写节点一并撤销；被其他已授权页面共用的码保留
    // （如教师/学员共用 education_profile_write，撤掉教师页时学员页的写权限不能跟着没）
    const otherGrantedPages = pages.value.filter(
      (candidate) => candidate.menuId !== page.menuId && next.has(candidate.menuId)
    )
    for (const action of page.actions) {
      if (!action.grantMenuId || action.grantMenuId === page.menuId) {
        continue
      }
      const sharedByOtherPage = otherGrantedPages.some((candidate) =>
        candidate.actions.some(
          (other) => other.mode !== 'uiOnly' && other.grantMenuId === action.grantMenuId
        )
      )
      if (!sharedByOtherPage) {
        next.delete(action.grantMenuId)
      }
    }
  } else {
    next.add(page.menuId)
    // 勾页面 = 本页全部操作：读取已不再挂码，写行按钮跟着页面一起授，
    // 不再让操作员在一页里逐项勾；要只读角色仍可单独取消某个按钮。
    for (const action of page.actions) {
      if (action.mode === 'uiOnly' || !action.grantMenuId) {
        continue
      }
      next.add(action.grantMenuId)
    }
  }
  grantedIds.value = [...next]
}

function toggleAction(action: ActionCatalogAction) {
  if (action.mode === 'uiOnly' || isActionCsvLocked(action)) {
    return
  }
  const group = action.permissionCode
    ? actionsByPermission.value.get(action.permissionCode) ?? [action]
    : [action]
  const grantIds = group
    .map((item) => item.grantMenuId)
    .filter((id): id is string => Boolean(id))
  const currentlyChecked = grantIds.some((id) => grantedIds.value.includes(id))
  const next = new Set(grantedIds.value)
  for (const id of grantIds) {
    if (currentlyChecked) {
      next.delete(id)
    } else {
      next.add(id)
    }
  }
  grantedIds.value = [...next]
}

function collectMenuIds() {
  // 只提交目录内的节点；目录之外的既有权（如小程序端页面）后端会原样保留，
  // 这里若一起回传反而会被"不在可授权目录内"的校验拒绝。
  const managed = new Set<string>()
  for (const page of pages.value) {
    managed.add(page.menuId)
  }
  for (const { action } of allActions.value) {
    if (action.grantMenuId) {
      managed.add(action.grantMenuId)
    }
  }
  return grantedIds.value.filter((id) => managed.has(id))
}

async function load() {
  const role = props.role
  if (!role) {
    return
  }
  loading.value = true
  errorText.value = ''
  catalog.value = null
  grantedIds.value = []
  presetCodes.value = []
  dataScopes.value = []
  activePageId.value = ''
  pageKeyword.value = ''
  try {
    const [catalogResult, authorization] = await Promise.all([
      fetchActionCatalog(role.id),
      fetchRoleAuthorization(role.id, institutionId.value),
    ])
    catalog.value = catalogResult
    grantedIds.value = authorization.menuIds
    presetCodes.value = authorization.presetPermissionCodes
    dataScopes.value = authorization.dataScopes
    activePageId.value = catalogResult.pages[0]?.menuId ?? ''
  } catch (error) {
    errorText.value = toMessage(error, '角色授权加载失败')
  } finally {
    loading.value = false
  }
}

function collectDataScopes() {
  return dataScopes.value.map((scope) => ({
    dimensionKey: scope.dimensionKey,
    scopeMode: scope.scopeMode,
    values: scope.scopeMode === 'PART' ? [...scope.values] : [],
  }))
}

async function submit() {
  if (!props.role) {
    return
  }
  const emptyScope = dataScopes.value.find(
    (scope) => scope.scopeMode === 'PART' && scope.values.length === 0
  )
  if (emptyScope) {
    errorText.value = `${emptyScope.partOptionLabel}至少要选择一个`
    return
  }
  saving.value = true
  errorText.value = ''
  try {
    await updateRoleAuthorization(
      props.role.id,
      { menuIds: collectMenuIds(), dataScopes: collectDataScopes() },
      institutionId.value
    )
    ElMessage.success('角色授权已保存，相关用户需重新登录后生效')
    emit('saved')
    emit('update:modelValue', false)
  } catch (error) {
    errorText.value = toMessage(error, '保存角色授权失败')
  } finally {
    saving.value = false
  }
}

watch(
  () => [props.modelValue, props.role?.id, institutionId.value] as const,
  ([visible]) => {
    if (visible) {
      void load()
    }
  }
)
</script>

<style scoped>
.role-auth-summary {
  color: var(--text-secondary);
}

.role-auth-scopes {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 12px 0;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-block);
}

.role-auth-scope {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.role-auth-scope-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.role-auth-scope-title {
  font-size: 13px;
  font-weight: 600;
}

.role-auth-scope-hint {
  font-size: 12px;
  color: var(--text-faint);
}

.role-auth-scope-select {
  max-width: 100%;
}

.role-auth-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
  min-height: 380px;
}

.role-auth-pages {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
}

.role-auth-page-list {
  flex: 1 1 auto;
  min-height: 0;
  max-height: 420px;
  overflow: auto;
  padding: 6px;
  border: 1px solid var(--border);
  border-radius: var(--radius-block);
}

/* 分组标题吸顶：列表滚动时当前组名一直可见，按导航里的分组找页面不用来回翻 */
.role-auth-group {
  position: sticky;
  top: 0;
  z-index: 1;
  margin: 8px -2px 2px;
  padding: 4px 8px;
  background: var(--card-bg);
  color: var(--text-faint);
  font-size: 12px;
  font-weight: 600;
}

.role-auth-group:first-child {
  margin-top: 0;
}

.role-auth-page-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 6px 8px;
  border: 0;
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 13px;
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.role-auth-page-item:hover {
  background: var(--tint-bg);
}

/* 组内页面缩进一级，和外面侧边栏的「目录 → 页面」看起来是同一种层级 */
.role-auth-page-item.is-child {
  padding-left: 24px;
}

.role-auth-page-item.is-active {
  background: var(--tint-bg);
  font-weight: 600;
}

.role-auth-page-name {
  flex: 1 1 auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-auth-actions {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-block);
}

.role-auth-actions-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border);
}

.role-auth-actions-title {
  font-size: 15px;
  font-weight: 600;
}

.role-auth-actions-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-faint);
}

.role-auth-action-list {
  flex: 1 1 auto;
  min-height: 0;
  max-height: 360px;
  overflow: auto;
  padding-top: 8px;
}

.role-auth-action {
  padding: 8px 6px;
  border-radius: 6px;
}

.role-auth-action + .role-auth-action {
  border-top: 1px dashed var(--border);
}

.role-auth-action.is-locked {
  background: var(--tint-bg);
}

.role-auth-action-label {
  font-size: 13px;
  color: var(--text-primary);
}

.role-auth-action-desc {
  margin-left: 24px;
  font-size: 12px;
  color: var(--text-faint);
}

.role-auth-action-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 4px 0 0 24px;
}

.role-auth-action-locked {
  margin: 4px 0 0 24px;
  font-size: 12px;
  color: var(--el-color-warning, #e6a23c);
}

.role-auth-empty {
  padding: 24px 8px;
  color: var(--text-faint);
  font-size: 13px;
  text-align: center;
}

.role-auth-session-tip {
  margin-top: 12px;
}
</style>
