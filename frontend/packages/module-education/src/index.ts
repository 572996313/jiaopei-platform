import { defineAsyncComponent } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import {
  registerBannerActions,
  registerInvitationActions,
  registerPermissionLabels,
} from '@cornerstone/core/extension/module-extensions'

/**
 * 教务模块入口：对外只暴露路由清单。
 * 对底座的扩展点注册写在本文件，import 即完成注册。
 */

/** 教务模块路由：教师/学员/班级/课程/排课/考勤 */
export const educationRoutes: RouteRecordRaw[] = [
  {
    path: 'teachers',
    name: 'teachers',
    component: () => import('@cornerstone/module-education/views/TeacherManagementView.vue'),
  },
  {
    path: 'courses',
    name: 'courses',
    component: () => import('@cornerstone/module-education/views/CourseManagementView.vue'),
  },
  {
    path: 'class-types',
    name: 'class-types',
    component: () => import('@cornerstone/module-education/views/ClassTypeManagementView.vue'),
  },
  {
    path: 'students',
    name: 'students',
    component: () => import('@cornerstone/module-education/views/StudentManagementView.vue'),
  },
  {
    path: 'enrollments',
    name: 'enrollments',
    component: () => import('@cornerstone/module-education/views/EnrollmentManagementView.vue'),
  },
  {
    path: 'classes',
    name: 'classes',
    component: () => import('@cornerstone/module-education/views/ClassManagementView.vue'),
  },
  {
    path: 'class-roster',
    name: 'class-roster',
    component: () => import('@cornerstone/module-education/views/ClassRosterView.vue'),
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
  {
    path: 'schedule-timetable',
    name: 'schedule-timetable',
    component: () => import('@cornerstone/module-education/views/ScheduleTimetableView.vue'),
  },
  {
    path: 'schedule-completeness',
    name: 'schedule-completeness',
    component: () => import('@cornerstone/module-education/views/ScheduleCompletenessView.vue'),
  },
  {
    path: 'announcements',
    name: 'announcements',
    component: () => import('@cornerstone/module-education/views/AnnouncementCenterView.vue'),
  },
]

/**
 * 本模块权限码的中文名与分组：只在权限选项接口不可用时作离线兜底，
 * 并给角色弹窗的权限网格提供分组名。权威中文名仍是 sys_menu 按钮节点。
 */
registerPermissionLabels([
  { code: 'education:read', label: '教务查看', group: '教务管理' },
  { code: 'education:schedule:write', label: '排课维护', group: '教务管理' },
  { code: 'education:attendance:write', label: '课次点名', group: '教务管理' },
  { code: 'education:attendance:manage', label: '考勤管理', group: '教务管理' },
  { code: 'education:class:write', label: '班级与学员维护', group: '教务管理' },
  { code: 'education:profile:write', label: '师生档案维护', group: '教务管理' },
  { code: 'education:catalog:write', label: '课程档案维护', group: '教务管理' },
  { code: 'announcement:read', label: '公告查看', group: '公告中心' },
  { code: 'announcement:write', label: '公告维护', group: '公告中心' },
  { code: 'miniapp:access', label: '小程序端访问', group: '小程序端' },
])

/**
 * 本模块的侧边栏入口：**不需要在这里登记**（理由同组织模块）。
 * 排课与上课(15)、学员与班级(139)、基础资料(130)、公告中心(25) 都是 `sys_menu` 的顶层节点，
 * 顶层组的名称、顺序、图标与组内页面一律由已授权的菜单树驱动，后台改完刷新即生效。
 * 目录节点必须授给所有在职角色这条没变：漏授权时子页会被当成孤儿提升到根，
 * 于是那一张页面自己变成一个顶层图标，而不是整组消失（见 docs/前端交付说明.md 3.4）。
 */

/**
 * 注册凭证页的行操作：邀请码可生成直达小程序注册页的太阳码。
 * 底座凭证页不认识"微信/小程序码"这类端能力，组件按扩展点注入操作列。
 */
registerInvitationActions([
  {
    key: 'education-wxacode',
    kinds: ['INVITE'],
    order: 10,
    component: defineAsyncComponent(
      () => import('@cornerstone/module-education/views/invitation/InvitationWxacodeAction.vue')
    ),
  },
])

/**
 * 顶栏右侧的全局入口：公告铃铛。它不属于任何一张页面（收件箱接口只认登录态、无权限码），
 * 底座横幅不认识"公告"这个概念，组件按 registerBannerActions 注入。
 */
registerBannerActions([
  {
    key: 'education-announcement-bell',
    order: 10,
    component: defineAsyncComponent(
      () => import('@cornerstone/module-education/views/announcement/AnnouncementBell.vue')
    ),
  },
])
