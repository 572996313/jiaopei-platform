import { requestGet, requestPost } from '@/utils/request'

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
}

/** 微信小程序登录:code 换 openid,已注册直接返回令牌,未注册时 registered=false */
export function wechatLogin(code: string) {
  return requestPost<WechatLoginResponse>('/auth/wechat/login', { code })
}

/** 自助注册:选择身份(学生/家长)自动建档进系统并分配角色,昵称选填 */
export function wechatRegister(code: string, role: MiniappRole, nickname?: string) {
  return requestPost<LoginResponse>('/auth/wechat/register', {
    code,
    role,
    nickname: nickname?.trim() ? nickname.trim() : undefined,
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
