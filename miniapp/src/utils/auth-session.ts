export interface AuthSessionSnapshot {
  accessToken: string
  tokenType: string
}

// 与管理后台(frontend)的 jiaopei.auth.session 区分开,两端互不干扰
const AUTH_SESSION_STORAGE_KEY = 'jiaopei.mini.auth.session'

export function getStoredAuthSession(): AuthSessionSnapshot | null {
  try {
    const raw = uni.getStorageSync(AUTH_SESSION_STORAGE_KEY) as string | undefined
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw) as Partial<AuthSessionSnapshot>
    if (!parsed.accessToken) {
      return null
    }
    return {
      accessToken: parsed.accessToken,
      tokenType: parsed.tokenType || 'Bearer',
    }
  } catch {
    return null
  }
}

export function setStoredAuthSession(session: AuthSessionSnapshot) {
  uni.setStorageSync(AUTH_SESSION_STORAGE_KEY, JSON.stringify(session))
}

export function clearStoredAuthSession() {
  uni.removeStorageSync(AUTH_SESSION_STORAGE_KEY)
}

export function getStoredAuthorization() {
  const session = getStoredAuthSession()
  if (!session?.accessToken) {
    return ''
  }
  return `${session.tokenType || 'Bearer'} ${session.accessToken}`
}
