import type { RouteRecordRaw } from 'vue-router'
import { OfficeBuilding, Timer } from '@element-plus/icons-vue'
import {
  onInstitutionReset,
  registerNavItems,
  registerPermissionLabels,
} from '@cornerstone/core/extension/module-extensions'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'

/**
 * 组织主数据模块入口：对外只暴露路由清单，其余（页面/API/store）由包内相对路径引用。
 * 对底座的扩展点注册写在这里，import 本入口即完成注册，
 * 所以壳工程装配清单必须引到本文件（只挂路由不引入口 = 扩展点不生效）。
 */

/** 组织模块路由：校区/教室/学期/时间段 与 时段模板 */
export const organizationRoutes: RouteRecordRaw[] = [
  {
    path: 'campus',
    name: 'campus',
    component: () => import('@cornerstone/module-organization/views/CampusManagementView.vue'),
  },
  {
    path: 'time-slots',
    name: 'time-slots',
    component: () => import('@cornerstone/module-organization/views/TimeSlotTemplateView.vue'),
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
 * groupOrder 决定分组自上而下的顺序，同一组内按登记顺序排列。
 */
registerNavItems([
  {
    key: 'campus',
    to: '/campus',
    label: '机构与教学资源',
    title: '机构与教学资源',
    icon: OfficeBuilding,
    group: 'archive',
    groupLabel: '档案中心',
    groupOrder: 40,
    prefixes: ['/campus'],
    alwaysVisible: true,
  },
  {
    key: 'time-slots',
    to: '/time-slots',
    label: '时间段设置',
    title: '时间段设置(节次模板与网格编辑)',
    icon: Timer,
    group: 'archive',
    groupLabel: '档案中心',
    groupOrder: 40,
    prefixes: ['/time-slots'],
    alwaysVisible: true,
  },
])

/** 机构切换/退出登录时清空本模块维护的教学范围（校区/学期） */
onInstitutionReset(() => {
  useEducationScopeStore().reset()
})
