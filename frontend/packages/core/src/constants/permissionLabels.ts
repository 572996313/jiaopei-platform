export interface PermissionLabelItem {
  code: string
  label: string
  group: string
}

/**
 * 权限码中文名映射（与后端 @RequiresPermission 注解字面量一一对应）。
 * 兜底用途：按钮权限模型的正式中文名来自 sys_menu 按钮节点的 menu_name，
 * 角色勾选的节点变更后以菜单管理维护的节点名为准。
 */
export const permissionLabels: PermissionLabelItem[] = [
  { code: 'system:user:manage', label: '用户维护', group: '系统管理' },
  { code: 'system:role:manage', label: '角色维护', group: '系统管理' },
  { code: 'system:menu:manage', label: '菜单维护', group: '系统管理' },
  { code: 'system:dict:manage', label: '字典维护', group: '系统管理' },
  { code: 'system:config:read', label: '参数查看', group: '系统管理' },
  { code: 'system:config:write', label: '参数维护', group: '系统管理' },
  { code: 'system:list-config:manage', label: '列表配置维护', group: '系统管理' },
  { code: 'organization:read', label: '组织查看', group: '组织档案' },
  { code: 'organization:write', label: '组织维护', group: '组织档案' },
  { code: 'education:read', label: '教务查看', group: '教务管理' },
  { code: 'education:write', label: '教务维护', group: '教务管理' },
]

const permissionLabelMap = new Map(permissionLabels.map((item) => [item.code, item]))

export function permissionLabel(code: string): string {
  return permissionLabelMap.get(code)?.label || code
}

export function permissionGroups(): Array<{ group: string; items: PermissionLabelItem[] }> {
  const groups: Array<{ group: string; items: PermissionLabelItem[] }> = []
  for (const item of permissionLabels) {
    let bucket = groups.find((entry) => entry.group === item.group)
    if (!bucket) {
      bucket = { group: item.group, items: [] }
      groups.push(bucket)
    }
    bucket.items.push(item)
  }
  return groups
}
