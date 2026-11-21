import { requestGet, requestPost, type RecoveryOptions } from '@/utils/request'

export interface LoginResponse {
  userId: string
  orgId: string | null
  username: string
  displayName: string | null
  roles: string[]
  permissions: string[]
  tokenType: string
  accessToken: string
  forceChangePassword: boolean
  /** 所属端:admin=企业端,student/parent=学生/家长端 */
  portal?: string | null
}

export type MiniappRole = 'STUDENT' | 'PARENT'

export interface WechatLoginResponse {
  registered: boolean
  auth: LoginResponse | null
}

export interface CurrentUserResponse {
  id: string
  orgId: string | null
  institutionId: string | null
  username: string
  displayName: string | null
  roles: string[]
  permissions: string[]
  passwordResetRequired: boolean
  forceChangePassword: boolean
  /** 所属端:admin=企业端,student/parent=学生/家长端 */
  portal?: string | null
}

export interface InvitationPrecheckView {
  valid: boolean
  kind: string | null
  institutionName: string | null
  roleCode: string | null
  roleName: string | null
  expiresAt: string | null
  message: string | null
}

/** 微信小程序登录:code 换 openid,已注册直接返回令牌,未注册时 registered=false */
export function wechatLogin(code: string, options?: RecoveryOptions) {
  return requestPost<WechatLoginResponse>('/auth/wechat/login', { code }, options)
}

/** 企业端账号密码登录:复用管理后台账号,返回 portal=admin 的令牌 */
export function accountLogin(username: string, password: string) {
  return requestPost<LoginResponse>('/auth/login', { username, password })
}

/** 扫码注册入口:校验小程序码 scene 里携带的邀请码 */
export function precheckInvitation(code: string) {
  return requestGet<InvitationPrecheckView>('/auth/register/precheck', { code })
}

/** 自助注册:选择身份(学生/家长)自动建档进系统并分配角色,昵称选填;携带邀请码时机构/角色由邀请码固定 */
export function wechatRegister(code: string, role?: MiniappRole, nickname?: string, invitationCode?: string) {
  return requestPost<LoginResponse>('/auth/wechat/register', {
    code,
    role: role || undefined,
    nickname: nickname?.trim() ? nickname.trim() : undefined,
    invitationCode: invitationCode?.trim() ? invitationCode.trim() : undefined,
  })
}

/** 账号注销:删除当前微信注册的账号(含身份与绑定),注销后可重新注册 */
export function wechatDeregister() {
  return requestPost<void>('/auth/wechat/deregister', {})
}

/** 获取当前登录用户信息(带令牌调用) */
export function fetchCurrentUser() {
  return requestGet<CurrentUserResponse>('/auth/me')
}

/** 退出登录,服务端令牌立即失效 */
export function logout() {
  return requestPost<void>('/auth/logout', {})
}

/** 调起微信登录拿一次性 code,5 分钟有效且只能用一次 */
export function getWxLoginCode(): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: (result) => {
        if (result.code) {
          resolve(result.code)
        } else {
          reject(new Error('微信登录失败,请重试'))
        }
      },
      fail: () => {
        reject(new Error('微信登录调用失败,请重试'))
      },
    })
  })
}
