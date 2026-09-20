import { defineStore } from 'pinia'
import {
  changePassword as changePasswordRequest,
  fetchCurrentUser,
  fetchCurrentMenus,
  login as loginRequest,
  logout as logoutRequest,
  type ChangePasswordPayload,
  type CurrentUserResponse,
  type LoginPayload,
} from '@cornerstone/core/api/auth'
import type { SystemMenuTreeNode } from '@cornerstone/core/api/system'
import {
  clearStoredAuthSession,
  getStoredAuthSession,
  setStoredAuthSession,
} from '@cornerstone/core/utils/auth-session'

const storedSession = getStoredAuthSession()

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: storedSession?.accessToken || '',
    tokenType: storedSession?.tokenType || 'Bearer',
    currentUser: null as CurrentUserResponse | null,
    currentMenus: [] as SystemMenuTreeNode[],
    initialized: false,
    bootstrapping: false,
  }),

  getters: {
    hasStoredToken: (state) => Boolean(state.accessToken),
    isAuthenticated: (state) => Boolean(state.accessToken && state.currentUser),
    mustChangePassword: (state) => Boolean(state.currentUser?.forceChangePassword),
    displayName: (state) =>
      state.currentUser?.displayName || state.currentUser?.username || '未登录',
    permissions: (state) => state.currentUser?.permissions || [],
  },

  actions: {
    setSession(accessToken: string, tokenType = 'Bearer') {
      this.accessToken = accessToken
      this.tokenType = tokenType || 'Bearer'
      setStoredAuthSession({
        accessToken: this.accessToken,
        tokenType: this.tokenType,
      })
    },

    clearSession() {
      this.accessToken = ''
      this.tokenType = 'Bearer'
      this.currentUser = null
      this.currentMenus = []
      this.initialized = true
      clearStoredAuthSession()
    },

    async loadCurrentMenus() {
      if (!this.accessToken) {
        this.currentMenus = []
        return []
      }

      try {
        this.currentMenus = await fetchCurrentMenus()
      } catch {
        this.currentMenus = []
      }
      return this.currentMenus
    },

    async bootstrap() {
      if (this.bootstrapping) {
        return this.currentUser
      }
      if (!this.accessToken) {
        this.initialized = true
        return null
      }

      this.bootstrapping = true
      try {
        this.currentUser = await fetchCurrentUser()
        await this.loadCurrentMenus()
        this.initialized = true
        return this.currentUser
      } catch {
        this.clearSession()
        return null
      } finally {
        this.bootstrapping = false
      }
    },

    async login(payload: LoginPayload) {
      const result = await loginRequest(payload)
      this.setSession(result.accessToken, result.tokenType || 'Bearer')
      await this.bootstrap()
      return this.currentUser
    },

    async refreshCurrentUser() {
      if (!this.accessToken) {
        this.currentUser = null
        this.currentMenus = []
        return null
      }
      this.currentUser = await fetchCurrentUser()
      await this.loadCurrentMenus()
      this.initialized = true
      return this.currentUser
    },

    async changePassword(payload: ChangePasswordPayload) {
      await changePasswordRequest(payload)
      if (this.currentUser) {
        this.currentUser = {
          ...this.currentUser,
          passwordResetRequired: false,
          forceChangePassword: false,
        }
      }
      try {
        await this.refreshCurrentUser()
      } catch {
        return this.currentUser
      }
      return this.currentUser
    },

    async logout() {
      try {
        if (this.accessToken) {
          await logoutRequest()
        }
      } finally {
        this.clearSession()
      }
    },

    hasPermission(permissionCode: string) {
      return this.permissions.includes(permissionCode)
    },

    hasMenuAccess(signals: string[]) {
      if (!signals.length) {
        return false
      }

      const matchNode = (items: SystemMenuTreeNode[]): boolean => {
        return items.some((item) => {
          if (signals.includes(item.menuCode) || signals.includes(item.path || '')) {
            return true
          }
          return item.children ? matchNode(item.children) : false
        })
      }

      return matchNode(this.currentMenus)
    },
  },
})
