import { computed, ref } from 'vue'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import {
  fetchCampuses,
  fetchTerms,
  type CampusSummary,
  type TermSummary,
} from '@cornerstone/module-organization/api/organization'
import {
  fetchStudents,
  fetchTeachingClasses,
  type StudentSummary,
  type TeachingClassSummary,
} from '@cornerstone/module-education/api/education'

export function useEducationScope() {
  const institutionStore = useInstitutionStore()

  const institutions = computed(() => institutionStore.institutions)
  const institutionId = computed(() => institutionStore.currentInstitutionId)

  const campuses = ref<CampusSummary[]>([])
  const terms = ref<TermSummary[]>([])
  const classes = ref<TeachingClassSummary[]>([])
  const students = ref<StudentSummary[]>([])

  async function loadScope(campusId?: string | null, termId?: string | null) {
    const instId = institutionStore.currentInstitutionId
    if (!instId) {
      campuses.value = []
      terms.value = []
      classes.value = []
      students.value = []
      return
    }

    const [campusList, termList, classList, studentList] = await Promise.all([
      fetchCampuses(instId),
      fetchTerms(instId),
      fetchTeachingClasses({ institutionId: instId, campusId, termId }),
      fetchStudents({ institutionId: instId, campusId }),
    ])

    campuses.value = campusList
    terms.value = termList
    classes.value = classList
    students.value = studentList
  }

  return {
    institutions,
    institutionId,
    campuses,
    terms,
    classes,
    students,
    loadScope,
  }
}
