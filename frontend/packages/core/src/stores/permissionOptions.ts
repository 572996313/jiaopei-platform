import { computed, ref } from 'vue'
import type { ComputedRef } from 'vue'
import { fetchPermissionOptions } from '@cornerstone/core/api/system'
import type { PermissionOption } from '@cornerstone/core/api/system'
import { permissionGroups } from '@cornerstone/core/constants/permissionLabels'

/**
 * 权限码下拉选项缓存:以 /system/permission-options(后端 @RequiresPermission 注解扫描
 * + 启用按钮节点中文名)为权威源,供角色弹窗「预设权限」、菜单按钮 permissionCode 下拉、
 * 列表权限标签回显使用。
 * 模块级单例:同一会话共享一次加载结果,页面卸载不清空。
 * 接口失败时整体降级为前端静态常量表(离线兜底,不打断页面)。
 */

/** 分组后的一组权限选项 */
export interface PermissionGroupOption {
  group: string
  items: PermissionOption[]
}

const state = ref<{ loaded: boolean; options: PermissionOption[] }>({ loaded: false, options: [] })
let loadPromise: Promise<void> | null = null

/** 静态常量表:code → 原有分组(系统管理/组织档案/教务管理)。未命中(新增码)归入「其他权限」组 */
const staticGroupByCode = new Map(
  permissionGroups().flatMap((group) =>
    group.items.map((item) => [item.code, group.group] as const)
  )
)

function keepUnique(options: PermissionOption[]): PermissionOption[] {
  const seen = new Set<string>()
  return options.filter((item) => {
    if (seen.has(item.code)) {
      return false
    }
    seen.add(item.code)
    return true
  })
}

async function fetchOnce() {
  if (loadPromise) {
    return loadPromise
  }
  loadPromise = fetchPermissionOptions()
    .then((options) => {
      state.value = { loaded: true, options: keepUnique(options) }
    })
    .catch(() => {
      // 加载失败:保留未加载标记,由兜底逻辑渲染静态常量表;下次进入页面可重试
      state.value = { loaded: false, options: [] }
      throw new Error('权限选项加载失败')
    })
    .finally(() => {
      loadPromise = null
    })
  return loadPromise
}

/** 确保已加载(成功一次后不再重复请求;失败可重试) */
export async function ensurePermissionOptionsLoaded() {
  if (!state.value.loaded) {
    await fetchOnce()
  }
}

function fallbackOptions(): PermissionOption[] {
  return permissionGroups().flatMap((group) =>
    group.items.map((item) => ({ code: item.code, label: item.label, menuNames: [item.label] }))
  )
}

/** 当前选项:接口结果优先,未加载/失败时返回静态常量表(离线兜底) */
function currentOptions(): PermissionOption[] {
  return state.value.loaded && state.value.options.length > 0
    ? state.value.options
    : fallbackOptions()
}

/**
 * 分组后的权限选项(角色弹窗复选框网格数据源):
 * 命中前端静态分组的码沿用原分组,未命中(新增码如 system:operlog:read)归入「其他权限」组。
 */
export function usePermissionOptionGroups(): ComputedRef<PermissionGroupOption[]> {
  return computed(() => {
    const groups: PermissionGroupOption[] = []
    const push = (group: string, item: PermissionOption) => {
      let bucket = groups.find((entry) => entry.group === group)
      if (!bucket) {
        bucket = { group, items: [] }
        groups.push(bucket)
      }
      bucket.items.push(item)
    }
    for (const item of currentOptions()) {
      push(staticGroupByCode.get(item.code) ?? '其他权限', item)
    }
    return groups
  })
}

/** 平铺权限选项(菜单按钮 permissionCode 下拉等用) */
export function usePermissionOptionList(): ComputedRef<Array<{ value: string; label: string }>> {
  return computed(() => currentOptions().map((item) => ({ value: item.code, label: item.label })))
}

/** 权限码 → 中文名:动态 label 优先(按钮节点 menuName),静态常量表兜底,最后回退为权限码本身 */
export function permissionOptionLabel(code: string): string {
  const dynamic = currentOptions().find((item) => item.code === code)
  if (dynamic?.label) {
    return dynamic.label
  }
  const staticLabel = staticGroupByCode.size
    ? permissionGroups()
        .flatMap((group) => group.items)
        .find((item) => item.code === code)?.label
    : undefined
  return staticLabel || code
}
