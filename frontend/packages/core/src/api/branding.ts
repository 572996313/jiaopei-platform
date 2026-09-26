import { requestGet } from '@cornerstone/core/api/request'

/**
 * 品牌文案：系统名称一类的展示文字，运维在「系统参数」页改，不用重新构建前端。
 * 空串表示该条参数没配（或被删了），前端会显示带参数键名的维护提示。
 */
export interface BrandingResponse {
  name: string
  tagline: string
  mark: string
  loginCopy: string
  loginFormTitle: string
}

export function fetchBranding() {
  return requestGet<BrandingResponse>('/system/branding')
}
