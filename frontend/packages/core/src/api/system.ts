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

/** 注销账号:物理删除账号行及其角色关联、注册凭证,业务档案解绑归属,不可恢复 */
export function deregisterSystemUser(id: string) {
  return requestDelete<null>(`/system/users/${id}`)
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

/** 父节点 id 是雪花 Long：必须以字符串下发，Number() 会截断 19 位精度（后端 FlexibleLongIdDeserializer 接收字符串） */
export function createSystemMenu(payload: SystemMenuCreatePayload) {
  return requestPost<SystemMenuTreeNode>('/system/menus', {
    ...payload,
    parentId: payload.parentId || null,
  }).then(normalizeMenu)
}

export function updateSystemMenu(id: string, payload: SystemMenuUpdatePayload) {
  return requestPut<SystemMenuTreeNode>(`/system/menus/${id}`, {
    ...payload,
    parentId: payload.parentId || null,
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
  /** 该角色在「预设权限」里直接勾了这个码(sys_role.permission_codes CSV) */
  fromPreset: boolean
  /** 该角色通过勾选携带此码的菜单节点获得授权 */
  fromMenuNode: boolean
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

/** 权限码下拉选项(角色弹窗「预设权限」/菜单节点权限码下拉/列表权限标签回显的公共数据源) */
export interface PermissionOption {
  /** 权限码,如 system:user:manage */
  code: string
  /** 中文名:前端会先用权限码登记表覆盖,此处为挂载节点名(按钮优先、页面其次),无节点则等于权限码 */
  label: string
  /** 挂载该权限码的启用菜单节点中文名列表(可空) */
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

// ==================== 编号规则 ====================

/**
 * 编号规则清单的一行：一条代码里登记的资源 + 它当前的生效格式。
 *
 * 清单以代码登记为准（不是以表里的行为准），所以没配置过的资源也会出现：
 * `configured=false` 时展示的是登记默认值，`previewCode` 为空。
 */
export interface CodeRuleSummary {
  id?: string | null
  resourceCode: string
  label: string
  configured: boolean
  enabled: boolean
  prefix: string
  /** 段间分隔符,空串=各段直接相连(是有语义的取值,不是"未填") */
  separator: string
  /** 日期段格式,空串=不带日期段、序号全局累加 */
  datePattern: string
  serialLength: number
  defaultPrefix: string
  defaultSeparator: string
  defaultDatePattern: string
  defaultSerialLength: number
  previewCode?: string | null
  remark?: string | null
}

export interface CodeRuleUpdatePayload {
  enabled: boolean
  prefix: string
  separator: string
  datePattern: string
  serialLength: number
  remark?: string | null
}

export function fetchCodeRules() {
  return requestGet<CodeRuleSummary[]>('/system/code-rules')
}

/** 按资源码 upsert:没有新增/删除,资源清单由代码登记决定 */
export function updateCodeRule(resourceCode: string, payload: CodeRuleUpdatePayload) {
  return requestPut<CodeRuleSummary>(
    `/system/code-rules/${encodeURIComponent(resourceCode)}`,
    payload
  )
}

/** 新增页那颗「生成」按钮:只需登录、不拦权限码,只回一个建议号不落库 */
export function nextCodeNumber(resourceCode: string) {
  return requestGet<string>('/system/code-rules/next', { resource: resourceCode })
}

/** 已启用自动编号的资源码:各新增页据此决定编号输入框要不要显示「生成」 */
export function fetchEnabledCodeResources() {
  return requestGet<string[]>('/system/code-rules/enabled')
}

// ==================== 导航编排 ====================

/**
 * 导航编排页的一行：管理端可编排的目录与页面节点（按钮不进导航，接口不返回）。
 * 这五是本页唯一可改的列——组名、图标、顺序、所属分组、是否可见；
 * 路由/权限码/类型仍归「菜单管理」。
 */
export interface NavigationNode {
  id: string
  parentId: string | null
  menuCode: string
  menuName: string
  menuType: string
  path?: string | null
  permissionCode?: string | null
  icon?: string | null
  sortNo?: number | null
  visible?: boolean | null
  /** 底座「系统设置」子树为 true：这一段在本页只读，挪动会绕过"不给非平台角色开系统页"的约束 */
  locked: boolean
  /** 多少个在职角色勾到了这个节点；目录为 0 意味着这一组的入口整块不显示且不报错 */
  grantedRoleCount: number
}

export interface NavigationNodePayload {
  id: string
  menuName: string
  icon?: string | null
  sortNo: number
  parentId?: string | null
  visible: boolean
}

export interface NavigationCatalogCreatePayload {
  menuName: string
  icon?: string
  menuCode?: string
}

export interface NavigationCatalogResult {
  id: string
  grantedRoleIdCount: number
  nodes: NavigationNode[]
}

function normalizeNavigationNode(item: NavigationNode): NavigationNode {
  return {
    ...item,
    id: String(item.id),
    parentId: item.parentId === null || item.parentId === undefined ? null : String(item.parentId),
  }
}

export function fetchNavigation() {
  return requestGet<NavigationNode[]>('/system/navigation').then((items) =>
    items.map(normalizeNavigationNode)
  )
}

/** 批量保存：id 以字符串下发（雪花 19 位，禁止 Number()；后端 FlexibleLongIdDeserializer 接收字符串） */
export function saveNavigation(nodes: NavigationNodePayload[]) {
  return requestPut<NavigationNode[]>('/system/navigation', { nodes }).then((items) =>
    items.map(normalizeNavigationNode)
  )
}

/** 新建顶层分组并同批授权给所有在职角色，返回最新导航结构 */
export function createNavigationCatalog(payload: NavigationCatalogCreatePayload) {
  return requestPost<NavigationCatalogResult>('/system/navigation/catalogs', payload).then((result) => ({
    ...result,
    id: String(result.id),
    nodes: (result.nodes || []).map(normalizeNavigationNode),
  }))
}
