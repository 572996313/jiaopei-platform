import { ref } from 'vue'
import {
  fetchCampuses,
  fetchInstitutions,
  fetchTerms,
  type CampusSummary,
  type InstitutionSummary,
  type TermSummary,
} from '@/api/organization'
import { fetchStudents, fetchTeachingClasses, type StudentSummary, type TeachingClassSummary } from '@/api/education'

export function useEducationScope() {
  const institutions = ref<InstitutionSummary[]>([])
  const campuses = ref<CampusSummary[]>([])
  const terms = ref<TermSummary[]>([])
  const classes = ref<TeachingClassSummary[]>([])
  const students = ref<StudentSummary[]>([])

  async function loadInstitutions() {
    institutions.value = await fetchInstitutions()
  }

  async function loadScope(institutionId: string | null, campusId: string | null, termId: string | null) {
    if (!institutionId) {
      campuses.value = []
      terms.value = []
      classes.value = []
      students.value = []
      return
    }

    const [campusList, termList, classList, studentList] = await Promise.all([
      fetchCampuses(institutionId),
      fetchTerms(institutionId),
      fetchTeachingClasses({ institutionId, campusId, termId }),
      fetchStudents({ institutionId, campusId }),
    ])

    campuses.value = campusList
    terms.value = termList
    classes.value = classList
    students.value = studentList
  }

  return {
    institutions,
    campuses,
    terms,
    classes,
    students,
    loadInstitutions,
    loadScope,
  }
}
