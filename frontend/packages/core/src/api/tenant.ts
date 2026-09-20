import { requestGet } from '@cornerstone/core/api/request'

/**
 * 机构（租户）接口。机构即多租户概念的落点，后端在阶段二A 已把 Institution 从
 * organization 包迁回底座 tenant 包，前端同步归属到底座，业务模块只引用这里的类型。
 * 路由前缀仍是 /organizations/*（后端接口路径未变）。
 */

/** 机构摘要（列表/下拉用，完整资料见系统设置-机构资料页） */
export interface InstitutionSummary {
  id: string
  orgCode?: string | null
  orgName?: string | null
  shortName?: string | null
  status?: string | null
}

export function fetchInstitutions() {
  return requestGet<InstitutionSummary[]>('/organizations/institutions')
}
