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

function preserveLargeIds(raw: unknown) {
  if (typeof raw !== 'string' || raw.trim().length === 0) {
    return raw
  }

  const pattern = new RegExp(`"(${LARGE_ID_FIELDS.join('|')})"\\s*:\\s*(\\d{16,})`, 'g')
  return JSON.parse(raw.replace(pattern, '"$1":"$2"'))
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
