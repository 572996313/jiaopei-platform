import { http } from '@cornerstone/core/api/http'

/**
 * 取小程序码图片：成功回 PNG 二进制，失败时把后端的中文结论还原成 Error。
 *
 * 失败响应在 `responseType: 'blob'` 下同样是 Blob，不先读回来就只能显示"请求失败"，
 * 操作员看不到"只有已发布的公告能生成小程序码"这类真正的原因。
 */
export async function fetchWxacode(path: string): Promise<Blob> {
  try {
    const response = await http.get<Blob>(path, { responseType: 'blob' })
    return response.data
  } catch (error) {
    throw new Error(await messageOf(error), { cause: error })
  }
}

async function messageOf(error: unknown): Promise<string> {
  const data = (error as { response?: { data?: unknown } })?.response?.data
  if (data instanceof Blob) {
    try {
      const body = JSON.parse(await data.text())
      if (typeof body?.message === 'string') {
        return body.message
      }
    } catch {
      // 不是 JSON 就落通用文案
    }
  }
  return '生成小程序码失败，请稍后重试'
}
