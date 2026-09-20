import { defineAsyncComponent } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import { registerPageSlot } from '@cornerstone/core/extension/module-extensions'

/**
 * 教务模块入口：对外只暴露路由清单。
 * 校区管理页里的"课程分类/课程/班型"三张卡片原本是页面内的一块，
 * 拆包时抽成组件按页面插槽注入——组织模块因此不需要反向依赖教务模块，
 * 不装配本模块时那三张卡片自动消失。
 */

/** 教务模块路由：教师/学员/班级/排课/考勤 */
export const educationRoutes: RouteRecordRaw[] = [
  {
    path: 'teachers',
    name: 'teachers',
    component: () => import('@cornerstone/module-education/views/TeacherManagementView.vue'),
  },
  {
    path: 'students',
    name: 'students',
    component: () => import('@cornerstone/module-education/views/StudentManagementView.vue'),
  },
  {
    path: 'classes',
    name: 'classes',
    component: () => import('@cornerstone/module-education/views/ClassManagementView.vue'),
  },
  {
    path: 'scheduling',
    name: 'scheduling',
    component: () => import('@cornerstone/module-education/views/ScheduleManageView.vue'),
  },
  {
    path: 'schedule',
    name: 'schedule',
    component: () => import('@cornerstone/module-education/views/ScheduleAttendanceView.vue'),
  },
]

registerPageSlot('campus-detail', {
  name: 'education-master-data',
  order: 10,
  component: defineAsyncComponent(
    () => import('@cornerstone/module-education/views/campus/EducationMasterDataCards.vue')
  ),
})
