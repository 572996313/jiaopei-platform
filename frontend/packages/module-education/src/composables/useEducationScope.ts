import { computed, ref } from 'vue'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import { fetchTerms, type TermSummary } from '@cornerstone/module-organization/api/organization'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'

/**
 * 页面顶部的范围筛选（机构 / 校区 / 学期）。
 *
 * 校区列表与 campusId 同源，都读 useEducationScopeStore：横幅那枚校区芯片取过一次就够，
 * 页面的弹窗校区下拉与名称回显不再逐页请求（新增校区后由校区与学期页强制刷新）。
 * 学期仍按页拉：它条数小、且改完学期的人就在同一屏，不值得为它加一层缓存。
 * 班级不在这里拉：它曾把整机构几百个班塞进每一页的下拉，现已改成 views/components/ClassSelect.vue
 * 按关键字搜，名称回显走 composables/classLabels.ts。班级档案页要整条记录，那里自己调
 * fetchTeachingClass（按 id 取单个班级）。
 */
export function useEducationScope() {
  const institutionStore = useInstitutionStore()
  const scopeStore = useEducationScopeStore()

  const institutions = computed(() => institutionStore.institutions)
  const institutionId = computed(() => institutionStore.currentInstitutionId)

  const campuses = computed(() => scopeStore.campuses)
  /** 只读：改范围一律走 scopeStore.setCampus()，它会把手上开着的页签一起重挂 */
  const campusId = computed(() => scopeStore.campusId)
  const prefillCampusId = computed(() => scopeStore.prefillCampusId)
  const terms = ref<TermSummary[]>([])

  async function loadScope() {
    const instId = institutionStore.currentInstitutionId
    if (!instId) {
      terms.value = []
      return
    }
    const [, termList] = await Promise.all([scopeStore.loadCampuses(instId), fetchTerms(instId)])
    terms.value = termList
  }

  return {
    institutions,
    institutionId,
    campuses,
    campusId,
    prefillCampusId,
    terms,
    loadScope,
  }
}
