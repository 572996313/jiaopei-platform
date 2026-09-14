import { defineStore } from 'pinia'
import {
  clearStoredAuthSession,
  getStoredAuthSession,
  setStoredAuthSession,
  type AuthSessionSnapshot,
} from '@/utils/auth-session'
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
  session: AuthSessionSnapshot | null
  userInfo: CurrentUserResponse | null
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    session: getStoredAuthSession(),
    userInfo: null,
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.session?.accessToken),
  },
  actions: {
    applyLogin(auth: LoginResponse) {
      const session: AuthSessionSnapshot = {
        accessToken: auth.accessToken,
        tokenType: auth.tokenType || 'Bearer',
      }
      this.session = session
      setStoredAuthSession(session)
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

    /** 自助注册:选择学生/家长身份建档进系统 */
    async registerAccount(role: MiniappRole, nickname?: string) {
      const code = await getWxLoginCode()
      const auth = await wechatRegister(code, role, nickname)
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
        this.session = null
        this.userInfo = null
      }
    },

    /** 注销账号:删除当前微信注册的账号及绑定,令牌作废 */
    async deregister() {
      try {
        await wechatDeregister()
      } finally {
        clearStoredAuthSession()
        this.session = null
        this.userInfo = null
      }
    },
  },
})
