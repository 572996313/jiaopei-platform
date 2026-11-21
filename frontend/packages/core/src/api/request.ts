import { isAxiosError, type AxiosProgressEvent, type AxiosResponse } from 'axios'
import { http } from '@cornerstone/core/api/http'

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

  constructor(
    message: string,
    options?: { code?: string | number; status?: number; path?: string }
  ) {
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

  const entries = Object.entries(params).filter(
    ([, value]) => value !== null && value !== undefined && value !== ''
  )
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

export async function requestPut<T>(
  url: string,
  body: object,
  params?: Record<string, unknown>
): Promise<T> {
  try {
    return unwrap(await http.put<ApiResponse<T>>(url, body, { params: sanitizeParams(params) }))
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

/**
 * 表单上传（multipart/form-data）。
 *
 * <p>为什么单独一条而不复用 requestPost：上传要拿 onProgress 画进度条，还要把全局 10 秒超时
 * 放宽（照片/表格在慢网下 10 秒传不完，误报超时比失败更让人困惑）。`Content-Type` 交给浏览器
 * 自己生成，手写会漏掉 boundary。</p>
 */
export async function requestUpload<T>(
  url: string,
  form: FormData,
  options: {
    onProgress?: (percent: number) => void
    /** 上传时长按 5 分钟算，与导出下载同口径 */
    timeout?: number
    signal?: AbortSignal
  } = {}
): Promise<T> {
  try {
    const response = await http.post<ApiResponse<T>>(url, form, {
      timeout: options.timeout ?? 300000,
      signal: options.signal,
      onUploadProgress: (event: AxiosProgressEvent) => {
        if (!options.onProgress || !event.total) {
          return
        }
        options.onProgress(Math.min(100, Math.round((event.loaded * 100) / event.total)))
      },
    })
    return unwrap(response)
  } catch (error) {
    throw normalizeError(error)
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}
