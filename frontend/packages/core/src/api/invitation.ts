import { requestGet, requestPost, requestPut } from '@cornerstone/core/api/request'
import type { PageParams, PageResult } from '@cornerstone/core/api/common'

/** 注册凭证(邀请码/激活码)视图,对应后端 InvitationView */
export interface InvitationSummary {
  id: string
  code: string
  kind: string
  institutionId: string
  institutionName?: string | null
  userId?: string | null
  username?: string | null
  roleCode?: string | null
  roleName?: string | null
  maxUses?: number | null
  usedCount: number
  expiresAt?: string | null
  status: string
  remark?: string | null
  createdAt?: string | null
}

export interface InvitationPageParams extends PageParams {
  keyword?: string
  kind?: string
  status?: string
  institutionId?: string
}

export interface InvitationCreatePayload {
  institutionId: string
  roleCode: string
  maxUses?: number
  validDays?: number
  remark?: string
}

export interface ActivationCodeCreatePayload {
  userId: string
  validDays?: number
}

/** 凭证预检结果(公开接口),对应后端 InvitationPrecheckView */
export interface InvitationPrecheck {
  valid: boolean
  kind?: string | null
  institutionName?: string | null
  roleCode?: string | null
  roleName?: string | null
  expiresAt?: string | null
  message?: string | null
}

export interface InvitationRegisterPayload {
  code: string
  username: string
  displayName: string
  phone?: string
  password: string
}

export interface AccountActivatePayload {
  code: string
  password: string
}

export interface AuthSessionResponse {
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

export function fetchInvitationPage(params: InvitationPageParams) {
  return requestGet<PageResult<InvitationSummary>>('/system/invitations/page', { ...params }).then(
    (result) => ({
      ...result,
      records: (result.records || []).map((item) => ({ ...item, id: String(item.id) })),
    })
  )
}

export function createInvite(payload: InvitationCreatePayload) {
  return requestPost<InvitationSummary>('/system/invitations/invite', payload)
}

export function createActivationCode(payload: ActivationCodeCreatePayload) {
  return requestPost<InvitationSummary>('/system/invitations/activation', payload)
}

export function disableInvitation(id: string) {
  return requestPut<null>(`/system/invitations/${id}/disable`, {})
}

export function precheckInvitation(code: string) {
  return requestGet<InvitationPrecheck>('/auth/register/precheck', { code })
}

export function registerByInvite(payload: InvitationRegisterPayload) {
  return requestPost<AuthSessionResponse>('/auth/register/invitation', payload)
}

export function activateAccount(payload: AccountActivatePayload) {
  return requestPost<AuthSessionResponse>('/auth/register/activation', payload)
}
