import { contributedPermissionLabels } from '@cornerstone/core/extension/module-extensions'

export interface PermissionLabelItem {
  code: string
  label: string
  group: string
}

/**
 * 底座权限码中文名映射（与 cornerstone-core 各 Controller 的 @RequiresPermission 字面量一一对应）。
 * 业务模块的码不放这张表：由各自入口经 registerPermissionLabels 贡献，见 allPermissionLabels()。
 * 这里是权限码中文名的**权威源**：一个码可以挂在多个页面节点上（如某个读码覆盖整个模块的页面），
 * 节点名说的是"挂在哪"而不是"码叫什么"，所以展示时先查本表、再退到后端返回的节点名。
 * 新增权限码时同步补一条，否则界面会把权限码本身当中文名显示。
 */
const basePermissionLabels: PermissionLabelItem[] = [
  { code: 'system:user:manage', label: '用户维护', group: '系统管理' },
  { code: 'system:role:manage', label: '角色维护', group: '系统管理' },
  { code: 'system:menu:manage', label: '菜单维护', group: '系统管理' },
  { code: 'system:dict:manage', label: '字典维护', group: '系统管理' },
  { code: 'system:config:read', label: '参数查看', group: '系统管理' },
  { code: 'system:config:write', label: '参数维护', group: '系统管理' },
  { code: 'system:list-config:manage', label: '列表配置维护', group: '系统管理' },
  { code: 'system:operlog:read', label: '操作日志查看', group: '系统管理' },
  { code: 'system:data-source:manage', label: '数据源配置维护', group: '系统管理' },
  { code: 'system:invitation:manage', label: '注册凭证维护', group: '系统管理' },
  { code: 'tenant:read', label: '机构资料查看', group: '机构(租户)' },
  { code: 'tenant:write', label: '机构资料维护', group: '机构(租户)' },
]

/**
 * 全量权限码标签 = 底座内置 + 已装配业务模块登记的。
 * 每次调用现算：模块入口的登记是 import 副作用，取快照会早于注册。
 */
export function allPermissionLabels(): PermissionLabelItem[] {
  return [...basePermissionLabels, ...contributedPermissionLabels()]
}

export function permissionLabel(code: string): string {
  return allPermissionLabels().find((item) => item.code === code)?.label || code
}

export function permissionGroups(): Array<{ group: string; items: PermissionLabelItem[] }> {
  const groups: Array<{ group: string; items: PermissionLabelItem[] }> = []
  for (const item of allPermissionLabels()) {
    let bucket = groups.find((entry) => entry.group === item.group)
    if (!bucket) {
      bucket = { group: item.group, items: [] }
      groups.push(bucket)
    }
    bucket.items.push(item)
  }
  return groups
}
