import { isAxiosError, type AxiosResponse } from 'axios'
import { http } from '@/api/http'

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

function unwrap<T>(response: AxiosResponse<ApiResponse<T>>): T {
  const body = response.data
  if (!body?.success) {
    throw new ApiError(body?.message || '请求失败', {
      code: body?.code,
      status: response.status,
      path: body?.path,
    })
  }
  return body.data
}

// axios 原生 error.message 是英文（如 "Network Error"、"timeout of 10000ms exceeded"），
// 不能直接透出给用户，映射为中文提示
function localizeNetworkError(error: { code?: string; message?: string }): string | null {
  if (error.code === 'ECONNABORTED' || /timeout/i.test(error.message ?? '')) {
    return '请求超时，请稍后重试'
  }
  if (/Network Error/i.test(error.message ?? '')) {
    return '网络连接异常，请检查网络后重试'
  }
  return null
}

function normalizeError(error: unknown): ApiError {
  if (isAxiosError(error)) {
    const body = error.response?.data as ApiResponse<unknown> | undefined
    if (body?.message) {
      return new ApiError(body.message, {
        code: body.code,
        status: error.response?.status,
        path: body.path,
      })
    }
    const localized = localizeNetworkError(error)
    return new ApiError(localized ?? '请求失败', {
      status: error.response?.status,
    })
  }
  if (error instanceof Error) {
    const localized = localizeNetworkError(error)
    return new ApiError(localized ?? (error.message || '请求失败'))
  }
  return new ApiError('请求失败')
}

function sanitizeParams(params?: Record<string, unknown>) {
  if (!params) {
    return undefined
  }

  const entries = Object.entries(params).filter(([, value]) => value !== null && value !== undefined && value !== '')
  return entries.length > 0 ? Object.fromEntries(entries) : undefined
}

export async function requestGet<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  try {
    return unwrap(await http.get<ApiResponse<T>>(url, { params: sanitizeParams(params) }))
  } catch (error) {
    throw normalizeError(error)
  }
}

export async function requestPost<T>(url: string, body: object): Promise<T> {
  try {
    return unwrap(await http.post<ApiResponse<T>>(url, body))
  } catch (error) {
    throw normalizeError(error)
  }
}

export async function requestPut<T>(url: string, body: object): Promise<T> {
  try {
    return unwrap(await http.put<ApiResponse<T>>(url, body))
  } catch (error) {
    throw normalizeError(error)
  }
}

export async function requestDelete<T>(url: string): Promise<T> {
  try {
    return unwrap(await http.delete<ApiResponse<T>>(url))
  } catch (error) {
    throw normalizeError(error)
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}
