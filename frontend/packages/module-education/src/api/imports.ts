import { http } from '@cornerstone/core/api/http'
import { ApiError, type ApiResponse } from '@cornerstone/core/api/request'
import { saveBlob } from './download'

/** 导入结果的一行，对应后端 XlsxImportRowView */
export interface XlsxImportRowView {
  rowNum: number
  valid: boolean
  error?: string | null
  notice?: string | null
  /** 列 key → 展示值；字典列存的是规范值，由列配置的 dictType 翻译成中文 */
  cells: Record<string, string | null | undefined>
}

export interface XlsxImportResult {
  total: number
  validCount: number
  errorCount: number
  createdCount: number
  skippedCount: number
  rows: XlsxImportRowView[]
}

/** 整批导入要逐行写库，全局 10 秒超时会误报"请求超时"而后端其实还在跑，单独放宽 */
const UPLOAD_TIMEOUT = 300000

async function uploadXlsx<T>(url: string, file: File): Promise<T> {
  const form = new FormData()
  form.append('file', file)
  try {
    const response = await http.post<ApiResponse<T>>(url, form, { timeout: UPLOAD_TIMEOUT })
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

// ------------------------------------------------------------------ 学员

const STUDENT_BASE = '/education/student-import'

export function downloadStudentImportTemplate() {
  return saveBlob(`${STUDENT_BASE}/template`, '学员档案批量导入模板.xlsx')
}

export function previewStudentImport(file: File) {
  return uploadXlsx<XlsxImportResult>(`${STUDENT_BASE}/preview`, file)
}

export function commitStudentImport(file: File) {
  return uploadXlsx<XlsxImportResult>(`${STUDENT_BASE}/commit`, file)
}

// ------------------------------------------------------------------ 班级

const CLASS_BASE = '/education/class-import'

export function downloadClassImportTemplate() {
  return saveBlob(`${CLASS_BASE}/template`, '班级档案批量导入模板.xlsx')
}

export function previewClassImport(file: File) {
  return uploadXlsx<XlsxImportResult>(`${CLASS_BASE}/preview`, file)
}

export function commitClassImport(file: File) {
  return uploadXlsx<XlsxImportResult>(`${CLASS_BASE}/commit`, file)
}

// ------------------------------------------------------------------ 分班

const CLASS_STUDENT_BASE = '/education/class-student-import'

export function downloadClassStudentImportTemplate() {
  return saveBlob(`${CLASS_STUDENT_BASE}/template`, '分班批量导入模板.xlsx')
}

export function previewClassStudentImport(file: File) {
  return uploadXlsx<XlsxImportResult>(`${CLASS_STUDENT_BASE}/preview`, file)
}

export function commitClassStudentImport(file: File) {
  return uploadXlsx<XlsxImportResult>(`${CLASS_STUDENT_BASE}/commit`, file)
}
