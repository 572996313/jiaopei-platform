import { computed, ref } from 'vue'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import {
  fetchCampuses,
  fetchTerms,
  type CampusSummary,
  type TermSummary,
} from '@cornerstone/module-organization/api/organization'

/**
 * 页面顶部的范围筛选（机构 / 校区 / 学期）。
 *
 * 班级不在这里拉：它曾把整机构几百个班塞进每一页的下拉，现已改成 views/components/ClassSelect.vue
 * 按关键字搜，名称回显走 composables/classLabels.ts。班级档案页要整条记录，那里自己调
 * fetchTeachingClass（按 id 取单个班级）。
 */
export function useEducationScope() {
  const institutionStore = useInstitutionStore()

  const institutions = computed(() => institutionStore.institutions)
  const institutionId = computed(() => institutionStore.currentInstitutionId)

  const campuses = ref<CampusSummary[]>([])
  const terms = ref<TermSummary[]>([])

  async function loadScope() {
    const instId = institutionStore.currentInstitutionId
    if (!instId) {
      campuses.value = []
      terms.value = []
      return
    }

    const [campusList, termList] = await Promise.all([fetchCampuses(instId), fetchTerms(instId)])

    campuses.value = campusList
    terms.value = termList
  }

  return {
    institutions,
    institutionId,
    campuses,
    terms,
    loadScope,
  }
}
