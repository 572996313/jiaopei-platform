import { requestDelete, requestGet, requestPost, requestPut } from '@cornerstone/core/api/request'
import type { PageParams, PageResult } from '@cornerstone/core/api/common'

export interface SystemUserSummary {
  id: string
  institutionId: string
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

export interface SystemMenuCreatePayload {
  parentId?: string | null
  menuCode: string
  menuName: string
  menuType: string
  path?: string
  component?: string
  permissionCode?: string
  icon?: string
  sortNo?: number
  visible?: boolean
  status?: string
  remark?: string
}

export interface SystemMenuUpdatePayload {
  parentId?: string | null
  menuName: string
  menuType: string
  path?: string
  component?: string
  permissionCode?: string
  icon?: string
  sortNo?: number
  visible?: boolean
  status?: string
  remark?: string
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

export interface DictOptionGroupSummary {
  typeCode: string
  typeName: string
  items: DictItemSummary[]
}

export interface DictTypeCreatePayload {
  typeCode: string
  typeName: string
  status?: string
  remark?: string
}

export interface DictTypeUpdatePayload {
  typeName: string
  status: string
  remark?: string
}

export interface DictItemCreatePayload {
  typeId: string
  itemCode: string
  itemName: string
  itemValue: string
  sortNo?: number
  status?: string
  remark?: string
}

export interface DictItemUpdatePayload {
  itemName: string
  itemValue: string
  sortNo?: number
  status: string
  remark?: string
}

export interface CurrentInstitutionProfile {
  id: string
  orgCode?: string | null
  name?: string | null
  shortName?: string | null
  contactName?: string | null
  contactPhone?: string | null
  address?: string | null
  status?: string | null
  remark?: string | null
}

export interface CurrentInstitutionUpdatePayload {
  name: string
  shortName?: string
  contactName?: string
  contactPhone?: string
  address?: string
  status?: string
  remark?: string
}

export interface SystemUserCreatePayload {
  institutionId: string
  username: string
  displayName: string
  phone?: string
  initialPassword?: string
  status?: string
  remark?: string
}

export interface SystemUserUpdatePayload {
  institutionId: string
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

export interface SystemConfigSummary {
  id: string
  configKey: string
  configValue: string
  valueType?: string | null
  configGroup?: string | null
  displayName?: string | null
  description?: string | null
  defaultValue?: string | null
  isSystem?: boolean | null
  sortOrder?: number | null
  status?: string | null
}

export interface SystemConfigCreatePayload {
  configKey: string
  configValue: string
  valueType: string
  configGroup: string
  displayName: string
  description?: string
  defaultValue?: string
  isSystem: boolean
  sortOrder: number
  status: string
}

export interface SystemConfigUpdatePayload {
  configValue: string
  displayName: string
  description?: string
  sortOrder: number
  status: string
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

function normalizeCurrentInstitution(item: CurrentInstitutionProfile): CurrentInstitutionProfile {
  return {
    ...item,
    id: String(item.id),
  }
}

export function fetchCurrentInstitution() {
  return requestGet<CurrentInstitutionProfile>('/system/institution/current').then(
    normalizeCurrentInstitution
  )
}

export function updateCurrentInstitution(payload: CurrentInstitutionUpdatePayload) {
  return requestPut<CurrentInstitutionProfile>('/system/institution/current', payload).then(
    normalizeCurrentInstitution
  )
}

export function fetchSystemUsers() {
  return requestGet<SystemUserSummary[]>('/system/users').then((items) => items.map(normalizeUser))
}

export interface SystemUserPageParams extends PageParams {
  keyword?: string
  status?: string
  institutionId?: string
}

export function fetchSystemUserPage(params: SystemUserPageParams) {
  return requestGet<PageResult<SystemUserSummary>>('/system/users/page', { ...params }).then(
    (result) => ({
      ...result,
      records: (result.records || []).map(normalizeUser),
    })
  )
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

export interface SystemRolePageParams extends PageParams {
  keyword?: string
  status?: string
}

export function fetchSystemRolePage(params: SystemRolePageParams) {
  return requestGet<PageResult<SystemRoleSummary>>('/system/roles/page', { ...params })
}

export function createSystemRole(payload: SystemRoleCreatePayload) {
  return requestPost<SystemRoleSummary>('/system/roles', payload)
}

export function updateSystemRole(id: string, payload: SystemRoleUpdatePayload) {
  return requestPut<SystemRoleSummary>(`/system/roles/${id}`, payload)
}

export function fetchSystemMenusTree() {
  return requestGet<SystemMenuTreeNode[]>('/system/menus/tree').then((items) =>
    items.map(normalizeMenu)
  )
}

export function createSystemMenu(payload: SystemMenuCreatePayload) {
  return requestPost<SystemMenuTreeNode>('/system/menus', {
    ...payload,
    parentId: payload.parentId ? Number(payload.parentId) : null,
  }).then(normalizeMenu)
}

export function updateSystemMenu(id: string, payload: SystemMenuUpdatePayload) {
  return requestPut<SystemMenuTreeNode>(`/system/menus/${id}`, {
    ...payload,
    parentId: payload.parentId ? Number(payload.parentId) : null,
  }).then(normalizeMenu)
}

export function deleteSystemMenu(id: string) {
  return requestDelete<void>(`/system/menus/${id}`)
}

export function fetchSystemRoleMenuIds(roleId: string) {
  return requestGet<Array<string | number>>(`/system/roles/${roleId}/menus`).then((items) =>
    items.map((item) => String(item))
  )
}

export function updateSystemRoleMenus(roleId: string, payload: RoleMenuUpdatePayload) {
  return requestPut<Array<string | number>>(`/system/roles/${roleId}/menus`, payload).then(
    (items) => items.map((item) => String(item))
  )
}

function normalizeSystemConfig(item: SystemConfigSummary): SystemConfigSummary {
  return {
    ...item,
    id: String(item.id),
  }
}

export function fetchSystemConfigs() {
  return requestGet<SystemConfigSummary[]>('/system/config').then((items) =>
    items.map(normalizeSystemConfig)
  )
}

export interface SystemConfigPageParams extends PageParams {
  keyword?: string
  configGroup?: string
  status?: string
}

export function fetchSystemConfigPage(params: SystemConfigPageParams) {
  return requestGet<PageResult<SystemConfigSummary>>('/system/config/page', { ...params }).then(
    (result) => ({
      ...result,
      records: (result.records || []).map(normalizeSystemConfig),
    })
  )
}

export function createSystemConfig(payload: SystemConfigCreatePayload) {
  return requestPost<SystemConfigSummary>('/system/config', payload).then(normalizeSystemConfig)
}

export function updateSystemConfig(id: string, payload: SystemConfigUpdatePayload) {
  return requestPut<SystemConfigSummary>(`/system/config/${id}`, payload).then(
    normalizeSystemConfig
  )
}

export function deleteSystemConfig(id: string) {
  return requestDelete<void>(`/system/config/${id}`)
}

export function fetchSystemDictTypes() {
  return requestGet<DictTypeSummary[]>('/system/dicts/types').then((items) =>
    items.map(normalizeDictType)
  )
}

export function fetchSystemDictItems(typeCode: string) {
  return requestGet<DictItemSummary[]>(`/system/dicts/types/${typeCode}/items`).then((items) =>
    items.map(normalizeDictItem)
  )
}

export function fetchDictOptions(typeCodes?: string[]) {
  const query =
    typeCodes && typeCodes.length > 0
      ? `?typeCodes=${typeCodes.map((code) => encodeURIComponent(code)).join(',')}`
      : ''
  return requestGet<DictOptionGroupSummary[]>(`/system/dicts/options${query}`).then((groups) =>
    groups.map((group) => ({
      ...group,
      items: (group.items || []).map(normalizeDictItem),
    }))
  )
}

export function createSystemDictType(payload: DictTypeCreatePayload) {
  return requestPost<DictTypeSummary>('/system/dicts/types', payload).then(normalizeDictType)
}

export function updateSystemDictType(id: string, payload: DictTypeUpdatePayload) {
  return requestPut<DictTypeSummary>(`/system/dicts/types/${id}`, payload).then(normalizeDictType)
}

export function deleteSystemDictType(id: string) {
  return requestDelete<void>(`/system/dicts/types/${id}`)
}

export function createSystemDictItem(payload: DictItemCreatePayload) {
  return requestPost<DictItemSummary>('/system/dicts/items', payload).then(normalizeDictItem)
}

export function updateSystemDictItem(id: string, payload: DictItemUpdatePayload) {
  return requestPut<DictItemSummary>(`/system/dicts/items/${id}`, payload).then(normalizeDictItem)
}

export function deleteSystemDictItem(id: string) {
  return requestDelete<void>(`/system/dicts/items/${id}`)
}

export interface ListConfigSummary {
  id: string
  resourceCode: string
  resourceName: string
  endpoint: string
  pageSize: number
  columnsJson: string
  queryJson: string
  actionsJson?: string | null
  formJson?: string | null
  /** 页面壳配置 JSON(阶段 A 扩展:后端加列后返回,缺省为 null) */
  pageJson?: string | null
  /** 弹窗壳配置 JSON(阶段 A 扩展:后端加列后返回,缺省为 null) */
  dialogJson?: string | null
  status?: string | null
  remark?: string | null
}

export interface ListConfigSavePayload {
  resourceName: string
  endpoint: string
  pageSize: number
  columnsJson: string
  queryJson: string
  actionsJson?: string | null
  formJson?: string | null
  /** 页面壳配置 JSON(缺省 null=未配置,页面按默认壳渲染) */
  pageJson?: string | null
  /** 弹窗壳配置 JSON(缺省 null=未配置,弹窗按默认壳渲染) */
  dialogJson?: string | null
  status: string
  remark?: string
}

function normalizeListConfig(item: ListConfigSummary): ListConfigSummary {
  return {
    ...item,
    id: String(item.id),
  }
}

export function fetchListConfigByCode(resourceCode: string) {
  return requestGet<ListConfigSummary>(
    `/system/list-configs/code/${encodeURIComponent(resourceCode)}`
  ).then(normalizeListConfig)
}

export function fetchListConfigs() {
  return requestGet<ListConfigSummary[]>('/system/list-configs').then((items) =>
    items.map(normalizeListConfig)
  )
}

export function createListConfig(payload: ListConfigSavePayload & { resourceCode: string }) {
  return requestPost<ListConfigSummary>('/system/list-configs', payload).then(normalizeListConfig)
}

export function updateListConfig(id: string, payload: ListConfigSavePayload) {
  return requestPut<ListConfigSummary>(`/system/list-configs/${id}`, payload).then(
    normalizeListConfig
  )
}

export function deleteListConfig(id: string) {
  return requestDelete<void>(`/system/list-configs/${id}`)
}

export interface PermissionEndpointInfo {
  method: string
  path: string
  summary?: string | null
}

export interface PermissionRoleRef {
  roleId: string
  roleCode: string
  roleName: string
}

export interface PermissionMenuNodeRef {
  menuId: string
  menuName: string
  menuCode: string
}

export interface PermissionConfigButtonRef {
  resourceCode: string
  resourceName: string
  action: string
  label: string
  position: string
}

export interface PermissionUsage {
  code: string
  endpoints: PermissionEndpointInfo[]
  roles: PermissionRoleRef[]
  menuNodes: PermissionMenuNodeRef[]
  configButtons: PermissionConfigButtonRef[]
  dangling: boolean
}

export interface UnregisteredPermission {
  code: string
  roles: PermissionRoleRef[]
  menuNodes: PermissionMenuNodeRef[]
  configButtons: PermissionConfigButtonRef[]
}

export interface PermissionOverview {
  permissions: PermissionUsage[]
  unregistered: UnregisteredPermission[]
}

export function fetchPermissionOverview() {
  return requestGet<PermissionOverview>('/system/permission-overview')
}

/** 权限码下拉选项(角色弹窗「预设权限」/菜单按钮权限码下拉/列表权限标签回显的公共数据源) */
export interface PermissionOption {
  /** 权限码,如 system:user:manage */
  code: string
  /** 中文名:优先按钮节点 menuName,缺失回退为权限码本身 */
  label: string
  /** 挂载该权限码的启用按钮节点中文名列表(可空) */
  menuNames: string[]
}

export function fetchPermissionOptions() {
  return requestGet<PermissionOption[]>('/system/permission-options')
}

// ==================== 操作留痕日志 ====================

export interface OperLogSummary {
  id: string
  traceId?: string | null
  userId?: string | null
  username?: string | null
  orgId?: string | null
  portal?: string | null
  operation?: string | null
  actionType?: string | null
  method?: string | null
  path?: string | null
  clientIp?: string | null
  userAgent?: string | null
  params?: string | null
  errorMsg?: string | null
  success?: boolean | null
  durationMs?: number | null
  createdAt?: string | null
}

export interface OperLogPageParams extends PageParams {
  username?: string
  actionType?: string
  portal?: string
  operation?: string
  success?: boolean
  beginTime?: string
  endTime?: string
}

function normalizeOperLog(item: OperLogSummary): OperLogSummary {
  return {
    ...item,
    id: String(item.id),
    userId: item.userId === null || item.userId === undefined ? null : String(item.userId),
    orgId: item.orgId === null || item.orgId === undefined ? null : String(item.orgId),
  }
}

export function fetchOperLogs(params: OperLogPageParams) {
  return requestGet<PageResult<OperLogSummary>>('/system/operlogs', { ...params }).then(
    (result) => ({
      ...result,
      records: (result.records || []).map(normalizeOperLog),
    })
  )
}

// ==================== 数据源配置 ====================

/** 数据源配置项(管理视图) */
export interface DataSourceSummary {
  id: string
  sourceCode: string
  sourceName: string
  endpoint?: string | null
  method?: string | null
  valueKey?: string | null
  labelKey?: string | null
  paramDefs?: string | null
  sourceType: string
  dictType?: string | null
  description?: string | null
  status?: string | null
  remark?: string | null
}

/** 数据源定义(消费者视图:前端按编码取定义后,调 endpoint 并组装 {value,label}) */
export interface DataSourceDefinition {
  sourceCode: string
  sourceName: string
  endpoint?: string | null
  method?: string | null
  valueKey?: string | null
  labelKey?: string | null
  paramDefs?: string | null
  sourceType: string
  dictType?: string | null
}

export interface DataSourceCreatePayload {
  sourceCode: string
  sourceName: string
  endpoint?: string
  method?: string
  valueKey?: string
  labelKey?: string
  paramDefs?: string
  sourceType: string
  dictType?: string
  description?: string
  status: string
  remark?: string
}

export interface DataSourceUpdatePayload {
  sourceName: string
  endpoint?: string
  method?: string
  valueKey?: string
  labelKey?: string
  paramDefs?: string
  sourceType: string
  dictType?: string
  description?: string
  status: string
  remark?: string
}

function normalizeDataSource(item: DataSourceSummary): DataSourceSummary {
  return {
    ...item,
    id: String(item.id),
  }
}

export function fetchDataSources() {
  return requestGet<DataSourceSummary[]>('/system/data-sources').then((items) =>
    items.map(normalizeDataSource)
  )
}

export function createDataSource(payload: DataSourceCreatePayload) {
  return requestPost<DataSourceSummary>('/system/data-sources', payload).then(normalizeDataSource)
}

export function updateDataSource(id: string, payload: DataSourceUpdatePayload) {
  return requestPut<DataSourceSummary>(`/system/data-sources/${id}`, payload).then(
    normalizeDataSource
  )
}

export function deleteDataSource(id: string) {
  return requestDelete<void>(`/system/data-sources/${id}`)
}

/** 按编码批量取启用的数据源定义(通用组件消费,仅需登录) */
export function fetchDataSourceDefinitions(codes: string[]) {
  const unique = Array.from(new Set(codes.filter((code) => code && code.trim()))).join(',')
  if (!unique) {
    return Promise.resolve({} as Record<string, DataSourceDefinition>)
  }
  return requestGet<Record<string, DataSourceDefinition>>(`/system/data-sources/definitions`, {
    codes: unique,
  })
}
