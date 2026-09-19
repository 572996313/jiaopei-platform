import {
  AlarmClock,
  Avatar,
  Calendar,
  Collection,
  Grid,
  House,
  List,
  Lock,
  Management,
  Menu,
  Monitor,
  Notebook,
  OfficeBuilding,
  Platform,
  Reading,
  Setting,
  Tools,
  User,
  UserFilled,
} from '@element-plus/icons-vue'
import type { Component } from 'vue'

/**
 * 侧边栏/菜单管理共用:后台 sys_menu.icon 存的字符串 key → Element Plus 图标组件。
 * 只在此注册允许使用的图标 key;未知 key 返回 undefined(不渲染图标,避免报错)。
 */
export const MENU_ICON_COMPONENTS: Record<string, Component> = {
  setting: Setting,
  user: User,
  'user-filled': UserFilled,
  avatar: Avatar,
  team: UserFilled,
  menu: Menu,
  book: Reading,
  reading: Reading,
  tools: Tools,
  monitor: Monitor,
  office: OfficeBuilding,
  'office-building': OfficeBuilding,
  calendar: Calendar,
  'alarm-clock': AlarmClock,
  grid: Grid,
  list: List,
  notebook: Notebook,
  collection: Collection,
  lock: Lock,
  house: House,
  management: Management,
  platform: Platform,
}

export function resolveMenuIcon(key?: string | null): Component | undefined {
  if (!key) {
    return undefined
  }
  return MENU_ICON_COMPONENTS[key] ?? MENU_ICON_COMPONENTS[key.toLowerCase()]
}

/** 将历史数据中任意写法(如 'Setting')规整为注册表内的小写 key;不在注册表则原样返回 */
export function normalizeMenuIconKey(key?: string | null): string {
  if (!key) {
    return ''
  }
  if (MENU_ICON_COMPONENTS[key]) {
    return key
  }
  const lowered = key.toLowerCase()
  return MENU_ICON_COMPONENTS[lowered] ? lowered : key
}

export const MENU_ICON_OPTIONS = Object.keys(MENU_ICON_COMPONENTS).map((key) => ({
  value: key,
  label: key,
}))
