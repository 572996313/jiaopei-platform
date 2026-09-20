import type { RouteRecordRaw } from 'vue-router'
import { educationRoutes } from '@cornerstone/module-education'
import { organizationRoutes } from '@cornerstone/module-organization'

/**
 * 壳工程的项目装配清单：本项目挂了哪些业务模块、工作台用哪个首页。
 * 换业务方向时改这一个文件（+ 删对应业务包与 package.json 依赖），底座包不动。
 *
 * 两点必须同时成立，少一个就是"装了但没挂上"：
 * 1. import 模块入口 —— 模块对底座的扩展点注册（数据目录/页面插槽）是 import 副作用；
 * 2. 路由清单进 projectChildRoutes —— 页面才可访问。
 * 只留底座时把下面模块清里的条目与 import 一并去掉，pnpm build 产物即不含业务代码。
 */
const installedModuleRoutes: RouteRecordRaw[][] = [organizationRoutes, educationRoutes]

/** 项目自有页面（跨模块的工作台等）+ 各业务模块的路由清单 */
export const projectChildRoutes: RouteRecordRaw[] = [
  {
    path: 'dashboard',
    name: 'dashboard',
    component: () => import('@/views/dashboard/DashboardView.vue'),
  },
  ...installedModuleRoutes.flat(),
]
