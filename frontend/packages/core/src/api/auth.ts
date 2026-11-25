import {
  requestDelete,
  requestGet,
  requestPost,
  requestUpload,
} from '@cornerstone/core/api/request'
import type { SystemMenuTreeNode } from '@cornerstone/core/api/system'

export interface LoginPayload {
  username: string
  password: string
}

export interface LoginResponse {
  userId: string
  orgId: string
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
  tokenType: string
  accessToken: string
  forceChangePassword?: boolean | null
}

export interface CurrentUserResponse {
  userId: string
  orgId: string
  institutionId: string
  username: string
  displayName: string
  /** 头像文件ID；null=未设置，前端按 fileId 走文件内容接口取图 */
  avatarFileId?: string | null
  roles: string[]
  permissions: string[]
  passwordResetRequired?: boolean | null
  forceChangePassword?: boolean | null
  portal?: string | null
  /** 数据范围维度；null=不限制，当前管理后台为 CAMPUS */
  dataScopeKey?: string | null
  /** 数据范围白名单；null=不限制，空数组=没有任何可见校区 */
  dataScopeIds?: string[] | null
  /** 手机号（个人信息面板展示） */
  phone?: string | null
  /** 最近登录时间 */
  lastLoginAt?: string | null
  /** 密码最近更新时间 */
  passwordUpdatedAt?: string | null
  /** 角色名称，与 roles 一一对应；角色已删时回落角色码 */
  roleNames?: string[]
}

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

export interface AvatarView {
  avatarFileId: string | null
}

export interface LoginPageResponse {
  tip: string
}

export function fetchLoginPage() {
  return requestGet<LoginPageResponse>('/auth/login-page')
}

export function login(payload: LoginPayload) {
  return requestPost<LoginResponse>('/auth/login', payload)
}

export function logout() {
  return requestPost<null>('/auth/logout', {})
}

export function fetchCurrentUser() {
  return requestGet<CurrentUserResponse>('/auth/me')
}

export function fetchCurrentMenus() {
  return requestGet<SystemMenuTreeNode[]>('/auth/current/menus')
}

export function changePassword(payload: ChangePasswordPayload) {
  return requestPost<null>('/auth/change-password', payload)
}

/** 上传本人头像：服务端一步完成校验、绑定与回写，成功后调 refreshCurrentUser 刷新横幅 */
export function uploadMyAvatar(file: File): Promise<AvatarView> {
  const form = new FormData()
  form.append('file', file)
  return requestUpload<AvatarView>('/auth/me/avatar', form)
}

/** 清除本人头像，回落到显示名称首字 */
export function deleteMyAvatar(): Promise<void> {
  return requestDelete<void>('/auth/me/avatar')
}

export interface AuthProviderView {
  code: string
  name: string
  icon: string | null
  type: string
}

export interface AccountOptionView {
  /** 不透明账号键，仅本次登录票据内有效，不暴露系统用户 ID */
  key: string
  displayName: string
  portal: string
  institutionName: string
}

export interface AccountSelectionView {
  providerCode: string
  accounts: AccountOptionView[]
}

export interface UserIdentityView {
  id: string
  providerCode: string
  providerName: string
  appType: string
  nickname: string | null
  createdAt: string
  lastLoginAt: string | null
}

/** 登录页可见的第三方渠道（后端只返回启用且已实现的） */
export function fetchLoginProviders() {
  return requestGet<AuthProviderView[]>('/auth/providers')
}

/**
 * 浏览器跳转用的授权地址。走同源 /api，开发环境由 Vite 代理、生产由 Nginx 转发。
 * bindTicket 为空表示登录，有值表示给当前账号绑定。
 */
export function oauthAuthorizeUrl(providerCode: string, bindTicket?: string) {
  const path = `/api/auth/oauth2/${encodeURIComponent(providerCode)}/authorize`
  return bindTicket ? `${path}?bindTicket=${encodeURIComponent(bindTicket)}` : path
}

/** 用回调页拿到的一次性票据换取访问令牌 */
export function exchangeOauthTicket(ticket: string) {
  return requestPost<LoginResponse>('/auth/oauth2/exchange', { ticket })
}

/** 多账号命中时读取候选账号（票据不消费，刷新页面可重试） */
export function fetchOauthSelectOptions(ticket: string) {
  return requestPost<AccountSelectionView>('/auth/oauth2/select-options', { ticket })
}

/** 选定账号并换取令牌 */
export function selectOauthAccount(ticket: string, accountKey: string) {
  return requestPost<LoginResponse>('/auth/oauth2/select', { ticket, accountKey })
}

/** 已登录账号申请绑定票据，随后跳转授权地址完成绑定 */
export function createOauthBindTicket(providerCode: string) {
  return requestPost<{ ticket: string }>(
    `/auth/oauth2/${encodeURIComponent(providerCode)}/bind-ticket`,
    {}
  )
}

export function fetchMyIdentities() {
  return requestGet<UserIdentityView[]>('/auth/me/identities')
}

export function unbindMyIdentity(identityId: string) {
  return requestDelete<void>(`/auth/me/identities/${encodeURIComponent(identityId)}`)
}
