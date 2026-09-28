import { requestGet } from '@/utils/request'

/**
 * 品牌文案:与 PC 登录页读的是同一个接口、同一组系统参数 brand.*。
 * 空串表示该条参数没配,前端显示带参数键名的维护提示,不回落构建期写死的名字。
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
