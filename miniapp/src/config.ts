// 后端 API 基地址
// 开发:直连本机后端,微信开发者工具需勾选"不校验合法域名"
// 生产:改为正式域名(必须 HTTPS,并在小程序后台配置 request 合法域名),
//      可通过构建时环境变量 VITE_API_BASE_URL 覆盖
export const API_BASE_URL =
  (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '') ||
  'http://127.0.0.1:9080/api'
