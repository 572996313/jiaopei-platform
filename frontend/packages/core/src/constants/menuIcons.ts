import * as elementPlusIcons from '@element-plus/icons-vue'
import type { Component } from 'vue'

/**
 * 侧边栏/菜单管理共用:后台 sys_menu.icon → Element Plus 图标组件。
 * 全量放开:任意 Element Plus 图标名均可直接配置(如 connection / Connection / alarm-clock),
 * 大小写与连字符不敏感;历史非标准值(home/users 等)已由 V35 迁移洗成标准名。
 */

/** 匹配键(去大小写/连字符)→ 标准图标名 */
const canonicalNameByKey = new Map<string, string>()
for (const name of Object.keys(elementPlusIcons)) {
  if (name === 'default') {
    continue
  }
  canonicalNameByKey.set(name.toLowerCase().replace(/[^a-z0-9]/g, ''), name)
}

/** 把任意写法(大小写 Connection / 连字符 alarm-clock)解析为标准图标名,解析不到原样返回 */
export function normalizeMenuIconKey(key?: string | null): string {
  if (!key) {
    return ''
  }
  const raw = key.trim()
  return canonicalNameByKey.get(raw.toLowerCase().replace(/[^a-z0-9]/g, '')) ?? raw
}

/** 图标 key → 组件;未知 key 返回 undefined(不渲染图标,避免报错) */
export function resolveMenuIcon(key?: string | null): Component | undefined {
  const canonical = normalizeMenuIconKey(key)
  return canonical
    ? (elementPlusIcons[canonical as keyof typeof elementPlusIcons] as Component)
    : undefined
}

/** 菜单管理图标下拉:全量 Element Plus 图标(按名称排序) */
export const MENU_ICON_OPTIONS = [...canonicalNameByKey.values()].sort().map((name) => ({
  value: name,
  label: name,
}))
