/**
 * 底座 ↔ 业务模块的扩展点注册表。
 *
 * 底座页面会需要业务维度的数据（用户管理要校区下拉、切机构要清掉业务页的筛选范围），
 * 但底座包不允许 import 业务包，否则换行业时拆不干净。做法与后端的"事件 + SPI"一致：
 * 业务模块在自己入口调用 register*／on* 登记实现，底座只按名字查表调用；
 * 模块未装配（清单里没挂）时查不到实现，底座按空数据降级而不是报错。
 *
 * 注册发生在模块入口被 import 时，所以壳工程的模块清单一定要引到模块包，
 * 只挂路由不挂清单 = 扩展点没注册。
 */

/** 通用数据目录项：id 用于取值，label 用于展示与 id→名称 回显 */
export interface DirectoryOption {
  id: string
  label: string
}

/** 目录加载器：按机构过滤，返回该机构下的全部条目 */
export type DirectoryLoader = (institutionId?: string | null) => Promise<DirectoryOption[]>

const directoryLoaders = new Map<string, DirectoryLoader>()

/** 业务模块登记自己的数据目录（如 'campus' 校区、'term' 学期） */
export function registerDirectory(name: string, loader: DirectoryLoader) {
  directoryLoaders.set(name, loader)
}

/** 底座取数据目录；未注册（模块未装配）时返回空数组 */
export async function loadDirectory(
  name: string,
  institutionId?: string | null
): Promise<DirectoryOption[]> {
  const loader = directoryLoaders.get(name)
  if (!loader) {
    return []
  }
  return loader(institutionId)
}

const institutionResetHandlers: Array<() => void> = []

/** 业务模块登记"机构切换/退出登录后要清空的范围状态" */
export function onInstitutionReset(handler: () => void) {
  institutionResetHandlers.push(handler)
}

/** 底座 institution store 在切换/重置时广播，不直接依赖任何业务 store */
export function notifyInstitutionReset() {
  for (const handler of institutionResetHandlers) {
    handler()
  }
}
