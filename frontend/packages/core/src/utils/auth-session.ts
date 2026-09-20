export interface AuthSessionSnapshot {
  accessToken: string
  tokenType: string
}

const AUTH_SESSION_STORAGE_KEY = 'cornerstone.auth.session'

function canUseStorage() {
  return typeof window !== 'undefined' && typeof window.localStorage !== 'undefined'
}

export function getStoredAuthSession(): AuthSessionSnapshot | null {
  if (!canUseStorage()) {
    return null
  }

  const raw = window.localStorage.getItem(AUTH_SESSION_STORAGE_KEY)
  if (!raw) {
    return null
  }

  try {
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
  if (!canUseStorage()) {
    return
  }

  window.localStorage.setItem(AUTH_SESSION_STORAGE_KEY, JSON.stringify(session))
}

export function clearStoredAuthSession() {
  if (!canUseStorage()) {
    return
  }

  window.localStorage.removeItem(AUTH_SESSION_STORAGE_KEY)
}

export function getStoredAuthorization() {
  const session = getStoredAuthSession()
  if (!session?.accessToken) {
    return ''
  }
  return `${session.tokenType || 'Bearer'} ${session.accessToken}`
}
