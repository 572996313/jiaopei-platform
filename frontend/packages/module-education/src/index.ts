import { defineAsyncComponent } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import { AlarmClock, Avatar, Bell, Files, Notebook, Reading, User } from '@element-plus/icons-vue'
import {
  registerBannerActions,
  registerInvitationActions,
  registerNavItems,
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
 * 本模块的侧边栏入口：底座只内置「系统设置」，业务导航一律靠登记。
 * groupOrder 决定分组自上而下的顺序，同一组内按登记顺序排列；
 * 「档案中心」这一组与组织模块共用，先登记的模块决定组名与顺序号。
 */
registerNavItems([
  {
    key: 'scheduling',
    to: '/scheduling',
    label: '排课中心',
    title: '排课中心',
    icon: AlarmClock,
    group: 'daily',
    groupLabel: '日常教务',
    groupOrder: 20,
    prefixes: ['/scheduling', '/schedule', '/schedule-completeness'],
    // 目录与子项来自 sys_menu(V37 排课管理/执行与课表、V59 排课完成度),名称/顺序/图标可后台配置
    menuCatalogCode: 'education_schedule_center',
  },
  {
    key: 'students',
    to: '/students',
    label: '学员档案',
    title: '学员档案',
    icon: User,
    group: 'daily',
    groupLabel: '日常教务',
    groupOrder: 20,
    prefixes: ['/students'],
    // 入口显隐与名称由 sys_menu 节点驱动(V84 节点):报名拆到独立页后这一页只剩学员本身
    menuCode: 'education_students',
  },
  {
    key: 'enrollments',
    to: '/enrollments',
    label: '学员报名',
    title: '学员报名',
    icon: Notebook,
    group: 'daily',
    groupLabel: '日常教务',
    groupOrder: 20,
    prefixes: ['/enrollments'],
    // 原「报名与入班」页收窄成只管报名(V84):入班在「班级名册」，档案在这一页
    menuCode: 'education_enrollments',
  },
  {
    key: 'classes',
    to: '/classes',
    label: '班级档案',
    title: '班级档案',
    icon: Reading,
    group: 'class',
    groupLabel: '班级与教学',
    groupOrder: 30,
    prefixes: ['/classes'],
    menuCode: 'education_classes',
  },
  {
    key: 'class-roster',
    to: '/class-roster',
    label: '班级名册',
    title: '班级名册',
    icon: Notebook,
    group: 'class',
    groupLabel: '班级与教学',
    groupOrder: 30,
    prefixes: ['/class-roster'],
    // 入班、退班与家长联系人在这一页(V83 节点):与档案页同一份读码，写码仍走按钮节点
    menuCode: 'education_class_roster',
  },
  {
    key: 'courses',
    to: '/courses',
    label: '课程档案',
    title: '课程档案',
    icon: Files,
    group: 'archive',
    groupLabel: '档案中心',
    groupOrder: 40,
    prefixes: ['/courses'],
    // 原「校区与学期」页里的三张教务卡片独立成页(V85):课程为主表，分类与班型封顶并排
    menuCode: 'education_courses',
  },
  {
    key: 'teachers',
    to: '/teachers',
    label: '教师档案',
    title: '教师档案',
    icon: Avatar,
    group: 'archive',
    groupLabel: '档案中心',
    groupOrder: 40,
    prefixes: ['/teachers'],
    menuCode: 'education_teachers',
  },
  {
    key: 'announcements',
    to: '/announcements',
    label: '公告中心',
    title: '公告中心',
    icon: Bell,
    group: 'notice',
    groupLabel: '消息通知',
    groupOrder: 50,
    prefixes: ['/announcements'],
    menuCode: 'announcement_center',
  },
])

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
