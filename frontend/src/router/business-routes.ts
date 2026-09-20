import type { RouteRecordRaw } from 'vue-router'

/**
 * 教培业务路由：校区/时间段/教师/学员/班级/排课/考勤。
 * 阶段三将按业务模块拆到 @cornerstone/module-* 包，由模块清单导出后在此聚合注册；
 * 更换业务方向时删除本文件并挂上新业务的清单即可，底座路由不受影响。
 */
export const businessChildRoutes: RouteRecordRaw[] = [
  {
    path: 'campus',
    name: 'campus',
    component: () => import('@/views/organization/CampusManagementView.vue'),
  },
  {
    path: 'time-slots',
    name: 'time-slots',
    component: () => import('@/views/organization/TimeSlotTemplateView.vue'),
  },
  {
    path: 'teachers',
    name: 'teachers',
    component: () => import('@/views/education/TeacherManagementView.vue'),
  },
  {
    path: 'students',
    name: 'students',
    component: () => import('@/views/education/StudentManagementView.vue'),
  },
  {
    path: 'classes',
    name: 'classes',
    component: () => import('@/views/education/ClassManagementView.vue'),
  },
  {
    path: 'scheduling',
    name: 'scheduling',
    component: () => import('@/views/education/ScheduleManageView.vue'),
  },
  {
    path: 'schedule',
    name: 'schedule',
    component: () => import('@/views/education/ScheduleAttendanceView.vue'),
  },
]
