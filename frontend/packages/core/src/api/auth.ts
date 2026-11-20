import { requestGet, requestPost } from '@cornerstone/core/api/request'
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
  roles: string[]
  permissions: string[]
  passwordResetRequired?: boolean | null
  forceChangePassword?: boolean | null
  portal?: string | null
  /** 数据范围维度；null=不限制，当前管理后台为 CAMPUS */
  dataScopeKey?: string | null
  /** 数据范围白名单；null=不限制，空数组=没有任何可见校区 */
  dataScopeIds?: string[] | null
}

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
  confirmPassword: string
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
