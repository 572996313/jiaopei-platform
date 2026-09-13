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
  wechatBind,
  wechatLogin,
  type CurrentUserResponse,
  type LoginResponse,
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

    /** 微信一键登录;返回 true 表示已绑定并登录成功,false 表示需要先绑定账号 */
    async loginByWechat(): Promise<boolean> {
      const code = await getWxLoginCode()
      const result = await wechatLogin(code)
      if (result.bound && result.auth) {
        this.applyLogin(result.auth)
        return true
      }
      return false
    },

    /** 用系统账号密码绑定当前微信 */
    async bindAccount(username: string, password: string) {
      const code = await getWxLoginCode()
      const auth = await wechatBind(code, username, password)
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
  },
})
