import { requestDelete, requestGet, requestPost, requestUpload } from '@cornerstone/core/api/request'
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
