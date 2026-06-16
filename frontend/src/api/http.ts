import axios from 'axios'
import { getStoredAuthorization } from '@/utils/auth-session'

const LARGE_ID_FIELDS = [
  'id',
  'userId',
  'orgId',
  'institutionId',
  'campusId',
  'termId',
  'classId',
  'classroomId',
  'timeSlotId',
  'studentId',
  'teacherId',
  'createdBy',
  'updatedBy',
]

const LARGE_ID_ARRAY_FIELDS = [
  'teacherIds',
]

function preserveLargeIds(raw: unknown) {
  if (typeof raw !== 'string' || raw.trim().length === 0) {
    return raw
  }

  const singleIdPattern = new RegExp(`"(${LARGE_ID_FIELDS.join('|')})"\\s*:\\s*(\\d{16,})`, 'g')
  const arrayIdPattern = new RegExp(`"(${LARGE_ID_ARRAY_FIELDS.join('|')})"\\s*:\\s*\\[([^\\]]*)\\]`, 'g')
  const normalized = raw
    .replace(singleIdPattern, '"$1":"$2"')
    .replace(arrayIdPattern, (_match, field: string, items: string) => {
      const safeItems = items
        .split(',')
        .map((item) => {
          const value = item.trim()
          return /^\d{16,}$/.test(value) ? `"${value}"` : item
        })
        .join(',')
      return `"${field}":[${safeItems}]`
    })

  return JSON.parse(normalized)
}

export const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
  transformResponse: [
    (data) => preserveLargeIds(data),
  ],
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
  (error) => Promise.reject(error),
)
