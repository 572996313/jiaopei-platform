import { computed, ref } from 'vue'
import type { Ref } from 'vue'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { fetchSystemDictItems } from '@cornerstone/core/api/system'
import type { DictItemSummary } from '@cornerstone/core/api/system'

/**
 * 系统设置各模块页的共享工具与状态字典。
 * 原 SystemManagementView 单页内 7 个 tab 共用同一份状态字典数据(common_status),
 * 拆分后改为模块级缓存:同一会话内多个页面实例共享一次加载结果,
 * 页面卸载不清空,避免在菜单/字典/角色/用户页之间切换时反复请求。
 */

export function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

export function trimToUndefined(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

export function parseCustomPermissionCodes(raw: string) {
  return raw
    .split(/[\n,]/)
    .map((item) => item.trim())
    .filter((item) => item.length > 0)
}

/** 状态字典加载失败或尚未加载时的兜底选项(与后端 common_status 一致) */
export const fallbackStatusOptions = [
  { value: 'active', label: '启用' },
  { value: 'disabled', label: '停用' },
]

let statusRef: Ref<DictItemSummary[]> | null = null
let statusLoaded = false
let loadPromise: Promise<void> | null = null

function ensureStatusDictState() {
  if (!statusRef) {
    statusRef = ref<DictItemSummary[]>([])
  }
}

async function fetchCommonStatusOnce() {
  if (loadPromise) {
    return loadPromise
  }
  loadPromise = fetchSystemDictItems('common_status')
    .then((items) => {
      ensureStatusDictState()
      statusRef!.value = items
      statusLoaded = true
    })
    .catch((error) => {
      // 加载失败保留空数组,由兜底选项渲染;下次进入页面可重试
      ensureStatusDictState()
      statusRef!.value = []
      throw error
    })
    .finally(() => {
      loadPromise = null
    })
  return loadPromise
}

/**
 * 业务状态字典:启用/停用。缓存一次加载结果(即使为空也不再重复请求),
 * 失败时抛错由调用方决定展示方式。
 */
export async function ensureStatusDictLoaded() {
  ensureStatusDictState()
  if (!statusLoaded) {
    await fetchCommonStatusOnce()
  }
}

/** 字典管理页在维护 common_status 类型(增删改条目)后调用,保持其他页面状态选项一致 */
export function refreshStatusDictCache(items: DictItemSummary[]) {
  ensureStatusDictState()
  statusRef!.value = items
  statusLoaded = true
}

function localizedDictLabel(value?: string | null, fallback?: string | null) {
  if (value === 'active') {
    return '启用'
  }
  if (value === 'disabled') {
    return '停用'
  }
  return fallback || value || '-'
}

/** 启用/停用下拉选项:优先 common_status 字典,加载前/失败后用兜底选项 */
export function useStatusOptions() {
  ensureStatusDictState()
  return computed(() => {
    const items = statusRef!.value
    if (items.length === 0) {
      return fallbackStatusOptions
    }
    return items
      .filter((item) => item.status !== 'disabled')
      .map((item) => ({
        value: item.itemValue,
        label: localizedDictLabel(item.itemValue, item.itemName),
      }))
  })
}

/** 状态值 → 显示文案(含兜底) */
export function useStatusLabelMap() {
  const statusOptions = useStatusOptions()
  return computed(() => new Map(statusOptions.value.map((item) => [item.value, item.label])))
}

export function statusLabel(status: string | null | undefined, map: Map<string, string>) {
  if (!status) {
    return '未设置'
  }
  return map.get(status) || localizedDictLabel(status, status)
}

export function statusTagType(status?: string | null) {
  return status === 'active' ? 'success' : 'info'
}

export function menuTypeLabel(type?: string | null) {
  if (type === 'catalog') {
    return '目录'
  }
  if (type === 'button') {
    return '按钮'
  }
  return '菜单'
}

/**
 * 权限判定集合(从原 SystemManagementView 拆分)。
 * canManageMenus 等依赖 auth store 的当前用户权限。
 */
export function useSystemPermissions() {
  const auth = useAuthStore()
  return computed(() => ({
    canManageMenus: auth.hasPermission('system:menu:manage'),
    canManageUsers: auth.hasPermission('system:user:manage'),
    canManageRoles: auth.hasPermission('system:role:manage'),
    canWriteConfigs: auth.hasPermission('system:config:write'),
    // 机构=租户属底座，权限码用底座自己的 tenant:*（阶段五-0c，业务模块的 organization:* 不混用）
    canWriteInstitution: auth.hasPermission('tenant:write'),
    canManageDicts: auth.hasPermission('system:dict:manage'),
    canManageListConfigs: auth.hasPermission('system:list-config:manage'),
    canManageDataSources: auth.hasPermission('system:data-source:manage'),
    canManageInvitations: auth.hasPermission('system:invitation:manage'),
  }))
}

/** 机构名称回显与格式化(用户列表 institutionName 渲染用) */
export function useInstitutionDisplay() {
  const institutionMap = new Map<string, string>()

  function formatInstitution(id?: string | null) {
    if (!id) {
      return '未设置'
    }
    return institutionMap.get(id) || `机构 #${id}`
  }

  return { institutionMap, formatInstitution }
}
