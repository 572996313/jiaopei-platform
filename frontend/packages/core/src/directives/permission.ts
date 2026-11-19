import type { Directive, DirectiveBinding } from 'vue'
import { useAuthStore } from '@cornerstone/core/stores/auth'

/**
 * 按钮级权限显隐：`v-permission="'system:user:manage'"` / `v-permission="['a', 'b']"`。
 *
 * <p>只负责"看不见"，不是安全边界——接口仍由后端 `@RequiresPermission` 硬拦。
 * 多码时任一命中即显示；空值视为公开。权限不足用 `display:none` 隐藏而非移除节点，
 * 避免权限刷新后 Vue 仍在更新已摘除的 DOM。</p>
 */
function resolveRequiredCodes(value: unknown): string[] {
  if (typeof value === 'string') {
    return value
      .split(/[,\s]+/)
      .map((code) => code.trim())
      .filter(Boolean)
  }
  if (Array.isArray(value)) {
    return value
      .filter((code): code is string => typeof code === 'string')
      .map((code) => code.trim())
      .filter(Boolean)
  }
  return []
}

function applyPermission(el: HTMLElement, binding: DirectiveBinding) {
  const codes = resolveRequiredCodes(binding.value)
  const allowed = codes.length === 0 || codes.some((code) => useAuthStore().hasPermission(code))
  if (allowed) {
    el.style.display = el.dataset.permissionDisplay ?? ''
    delete el.dataset.permissionDisplay
    return
  }
  if (el.dataset.permissionDisplay === undefined) {
    el.dataset.permissionDisplay = el.style.display
  }
  el.style.display = 'none'
}

export const permissionDirective: Directive<HTMLElement, unknown> = {
  mounted: applyPermission,
  updated: applyPermission,
}
