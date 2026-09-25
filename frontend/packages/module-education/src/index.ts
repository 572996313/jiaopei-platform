import { defineAsyncComponent } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import { AlarmClock, Avatar, Reading, User } from '@element-plus/icons-vue'
import {
  registerInvitationActions,
  registerNavItems,
  registerPermissionLabels,
  registerPageSlot,
} from '@cornerstone/core/extension/module-extensions'

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
  {
    path: 'schedule-completeness',
    name: 'schedule-completeness',
    component: () => import('@cornerstone/module-education/views/ScheduleCompletenessView.vue'),
  },
]

/**
 * 本模块权限码的中文名与分组：只在权限选项接口不可用时作离线兜底，
 * 并给角色弹窗的权限网格提供分组名。权威中文名仍是 sys_menu 按钮节点。
 */
registerPermissionLabels([
  { code: 'education:read', label: '教务查看', group: '教务管理' },
  { code: 'education:write', label: '教务维护', group: '教务管理' },
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
    label: '报名与入班',
    title: '报名与入班',
    icon: User,
    group: 'daily',
    groupLabel: '日常教务',
    groupOrder: 20,
    prefixes: ['/students'],
    // 入口显隐与名称由 sys_menu 节点驱动(V61):角色没勾这个页面就看不到入口
    menuCode: 'education_enrollments',
  },
  {
    key: 'classes',
    to: '/classes',
    label: '班级学员',
    title: '班级学员',
    icon: Reading,
    group: 'class',
    groupLabel: '班级与教学',
    groupOrder: 30,
    prefixes: ['/classes'],
    menuCode: 'education_classes',
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
])

registerPageSlot('campus-detail', {
  name: 'education-master-data',
  order: 10,
  component: defineAsyncComponent(
    () => import('@cornerstone/module-education/views/campus/EducationMasterDataCards.vue')
  ),
})

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
