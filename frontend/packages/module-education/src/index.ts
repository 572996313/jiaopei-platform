import { defineAsyncComponent } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import { AlarmClock, Bell, School } from '@element-plus/icons-vue'
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
 * 一个 `sys_menu` 目录节点 = 侧边栏一组（V92 定稿的形态），组名、组内页面与顺序都取菜单树，
 * 后台改菜单名/排序即生效；登记方只负责给出图标与组顺序号。
 * 教师/课程/班型三张档案页不在这里登记——它们与组织模块的校区、教室、节次同属「基础资料」
 * 一组，由组织模块登记那一份目录（菜单树驱动子项，跨模块不影响显隐；关 organization 必须
 * 同关 education，所以不存在"装了教务没装组织却少入口"的状态）。
 */
registerNavItems([
  {
    key: 'scheduling',
    to: '/scheduling',
    label: '排课与上课',
    title: '排课与上课',
    icon: AlarmClock,
    group: 'run',
    groupLabel: '排课与上课',
    groupOrder: 20,
    prefixes: ['/scheduling', '/schedule', '/schedule-completeness'],
    // 目录与子项来自 sys_menu(V37 排课管理、V59 排课完成度、V92 改名课表与点名/课时完成度)
    menuCatalogCode: 'education_schedule_center',
  },
  {
    key: 'student-class',
    to: '/students',
    label: '学员与班级',
    title: '学员与班级',
    icon: School,
    group: 'student-class',
    groupLabel: '学员与班级',
    groupOrder: 30,
    prefixes: ['/students', '/enrollments', '/classes', '/class-roster'],
    // 从建档到进班一条链收进一个目录(V92 新建节点 139):学员档案 → 报名 → 班级 → 分班
    // 目录节点必须授给所有在职角色，漏授权时这四张页面的入口整块不显示(同 V81 的 130)
    menuCatalogCode: 'education_student_class',
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
