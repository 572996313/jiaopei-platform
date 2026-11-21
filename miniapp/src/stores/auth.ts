import { defineStore } from 'pinia'
import {
  clearStoredAuthSession,
  getStoredAuthSession,
  patchStoredAuthSession,
  setStoredAuthSession,
  type AuthSessionSnapshot,
  type LoginMode,
} from '@/utils/auth-session'
import { registerSessionRecovery } from '@/utils/request'
import {
  accountLogin,
  fetchCurrentUser,
  getWxLoginCode,
  logout as logoutApi,
  wechatDeregister,
  wechatLogin,
  wechatRegister,
  type CurrentUserResponse,
  type LoginResponse,
  type MiniappRole,
} from '@/api/auth'

interface AuthState {
  userInfo: CurrentUserResponse | null
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    userInfo: null,
  }),
  actions: {
    /**
     * 登录态只认 storage 一份真相,现读现判。
     * 不做成 getter:读 storage 没有响应式依赖,computed 会把首次结果永久缓存,
     * 会话被清掉后依旧返回 true,登录页与首页就会互相 reLaunch。
     */
    isLoggedIn(): boolean {
      return Boolean(getStoredAuthSession()?.accessToken)
    },

    /** 当前端:优先用 /auth/me 的实时值,冷启动时回落到会话里记住的端 */
    currentPortal(): string {
      return this.userInfo?.portal || getStoredAuthSession()?.portal || ''
    },

    /** 企业端(管理后台账号)——学生/家长端之外的所有平台账号 */
    isEnterprise(): boolean {
      return this.currentPortal() === 'admin'
    },

    hasPermission(code: string): boolean {
      return this.userInfo?.permissions?.includes(code) ?? false
    },

    applyLogin(auth: LoginResponse, loginMode: LoginMode) {
      setStoredAuthSession(toSessionSnapshot(auth, loginMode))
    },

    /** 微信一键登录;返回 true 表示已注册并登录成功,false 表示需要先选择身份注册 */
    async loginByWechat(): Promise<boolean> {
      const code = await getWxLoginCode()
      const result = await wechatLogin(code)
      if (result.registered && result.auth) {
        this.applyLogin(result.auth, 'wechat')
        return true
      }
      return false
    },

    /** 企业端账号密码登录:复用管理后台账号,登录后立即拉一次 /auth/me 拿到角色、权限与端 */
    async loginByPassword(username: string, password: string) {
      const auth = await accountLogin(username.trim(), password)
      if (auth.forceChangePassword) {
        // 强制改密账号调任何业务接口都会被过滤器拦成 403,在登录这一步就说清楚
        throw new Error('该账号需要先修改初始密码，请先登录管理后台完成改密后再使用小程序')
      }
      this.applyLogin(auth, 'password')
      await this.refreshUserInfo()
      return auth
    },

    /** 自助注册:选择学生/家长身份建档进系统;携带邀请码时机构/角色由邀请码固定,无需选身份 */
    async registerAccount(role?: MiniappRole, nickname?: string, invitationCode?: string) {
      const code = await getWxLoginCode()
      const auth = await wechatRegister(code, role, nickname, invitationCode)
      this.applyLogin(auth, 'wechat')
    },

    async refreshUserInfo() {
      this.userInfo = await fetchCurrentUser()
      if (this.userInfo?.portal) {
        patchStoredAuthSession({ portal: this.userInfo.portal })
      }
    },

    async logout() {
      try {
        await logoutApi()
      } finally {
        clearStoredAuthSession()
        this.userInfo = null
      }
    },

    /** 注销账号:删除当前微信注册的账号及绑定,令牌作废(仅微信自助注册账号可用) */
    async deregister() {
      try {
        await wechatDeregister()
      } finally {
        clearStoredAuthSession()
        this.userInfo = null
      }
    },
  },
})

function toSessionSnapshot(auth: LoginResponse, loginMode: LoginMode): AuthSessionSnapshot {
  return {
    accessToken: auth.accessToken,
    tokenType: auth.tokenType || 'Bearer',
    loginMode,
    portal: auth.portal || undefined,
  }
}

/**
 * 令牌过期或被管理端踢下线时静默重登。小程序端微信登录本来就是 wx.login 一键、
 * 不需要用户输入任何东西,弹回登录页反而是种故障表现。
 *
 * <p>账号密码登录(企业端)绝对不能走这条路:同一个微信可能还注册过学生/家长账号,
 * 静默重登会把人换成另一个账号而界面毫无感知。密码端 401 一律回登录页重新输密码。</p>
 */
registerSessionRecovery(async () => {
  if (getStoredAuthSession()?.loginMode !== 'wechat') {
    return false
  }
  const code = await getWxLoginCode()
  const result = await wechatLogin(code, { allowSessionRecovery: false })
  if (!result.registered || !result.auth) {
    // 账号已不存在(注销或解绑),只能回登录页重新走注册
    return false
  }
  setStoredAuthSession(toSessionSnapshot(result.auth, 'wechat'))
  return true
})
