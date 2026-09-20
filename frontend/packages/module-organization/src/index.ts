import { h } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import {
  onInstitutionReset,
  registerDirectory,
  registerListRenderer,
} from '@cornerstone/core/extension/module-extensions'
import { fetchCampuses } from '@cornerstone/module-organization/api/organization'
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

/** 向底座登记校区数据目录：底座的用户管理页按名字取校区下拉 */
registerDirectory('campus', async (institutionId) => {
  const campuses = await fetchCampuses(institutionId)
  return campuses.map((item) => ({
    id: item.id,
    label: item.campusName || item.campusCode || `校区 #${item.id}`,
  }))
})

/**
 * 向底座登记校区名称单元格渲染器：列表配置里 renderer=campusName 的列（用户管理的"默认校区"列）由这里绘制。
 * 取值按列自己的 prop，所以任何存校区 id 的列都能复用；
 * 名称从宿主注入的 directories.campuses 查（键名与 sys_list_config 的 optionsLoader 编码一致），
 * 查不到时按 id 兜底。底座未装配本模块时名字查不到，该列退回普通渲染（显示原始 id）。
 */
registerListRenderer({
  name: 'campusName',
  label: 'campusName(校区名称)',
  render: (row, column, context) => {
    const campusId = row[column.prop]
    if (campusId === null || campusId === undefined || campusId === '') {
      return h('span', '未设置')
    }
    const matched = context?.directories?.campuses?.find(
      (item) => String(item.value) === String(campusId)
    )
    return h('span', matched?.label ?? `校区 #${campusId}`)
  },
})

/** 机构切换/退出登录时清空本模块维护的教学范围（校区/学期） */
onInstitutionReset(() => {
  useEducationScopeStore().reset()
})
