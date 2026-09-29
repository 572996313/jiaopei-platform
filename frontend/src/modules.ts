import type { RouteRecordRaw } from 'vue-router'
import { Calendar } from '@element-plus/icons-vue'
import {
  registerBranding,
  registerNavItems,
} from '@cornerstone/core/extension/module-extensions'
// 顺序有含义：模块入口的扩展点注册是 import 副作用，先 import 的先登记
// （权限标签分组、侧边栏分组在界面上按这个顺序排列，组织在前）。
import { organizationRoutes } from '@cornerstone/module-organization'
import { educationRoutes } from '@cornerstone/module-education'

/**
 * 壳工程的项目装配清单：本项目挂了哪些业务模块、工作台用哪个首页。
 * 换业务方向时改这一个文件（+ 删对应业务包与 package.json 依赖），底座包不动。
 *
 * 两点必须同时成立，少一个就是"装了但没挂上"：
 * 1. import 模块入口 —— 模块对底座的扩展点注册（侧边栏导航/权限标签/页面插槽）是 import 副作用；
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

/**
 * 壳工程自己的导航项与品牌文案。
 * 侧边栏的顶层组现在一律由 `sys_menu` 的顶层节点驱动（组名/组序/图标/组内页面都在库里），
 * **这里只剩下一种入口：没有菜单节点可挂的落地页**。工作台是跨模块聚合页、不对应任何权限码，
 * 所以只能在这里登记，并用 `groupOrder: 10` 钉在最前（业务目录的 sort_no 都大于它）。
 * 新页面要进导航请在「系统设置 → 菜单管理」建节点，不要回到这里加登记项。
 */
registerNavItems([
  {
    key: 'dashboard',
    to: '/dashboard',
    label: '今日教务',
    title: '今日教务工作台',
    icon: Calendar,
    group: 'workbench',
    groupLabel: '工作台',
    groupOrder: 10,
    prefixes: ['/dashboard'],
    alwaysVisible: true,
  },
])

/**
 * 登录页能力卡。系统名称、副标题、介绍语已经改由系统参数 brand.* 下发（V68 种入，运维在
 * 「系统管理 → 系统参数」改），这里是结构化数组、单值输入框塞不下，才留在构建期。
 */
registerBranding({
  loginFeatures: [
    { title: '组织与校区', text: '机构、校区、教室、学期、时间段统一管理。' },
    { title: '排课与执行', text: '围绕线下班课模式搭建稳定的教务中台。' },
  ],
})
