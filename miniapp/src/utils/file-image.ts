import { API_BASE_URL } from '@/config'
import { getStoredAuthorization } from '@/utils/auth-session'

/**
 * 文件内容地址。
 *
 * <p>该接口要求登录 + 同机构，`<image>` 发的是裸 GET、带不上 Authorization，
 * 所以页面不能直接把它填进 src，必须先用 {@link fetchImageTempPath} 下载成本地临时路径。</p>
 */
export function fileContentUrl(fileId: string): string {
  return `${API_BASE_URL}/system/files/${fileId}/content`
}

/** 同一 fileId 的在会话内缓存临时路径，避免「我的」页每次 onShow 都重新下载 */
const tempPathCache = new Map<string, string>()

/** 把 fileId 换成小程序可用的本地临时路径（带令牌下载）。 */
export function fetchImageTempPath(fileId: string): Promise<string> {
  const cached = tempPathCache.get(fileId)
  if (cached) {
    return Promise.resolve(cached)
  }

  const header: Record<string, string> = {}
  const authorization = getStoredAuthorization()
  if (authorization) {
    header.Authorization = authorization
  }

  return new Promise<string>((resolve, reject) => {
    uni.downloadFile({
      url: fileContentUrl(fileId),
      header,
      success: (res) => {
        if (res.statusCode === 200 && res.tempFilePath) {
          tempPathCache.set(fileId, res.tempFilePath)
          resolve(res.tempFilePath)
          return
        }
        reject(new Error('头像加载失败'))
      },
      fail: () => reject(new Error('头像加载失败')),
    })
  })
}
