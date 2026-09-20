import type { RouteRecordRaw } from 'vue-router'
import {
  onInstitutionReset,
  registerDirectory,
} from '@cornerstone/core/extension/module-extensions'
import { fetchCampuses } from '@/api/organization'
import { useEducationScopeStore } from '@/stores/educationScope'
import { businessChildRoutes } from './router/business-routes'

/**
 * 壳工程的项目装配清单：本教培项目挂了哪些业务模块、工作台用哪个首页、
 * 底座扩展点由哪个模块来填。换业务方向时改这一个文件（+ 删对应业务代码），
 * 底座包不动。
 *
 * 这里的 register/on 调用是 import 副作用：必须被路由装配引到才生效，
 * 所以 router/index.ts 顶层 import 本文件，不要改成懒加载。
 */

/** 组织模块：向底座登记校区目录（用户管理页的校区下拉与名称回显） */
registerDirectory('campus', async (institutionId) => {
  const campuses = await fetchCampuses(institutionId)
  return campuses.map((item) => ({
    id: item.id,
    label: item.campusName || item.campusCode || `校区 #${item.id}`,
  }))
})

/** 教务模块：机构切换/退出登录时清空跨页共享的教学范围（校区/学期） */
onInstitutionReset(() => {
  useEducationScopeStore().reset()
})

/** 项目自有页面（跨模块的工作台等）+ 各业务模块的路由清单 */
export const projectChildRoutes: RouteRecordRaw[] = [
  {
    path: 'dashboard',
    name: 'dashboard',
    component: () => import('@/views/dashboard/DashboardView.vue'),
  },
  ...businessChildRoutes,
]
