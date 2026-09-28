import type { RouteRecordRaw } from 'vue-router'
import { defineAsyncComponent } from 'vue'
import { OfficeBuilding } from '@element-plus/icons-vue'
import {
  onInstitutionReset,
  registerBannerChips,
  registerNavItems,
  registerPermissionLabels,
} from '@cornerstone/core/extension/module-extensions'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'

/**
 * 组织主数据模块入口：对外只暴露路由清单，其余（页面/API/store）由包内相对路径引用。
 * 对底座的扩展点注册写在这里，import 本入口即完成注册，
 * 所以壳工程装配清单必须引到本文件（只挂路由不引入口 = 扩展点不生效）。
 */

/** 组织模块路由：校区与学期 / 校区默认账号 / 教室 / 时段模板 / 节次明细 */
export const organizationRoutes: RouteRecordRaw[] = [
  {
    path: 'campus',
    name: 'campus',
    component: () => import('@cornerstone/module-organization/views/CampusManagementView.vue'),
  },
  {
    path: 'campus-accounts',
    name: 'campus-accounts',
    component: () =>
      import('@cornerstone/module-organization/views/CampusAccountManagementView.vue'),
  },
  {
    path: 'classrooms',
    name: 'classrooms',
    component: () => import('@cornerstone/module-organization/views/ClassroomManagementView.vue'),
  },
  {
    path: 'time-slots',
    name: 'time-slots',
    component: () => import('@cornerstone/module-organization/views/TimeSlotTemplateView.vue'),
  },
  {
    path: 'time-slot-list',
    name: 'time-slot-list',
    component: () => import('@cornerstone/module-organization/views/TimeSlotListView.vue'),
  },
]

/**
 * 本模块权限码的中文名与分组：只在权限选项接口不可用时作离线兜底，
 * 并给角色弹窗的权限网格提供分组名。权威中文名仍是 sys_menu 按钮节点。
 */
registerPermissionLabels([
  { code: 'organization:read', label: '组织查看', group: '组织档案' },
  { code: 'organization:write', label: '组织维护', group: '组织档案' },
])

/**
 * 本模块的侧边栏入口：底座只内置「系统设置」，业务导航一律靠登记。
 * 一个 `sys_menu` 目录节点 = 侧边栏一组，组内页面的名称与顺序都由菜单树决定（V92 定稿形态）。
 */
registerNavItems([
  {
    key: 'base-data',
    to: '/campus',
    label: '基础资料',
    title: '基础资料',
    icon: OfficeBuilding,
    group: 'base-data',
    groupLabel: '基础资料',
    groupOrder: 40,
    // 这一组是"开班前配好、之后不常进"的档案：本模块的校区/教室/节次五页 + 教务的教师/课程/班型三页
    prefixes: [
      '/campus',
      '/campus-accounts',
      '/classrooms',
      '/time-slots',
      '/time-slot-list',
      '/teachers',
      '/courses',
      '/class-types',
    ],
    // 目录与八张页面的名称、顺序、图标都在 sys_menu(V81/V82/V86 五页 + V92 收编教师/课程/班型)
    menuCatalogCode: 'organization_teaching_resources',
  },
])

/** 机构切换/退出登录时清空本模块维护的教学范围（校区/学期） */
onInstitutionReset(() => {
  useEducationScopeStore().reset()
})

/**
 * 横幅上的校区范围芯片：底座不认识"校区"这个概念，组件按 registerBannerChips 注入机构芯片右侧。
 * 它同时是 campusId 的初值来源（进系统/换机构时 initScope 落账号默认校区），
 * 所以业务模块没装配时页面一律按"全部校区"跑，不会拿不到范围。
 */
registerBannerChips([
  {
    key: 'organization-campus-scope',
    order: 100,
    component: defineAsyncComponent(
      () => import('@cornerstone/module-organization/views/components/CampusScopeChip.vue')
    ),
  },
])
