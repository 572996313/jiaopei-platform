export type LoginMode = 'wechat' | 'password'

export interface AuthSessionSnapshot {
  accessToken: string
  tokenType: string
  /** 登录方式:微信一键登录 / 账号密码登录(企业端) */
  loginMode: LoginMode
  /** 所属端:admin=企业端,student/parent=学生/家长端;由服务端 /auth/me 下发 */
  portal?: string
}

// 与管理后台(frontend)的 cornerstone.auth.session 区分开,两端互不干扰
const AUTH_SESSION_STORAGE_KEY = 'cornerstone.mini.auth.session'

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
      // 老会话没有这个字段,当时只有微信登录,按 wechat 处理
      loginMode: parsed.loginMode === 'password' ? 'password' : 'wechat',
      portal: typeof parsed.portal === 'string' ? parsed.portal : undefined,
    }
  } catch {
    return null
  }
}

export function setStoredAuthSession(session: AuthSessionSnapshot) {
  uni.setStorageSync(AUTH_SESSION_STORAGE_KEY, JSON.stringify(session))
}

/** 刷新用户信息后把端写回会话,下次冷启动不必等 /auth/me 才知道该进哪套首页 */
export function patchStoredAuthSession(patch: Partial<Omit<AuthSessionSnapshot, 'accessToken'>>) {
  const session = getStoredAuthSession()
  if (!session) {
    return
  }
  setStoredAuthSession({ ...session, ...patch })
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

const PENDING_ROUTE_STORAGE_KEY = 'cornerstone.mini.pending-route'

/**
 * 登录前那一步想去的页面。
 *
 * <p>扫公告码/点分享卡片进来的人多半还没登录，被弹回登录页就等于这一条码白发了——
 * 所以跳登录页之前先把目标地址记下来，登录或注册成功后回到原页。</p>
 */
export function savePendingRoute(url: string) {
  uni.setStorageSync(PENDING_ROUTE_STORAGE_KEY, url)
}

/** 取一次即清：它是"上一次没登录进去的那页"，只对紧接着的那次登录有效，留到下次登录就成了误导 */
export function takePendingRoute(): string {
  const url = String(uni.getStorageSync(PENDING_ROUTE_STORAGE_KEY) || '')
  uni.removeStorageSync(PENDING_ROUTE_STORAGE_KEY)
  return url
}
