import { http } from '@cornerstone/core/api/http'
import { ApiError, requestGet, requestPost, type ApiResponse } from '@cornerstone/core/api/request'
import type { TeacherPageParams } from '@cornerstone/module-education/api/education'

/** 导入结果的一行，对应后端 TeacherImportRowView */
export interface TeacherImportRowView {
  rowNum: number
  teacherCode?: string | null
  teacherName?: string | null
  phone?: string | null
  campusCode?: string | null
  gender?: string | null
  employmentType?: string | null
  hiredAt?: string | null
  status?: string | null
  valid: boolean
  error?: string | null
  notice?: string | null
  /** 档案ID：预览阶段只在本机构已有同编码时非空，导入后为新建或复用的档案ID */
  teacherId?: string | null
}

export interface TeacherImportResult {
  total: number
  validCount: number
  errorCount: number
  createdCount: number
  skippedCount: number
  rows: TeacherImportRowView[]
}

/** 匹配候选的一行，对应后端 TeacherAccountLinkCandidateView；一位教师可能占多行（同号命中多个账号） */
export interface TeacherLinkCandidateView {
  teacherId?: string | null
  teacherCode?: string | null
  teacherName?: string | null
  phone?: string | null
  userId?: string | null
  username?: string | null
  displayName?: string | null
  accountStatus?: string | null
  nameMatched?: boolean | null
  actionable: boolean
  notice?: string | null
}

export interface TeacherLinkCandidateResult {
  unlinkedTotal: number
  matchableTotal: number
  candidateCount: number
  rows: TeacherLinkCandidateView[]
}

const IMPORT_BASE = '/education/teacher-import'
const TEACHER_BASE = '/education/teachers'

/** 整批导入要逐行建档写库，全局 10 秒超时会误报"请求超时"而后端其实还在跑，单独放宽 */
const UPLOAD_TIMEOUT = 300000

async function upload<T>(path: string, file: File): Promise<T> {
  const form = new FormData()
  form.append('file', file)
  try {
    const response = await http.post<ApiResponse<T>>(`${IMPORT_BASE}${path}`, form, {
      timeout: UPLOAD_TIMEOUT,
    })
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

export function previewTeacherImport(file: File) {
  return upload<TeacherImportResult>('/preview', file)
}

export function commitTeacherImport(file: File) {
  return upload<TeacherImportResult>('/commit', file)
}

export function fetchTeacherLinkCandidates() {
  return requestGet<TeacherLinkCandidateResult>(`${TEACHER_BASE}/link-candidates`)
}

/** 只提交「档案-账号」配对：归属机构与手机号比对都在后端做，前端不传可篡改的机构参数 */
export function linkTeacherAccounts(links: { teacherId: string; userId: string }[]) {
  return requestPost<{ linkedCount: number }>(`${TEACHER_BASE}/link-accounts`, { links })
}

export function downloadTeacherTemplate() {
  return saveBlob(`${IMPORT_BASE}/template`, '教师档案批量导入模板.xlsx')
}

/** 导出沿用列表的筛选条件（分页参数不参与：导出一次给全量） */
export function exportTeachers(
  filters: Pick<TeacherPageParams, 'institutionId' | 'campusId' | 'keyword' | 'status'>
) {
  return saveBlob(`${TEACHER_BASE}/export`, '教师档案.xlsx', { ...filters })
}

async function saveBlob(path: string, filename: string, params?: Record<string, unknown>) {
  let blob: Blob
  try {
    const response = await http.get<Blob>(path, {
      params,
      responseType: 'blob',
      timeout: UPLOAD_TIMEOUT,
    })
    blob = response.data
  } catch (error) {
    // blob 请求里后端的失败响应也是 Blob，不先读出来就只能给一句通用文案
    throw new ApiError(await messageOfBlob(error))
  }
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

async function messageOfBlob(error: unknown): Promise<string> {
  const data = (error as { response?: { data?: unknown } })?.response?.data
  if (data instanceof Blob) {
    try {
      const json = JSON.parse(await data.text())
      if (typeof json?.message === 'string') {
        return json.message
      }
    } catch {
      // 不是 JSON 就落通用文案
    }
  }
  return '下载失败，请稍后重试'
}
