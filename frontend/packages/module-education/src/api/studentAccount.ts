import { http } from '@cornerstone/core/api/http'
import { ApiError, requestPost, type ApiResponse } from '@cornerstone/core/api/request'

/** 导入结果的一行，对应后端 StudentAccountImportRowView */
export interface StudentAccountImportRowView {
  rowNum: number
  studentNo?: string | null
  studentName?: string | null
  accountKind?: string | null
  displayName?: string | null
  username?: string | null
  phone?: string | null
  /** 账号ID，预览阶段只命中已有账号时非空，导入后为新建/复用的账号ID */
  userId?: string | null
  studentId?: string | null
  valid: boolean
  error?: string | null
  notice?: string | null
}

export interface StudentAccountImportResult {
  total: number
  validCount: number
  errorCount: number
  createdCount: number
  boundCount: number
  skippedCount: number
  rows: StudentAccountImportRowView[]
}

/** 批量发码的一行，对应后端 StudentAccountActivationView */
export interface StudentAccountActivationView {
  userId: string
  username?: string | null
  displayName?: string | null
  phone?: string | null
  code?: string | null
  expiresAt?: string | null
  message?: string | null
}

const BASE = '/education/student-account-import'

/** 整批导入要逐行建号写库，core 的 10 秒全局超时会误报"请求超时"而后端其实还在跑，单独放宽 */
async function upload<T>(path: string, file: File, institutionId: string): Promise<T> {
  const form = new FormData()
  form.append('file', file)
  try {
    const response = await http.post<ApiResponse<T>>(
      `${BASE}${path}?institutionId=${institutionId}`,
      form,
      { timeout: 300000 }
    )
    const body = response.data
    if (!body?.success) {
      throw new ApiError(body?.message || '请求失败')
    }
    return body.data
  } catch (error) {
    if (error instanceof ApiError) {
      throw error
    }
    throw new ApiError('上传失败，请检查网络或文件后重试')
  }
}

export function previewStudentAccountImport(file: File, institutionId: string) {
  return upload<StudentAccountImportResult>('/preview', file, institutionId)
}

export function commitStudentAccountImport(file: File, institutionId: string) {
  return upload<StudentAccountImportResult>('/commit', file, institutionId)
}

export function createStudentAccountActivationCodes(
  userIds: string[],
  institutionId: string,
  validDays?: number
) {
  return requestPost<StudentAccountActivationView[]>(
    `${BASE}/activation-codes?institutionId=${institutionId}`,
    { userIds, validDays }
  )
}

/** 后端用 POI 现生成的 xlsx，浏览器只负责保存二进制 */
async function downloadXlsx(path: string, filename: string, query: Record<string, string>) {
  const search = new URLSearchParams(query).toString()
  const response = await http.get<Blob>(`${BASE}${path}?${search}`, {
    responseType: 'blob',
    timeout: 60000,
  })
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

export function downloadStudentAccountTemplate() {
  return downloadXlsx('/template', '账号批量导入模板.xlsx', {})
}

/** 名册前两列已预填，业务员只补账号类型/姓名/手机号 */
export function downloadStudentAccountRoster(institutionId: string, campusId?: string | null) {
  const query: Record<string, string> = { institutionId }
  if (campusId) {
    query.campusId = campusId
  }
  return downloadXlsx('/roster', '待开账号学员名册.xlsx', query)
}
