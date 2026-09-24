import { http } from '@cornerstone/core/api/http'
import { ApiError, type ApiResponse } from '@cornerstone/core/api/request'

/** 导入结果的一行，对应后端 UserAccountImportRowView */
export interface UserAccountImportRowView {
  rowNum: number
  username?: string | null
  displayName?: string | null
  phone?: string | null
  roleCodes?: string | null
  status?: string | null
  valid: boolean
  error?: string | null
  notice?: string | null
  /** 账号ID：预览阶段只在本机构已有同号时非空，导入后为新建或复用的账号ID */
  userId?: string | null
}

export interface UserAccountImportResult {
  total: number
  validCount: number
  errorCount: number
  createdCount: number
  skippedCount: number
  rows: UserAccountImportRowView[]
}

const BASE = '/system/user-import'

/**
 * 整批导入要逐行建号写库，core 的 10 秒全局超时会误报"请求超时"而后端其实还在跑，单独放宽。
 * 归属机构不传参：后端只认当前登录账号所在机构。
 */
async function upload<T>(path: string, file: File): Promise<T> {
  const form = new FormData()
  form.append('file', file)
  try {
    const response = await http.post<ApiResponse<T>>(`${BASE}${path}`, form, { timeout: 300000 })
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

export function previewUserAccountImport(file: File) {
  return upload<UserAccountImportResult>('/preview', file)
}

export function commitUserAccountImport(file: File) {
  return upload<UserAccountImportResult>('/commit', file)
}

/** 后端用 POI 现生成的 xlsx，浏览器只负责保存二进制 */
export async function downloadUserAccountTemplate() {
  const response = await http.get<Blob>(`${BASE}/template`, {
    responseType: 'blob',
    timeout: 60000,
  })
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = '账号批量导入模板.xlsx'
  link.click()
  URL.revokeObjectURL(url)
}
