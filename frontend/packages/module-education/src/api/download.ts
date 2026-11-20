import { http } from '@cornerstone/core/api/http'
import { ApiError } from '@cornerstone/core/api/request'

/** 导出/模板文件可能要等后端现算表格，全局 10 秒超时会误报「请求超时」，单独放宽 */
const DOWNLOAD_TIMEOUT = 300000

/**
 * 下载二进制文件并触发浏览器保存。
 *
 * <p>后端失败时 `responseType:'blob'` 拿到的是装着 JSON 的 Blob，直接透出只会看到
 * 「下载失败」，所以先把 Blob 读回 JSON 里那句中文再抛。</p>
 */
export async function saveBlob(path: string, filename: string, params?: Record<string, unknown>) {
  let blob: Blob
  try {
    const response = await http.get<Blob>(path, {
      params,
      responseType: 'blob',
      timeout: DOWNLOAD_TIMEOUT,
    })
    blob = response.data
  } catch (error) {
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
