import axios from 'axios'
import { getStoredAuthorization } from '@/utils/auth-session'

// 后端雪花 ID 是 19 位 Long，以 JSON 数字传输时超出 JS 安全整数范围会丢精度，
// 必须转成字符串。按字段名约定（id / *Id / *Ids / *By）匹配，避免白名单漏掉新字段。
const LARGE_ID_FIELD_PATTERN =
  /"(id|[A-Za-z0-9_]*Ids?|[A-Za-z0-9_]*By)"\s*:\s*(\d{16,})(?=\s*[,}\]])/g
const LARGE_ID_ARRAY_FIELD_PATTERN = /"(id|[A-Za-z0-9_]*Ids?|[A-Za-z0-9_]*By)"\s*:\s*\[([^\]]*)\]/g
const LARGE_INTEGER_PATTERN = /^\d{16,}$/

function preserveLargeIds(raw: unknown) {
  if (typeof raw !== 'string' || raw.trim().length === 0) {
    return raw
  }

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

export const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
  transformResponse: [(data) => preserveLargeIds(data)],
})

http.interceptors.request.use((config) => {
  const authorization = getStoredAuthorization() || import.meta.env.VITE_API_AUTHORIZATION?.trim()
  if (authorization) {
    config.headers = config.headers ?? {}
    ;(config.headers as Record<string, string>).Authorization = authorization
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => Promise.reject(error)
)
