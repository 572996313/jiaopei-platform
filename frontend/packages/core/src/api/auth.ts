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
  defaultCampusId?: string | null
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
  passwordResetRequired?: boolean | null
  forceChangePassword?: boolean | null
}

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
  confirmPassword: string
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
