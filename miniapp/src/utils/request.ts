import { API_BASE_URL } from '@/config'
import { clearStoredAuthSession, getStoredAuthorization, savePendingRoute } from '@/utils/auth-session'

export interface ApiResponse<T> {
  code: string | number
  success: boolean
  message: string
  data: T
  timestamp?: string
  traceId?: string
  path?: string
}

export class ApiError extends Error {
  code?: string | number
  status?: number
  path?: string

  constructor(message: string, options?: { code?: string | number; status?: number; path?: string }) {
    super(message)
    this.name = 'ApiError'
    this.code = options?.code
    this.status = options?.status
    this.path = options?.path
  }
}

// 后端雪花 ID 是 19 位 Long,以 JSON 数字传输时超出 JS 安全整数范围会丢精度,
// 必须转成字符串。与管理后台 frontend/src/api/http.ts 保持同一套正则约定。
const LARGE_ID_FIELD_PATTERN = /"(id|[A-Za-z0-9_]*Ids?|[A-Za-z0-9_]*By)"\s*:\s*(\d{16,})(?=\s*[,}\]])/g
const LARGE_ID_ARRAY_FIELD_PATTERN = /"(id|[A-Za-z0-9_]*Ids?|[A-Za-z0-9_]*By)"\s*:\s*\[([^\]]*)\]/g
const LARGE_INTEGER_PATTERN = /^\d{16,}$/

function preserveLargeIds(raw: string): unknown {
  const normalized = raw
    .replace(LARGE_ID_FIELD_PATTERN, '"$1":"$2"')
    .replace(LARGE_ID_ARRAY_FIELD_PATTERN, (_match, field: string, items: string) => {
      const safeItems = items
        .split(',')
        .map((item) => {
          const value = item.trim()
          return LARGE_INTEGER_PATTERN.test(value) ? `"${value}"` : item
        })
        .join(',')
      return `"${field}":[${safeItems}]`
    })

  return JSON.parse(normalized)
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: string | Record<string, unknown> | ArrayBuffer
  /** 静默重登流程自己发出的请求置 false,避免它再收 401 时又触发一次重登 */
  allowSessionRecovery?: boolean
}

export interface RecoveryOptions {
  allowSessionRecovery?: boolean
}

type SessionRecovery = () => Promise<boolean>

// 会话恢复逻辑登记在 stores/auth(它才知道怎么重登),请求层不反向 import store/api,避免成环
let sessionRecovery: SessionRecovery | null = null
let recoveryInFlight: Promise<boolean> | null = null

export function registerSessionRecovery(handler: SessionRecovery) {
  sessionRecovery = handler
}

/** 并发请求同时 401 时共用同一次重登,不重复调 wx.login */
function recoverSessionOnce(): Promise<boolean> {
  if (!sessionRecovery) {
    return Promise.resolve(false)
  }
  if (!recoveryInFlight) {
    recoveryInFlight = sessionRecovery()
      .catch(() => false)
      .finally(() => {
        recoveryInFlight = null
      })
  }
  return recoveryInFlight
}

function goToLogin() {
  clearStoredAuthSession()
  const pages = getCurrentPages()
  const page = pages[pages.length - 1]
  // 已经在登录页就不再 reLaunch,重复跳转会把登录页销毁重建,表现就是屏幕一直闪
  if (!page?.route || page.route === 'pages/login/index') {
    return
  }
  savePendingRoute(fullPathOf(page))
  uni.reLaunch({ url: '/pages/login/index' })
}

interface PageSnapshot {
  route?: string
  options?: Record<string, unknown>
}

/**
 * 把当前页还原成可以直接 reLaunch 的地址。
 * 扫码进来的 scene 已经是微信编码过的一层(`a%3D123`),先解一次再统一编码,
 * 否则回跳时它变成 `a%253D123`,页面解一层拿到的是 `a%3D123`,认不出是公告。
 */
function fullPathOf(page: PageSnapshot): string {
  const query = Object.entries(page.options ?? {})
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(decodeParam(String(value)))}`)
    .join('&')
  return `/${page.route}${query ? '?' + query : ''}`
}

function decodeParam(value: string): string {
  try {
    return decodeURIComponent(value)
  } catch {
    // 不是合法的百分号编码就原样带回,不能因为解不开就把整页参数丢了
    return value
  }
}

function localizeNetworkError(errMsg: string | undefined): string {
  const message = errMsg ?? ''
  if (/timeout/i.test(message)) {
    return '请求超时，请稍后重试'
  }
  return '网络连接异常，请检查网络后重试'
}

function sendRequest<T>(url: string, options: RequestOptions): Promise<T> {
  const header: Record<string, string> = {}
  const authorization = getStoredAuthorization()
  if (authorization) {
    header.Authorization = authorization
  }

  return new Promise<T>((resolve, reject) => {
    uni.request({
      url: API_BASE_URL + url,
      method: options.method ?? 'GET',
      data: options.data,
      timeout: 10000,
      header,
      // 关闭自动 JSON.parse,拿到原始文本先做雪花 ID 精度保护再解析
      dataType: 'text',
      responseType: 'text',
      success: (res) => {
        if (res.statusCode === 401) {
          reject(new ApiError('登录已失效，请重新登录', { status: 401 }))
          return
        }

        let body: ApiResponse<T>
        try {
          const raw = typeof res.data === 'string' ? res.data : JSON.stringify(res.data)
          body = preserveLargeIds(raw) as ApiResponse<T>
        } catch {
          reject(new ApiError(`服务响应异常(${res.statusCode})`, { status: res.statusCode }))
          return
        }

        if (!body?.success) {
          reject(new ApiError(body?.message || '请求失败', {
            code: body?.code,
            status: res.statusCode,
            path: body?.path,
          }))
          return
        }
        resolve(body.data)
      },
      fail: (error) => {
        reject(new ApiError(localizeNetworkError(error?.errMsg)))
      },
    })
  })
}

async function request<T>(url: string, options: RequestOptions): Promise<T> {
  try {
    return await sendRequest<T>(url, options)
  } catch (error) {
    if (!isApiError(error) || error.status !== 401) {
      throw error
    }
    // 令牌被服务端判失效(过期或被踢下线):先静默重登一次并重放请求,重登不成才退回登录页
    if (options.allowSessionRecovery !== false && (await recoverSessionOnce())) {
      return request<T>(url, { ...options, allowSessionRecovery: false })
    }
    goToLogin()
    throw error
  }
}

function sanitizeParams(params?: Record<string, unknown>) {
  if (!params) {
    return undefined
  }
  const entries = Object.entries(params).filter(([, value]) => value !== null && value !== undefined && value !== '')
  return entries.length > 0 ? Object.fromEntries(entries) : undefined
}

export async function requestGet<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return request<T>(url, { method: 'GET', data: sanitizeParams(params) })
}

export async function requestPost<T>(
  url: string,
  body: Record<string, unknown>,
  options?: RecoveryOptions,
): Promise<T> {
  return request<T>(url, { method: 'POST', data: body, ...options })
}

export async function requestDelete<T>(url: string): Promise<T> {
  return request<T>(url, { method: 'DELETE' })
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}
