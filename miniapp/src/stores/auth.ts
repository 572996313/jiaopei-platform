import { defineStore } from 'pinia'
import {
  clearStoredAuthSession,
  getStoredAuthSession,
  setStoredAuthSession,
  type AuthSessionSnapshot,
} from '@/utils/auth-session'
import { registerSessionRecovery } from '@/utils/request'
import {
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

    applyLogin(auth: LoginResponse) {
      setStoredAuthSession(toSessionSnapshot(auth))
    },

    /** 微信一键登录;返回 true 表示已注册并登录成功,false 表示需要先选择身份注册 */
    async loginByWechat(): Promise<boolean> {
      const code = await getWxLoginCode()
      const result = await wechatLogin(code)
      if (result.registered && result.auth) {
        this.applyLogin(result.auth)
        return true
      }
      return false
    },

    /** 自助注册:选择学生/家长身份建档进系统;携带邀请码时机构/角色由邀请码固定,无需选身份 */
    async registerAccount(role?: MiniappRole, nickname?: string, invitationCode?: string) {
      const code = await getWxLoginCode()
      const auth = await wechatRegister(code, role, nickname, invitationCode)
      this.applyLogin(auth)
    },

    async refreshUserInfo() {
      this.userInfo = await fetchCurrentUser()
    },

    async logout() {
      try {
        await logoutApi()
      } finally {
        clearStoredAuthSession()
        this.userInfo = null
      }
    },

    /** 注销账号:删除当前微信注册的账号及绑定,令牌作废 */
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

function toSessionSnapshot(auth: LoginResponse): AuthSessionSnapshot {
  return {
    accessToken: auth.accessToken,
    tokenType: auth.tokenType || 'Bearer',
  }
}

/**
 * 令牌过期或被管理端踢下线时静默重登。小程序端登录本来就是 wx.login 一键、
 * 不需要用户输入任何东西,弹回登录页反而是种故障表现。
 * 重登请求自己关掉会话恢复,否则它再收 401 会等在同一个 promise 上。
 */
registerSessionRecovery(async () => {
  const code = await getWxLoginCode()
  const result = await wechatLogin(code, { allowSessionRecovery: false })
  if (!result.registered || !result.auth) {
    // 账号已不存在(注销或解绑),只能回登录页重新走注册
    return false
  }
  setStoredAuthSession(toSessionSnapshot(result.auth))
  return true
})
