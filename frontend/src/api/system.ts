import { requestGet, requestPost, requestPut } from '@/api/request'

export interface SystemUserSummary {
  id: string
  institutionId: string
  defaultCampusId?: string | null
  username: string
  displayName: string
  phone?: string | null
  status?: string | null
  passwordResetRequired?: boolean | null
  forceChangePassword?: boolean | null
  lastLoginAt?: string | null
  remark?: string | null
  roleIds: string[]
  roleCodes: string[]
}

export interface SystemRoleSummary {
  id: string
  roleCode: string
  roleName: string
  permissionCodes: string[]
  status?: string | null
  remark?: string | null
}

export interface SystemMenuTreeNode {
  id: string
  parentId?: string | null
  menuCode: string
  menuName: string
  menuType: string
  path?: string | null
  component?: string | null
  permissionCode?: string | null
  icon?: string | null
  sortNo?: number | null
  visible?: boolean | null
  status?: string | null
  remark?: string | null
  children?: SystemMenuTreeNode[]
}

export interface DictTypeSummary {
  id: string
  typeCode: string
  typeName: string
  status?: string | null
  remark?: string | null
}

export interface DictItemSummary {
  id: string
  typeId: string
  itemCode: string
  itemName: string
  itemValue: string
  sortNo?: number | null
  status?: string | null
  remark?: string | null
}

export interface SystemUserCreatePayload {
  institutionId: string
  defaultCampusId?: string | null
  username: string
  displayName: string
  phone?: string
  initialPassword?: string
  status?: string
  remark?: string
}

export interface SystemUserUpdatePayload {
  institutionId: string
  defaultCampusId?: string | null
  displayName: string
  phone?: string
  status: string
  remark?: string
}

export interface ResetPasswordResponse {
  userId: string
  initialPassword: string
  passwordResetRequired?: boolean | null
}

export interface UserRoleUpdatePayload {
  roleIds: string[]
}

export interface SystemRoleCreatePayload {
  roleCode: string
  roleName: string
  permissionCodes: string[]
  status?: string
  remark?: string
}

export interface SystemRoleUpdatePayload {
  roleName: string
  permissionCodes: string[]
  status: string
  remark?: string
}

export interface RoleMenuUpdatePayload {
  menuIds: string[]
}

function normalizeUser(user: SystemUserSummary): SystemUserSummary {
  return {
    ...user,
    roleIds: (user.roleIds || []).map((item) => String(item)),
    roleCodes: user.roleCodes || [],
  }
}

function normalizeMenu(node: SystemMenuTreeNode): SystemMenuTreeNode {
  return {
    ...node,
    id: String(node.id),
    parentId: node.parentId === null || node.parentId === undefined ? null : String(node.parentId),
    children: (node.children || []).map(normalizeMenu),
  }
}

function normalizeDictType(item: DictTypeSummary): DictTypeSummary {
  return {
    ...item,
    id: String(item.id),
  }
}

function normalizeDictItem(item: DictItemSummary): DictItemSummary {
  return {
    ...item,
    id: String(item.id),
    typeId: String(item.typeId),
  }
}

export function fetchSystemUsers() {
  return requestGet<SystemUserSummary[]>('/system/users').then((items) => items.map(normalizeUser))
}

export function createSystemUser(payload: SystemUserCreatePayload) {
  return requestPost<SystemUserSummary>('/system/users', payload).then(normalizeUser)
}

export function updateSystemUser(id: string, payload: SystemUserUpdatePayload) {
  return requestPut<SystemUserSummary>(`/system/users/${id}`, payload).then(normalizeUser)
}

export function resetSystemUserPassword(id: string) {
  return requestPost<ResetPasswordResponse>(`/system/users/${id}/reset-password`, {})
}

export function updateSystemUserRoles(id: string, payload: UserRoleUpdatePayload) {
  return requestPost<SystemUserSummary>(`/system/users/${id}/roles`, payload).then(normalizeUser)
}

export function fetchSystemRoles() {
  return requestGet<SystemRoleSummary[]>('/system/roles')
}

export function createSystemRole(payload: SystemRoleCreatePayload) {
  return requestPost<SystemRoleSummary>('/system/roles', payload)
}

export function updateSystemRole(id: string, payload: SystemRoleUpdatePayload) {
  return requestPut<SystemRoleSummary>(`/system/roles/${id}`, payload)
}

export function fetchSystemMenusTree() {
  return requestGet<SystemMenuTreeNode[]>('/system/menus/tree').then((items) => items.map(normalizeMenu))
}

export function fetchSystemRoleMenuIds(roleId: string) {
  return requestGet<Array<string | number>>(`/system/roles/${roleId}/menus`).then((items) => items.map((item) => String(item)))
}

export function updateSystemRoleMenus(roleId: string, payload: RoleMenuUpdatePayload) {
  return requestPut<Array<string | number>>(`/system/roles/${roleId}/menus`, payload).then((items) => items.map((item) => String(item)))
}

export function fetchSystemDictTypes() {
  return requestGet<DictTypeSummary[]>('/system/dicts/types').then((items) => items.map(normalizeDictType))
}

export function fetchSystemDictItems(typeCode: string) {
  return requestGet<DictItemSummary[]>(`/system/dicts/types/${typeCode}/items`).then((items) => items.map(normalizeDictItem))
}
