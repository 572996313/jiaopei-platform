import { fetchBlob, saveBlob } from '@cornerstone/core/api/blob'
import type { PageParams, PageResult } from '@cornerstone/core/api/common'
import {
  requestDelete,
  requestGet,
  requestPost,
  requestUpload,
} from '@cornerstone/core/api/request'

/**
 * 通用文件能力（底座）。
 *
 * <p>契约只有一条：**业务表存 fileId，不存 objectKey / bucket / URL**。上传由后端生成对象键，
 * 前端与业务方都拿不到、也不需要知道文件存在哪里；取内容一律走
 * `GET /api/system/files/{fileId}/content`，由后端校验机构后回源。</p>
 *
 * <p>大 ID：fileId / institutionId / bizId / uploadedBy 在响应里已被 http 层转成字符串，
 * 类型按 string 写；sizeBytes 不是 ID，保留 number。</p>
 */

const FILE_BASE = '/system/files'

export interface FileView {
  /** 文件ID（业务表保存这个值） */
  fileId: string
  fileName: string
  extension?: string | null
  contentType?: string | null
  sizeBytes: number
  bizType?: string | null
  bizId?: string | null
  createdAt?: string | null
}

export interface FileListView extends FileView {
  institutionId: string
  uploadedBy?: string | null
}

export interface FilePageParams extends PageParams {
  /** 文件名模糊匹配 */
  keyword?: string
  /** 业务类型码精确匹配 */
  bizType?: string
  bizId?: string
  /** ISO 时间，如 2026-11-21T00:00:00 */
  createdFrom?: string
  createdTo?: string
}

export interface UploadFileOptions {
  bizType?: string
  bizId?: string
  /** 0-100；传了才会接 onUploadProgress */
  onProgress?: (percent: number) => void
  signal?: AbortSignal
}

/** 上传单个文件，返回元数据（其中 fileId 写进业务表） */
export function uploadFile(file: File, options: UploadFileOptions = {}): Promise<FileView> {
  const form = new FormData()
  form.append('file', file)
  if (options.bizType) {
    form.append('bizType', options.bizType)
  }
  if (options.bizId) {
    form.append('bizId', options.bizId)
  }
  return requestUpload<FileView>(FILE_BASE, form, {
    onProgress: options.onProgress,
    signal: options.signal,
  })
}

/** 取文件元数据 */
export function fetchFile(fileId: string): Promise<FileView> {
  return requestGet<FileView>(`${FILE_BASE}/${fileId}`)
}

/** 取文件字节；页面里要显示图片时经 use-file-url 包成 blob URL */
export function fetchFileBlob(fileId: string): Promise<Blob> {
  return fetchBlob(`${FILE_BASE}/${fileId}/content`)
}

/** 下载文件（浏览器另存为），文件名用元数据里的原始名 */
export function saveFile(fileId: string, fileName: string): Promise<void> {
  return saveBlob(`${FILE_BASE}/${fileId}/content`, fileName, { download: true })
}

/** 删除文件；已被业务数据引用（bizId 非空）的文件后端会拒绝 */
export function deleteFile(fileId: string): Promise<void> {
  return requestDelete<void>(`${FILE_BASE}/${fileId}`)
}

/** 文件管理页分页 */
export function pageFiles(params: FilePageParams): Promise<PageResult<FileListView>> {
  return requestGet<PageResult<FileListView>>(`${FILE_BASE}/page`, { ...params })
}

/** 立即清理过期孤儿文件，返回本次清理条数 */
export function cleanupOrphanFiles(): Promise<number> {
  return requestPost<number>(`${FILE_BASE}/cleanup-orphans`, {})
}
