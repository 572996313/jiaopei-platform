import { computed, ref } from 'vue'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'
import {
  fetchClassrooms,
  fetchTimeSlots,
  type ClassroomSummary,
  type TimeSlotSummary,
} from '@cornerstone/module-organization/api/organization'
import { fetchTeachers, type TeacherSummary } from '@cornerstone/module-education/api/education'
import { classLabelOf } from './classLabels'
import {
  compactTimeRange,
  formatTeacherIdsAs,
  timeSlotOptionLabel,
} from '@cornerstone/module-education/utils/schedule'

/**
 * 排课两页（课表与点名、课表总览）共用的名称反查与格式化。
 *
 * 教师/教室/时段三份是课次行上的名称反查字典：课次可以来自任何校区，
 * 一律按机构全量拉（后端会按角色的可访问校区收窄），不跟横幅的校区范围收窄——
 * 收窄会让同一机构其他校区的课次渲染成「教师 #裸id」。
 */
export function useLessonFormatters() {
  const institutionStore = useInstitutionStore()
  const scopeStore = useEducationScopeStore()

  const teachers = ref<TeacherSummary[]>([])
  const classrooms = ref<ClassroomSummary[]>([])
  const timeSlots = ref<TimeSlotSummary[]>([])

  const institutionMap = computed(() => {
    return new Map(
      institutionStore.institutions.map((item) => [
        item.id,
        item.orgName || item.shortName || item.orgCode || `机构 #${item.id}`,
      ])
    )
  })

  const campusMap = computed(() => {
    return new Map(
      scopeStore.campuses.map((item) => [
        item.id,
        item.campusName || item.campusCode || `校区 #${item.id}`,
      ])
    )
  })

  const classroomMap = computed(() => {
    return new Map(
      classrooms.value.map((item) => [
        item.id,
        item.classroomName || item.classroomCode || `教室 #${item.id}`,
      ])
    )
  })

  const teacherMap = computed(() => {
    return new Map(
      teachers.value.map((item) => [
        item.id,
        item.teacherName || item.teacherCode || `教师 #${item.id}`,
      ])
    )
  })

  const timeSlotMap = computed(() => {
    return new Map(timeSlots.value.map((item) => [item.id, item]))
  })

  async function loadSupportingOptions() {
    const institutionId = institutionStore.currentInstitutionId
    if (!institutionId) {
      teachers.value = []
      classrooms.value = []
      timeSlots.value = []
      return
    }

    const [teacherList, classroomList, timeSlotList] = await Promise.all([
      fetchTeachers({ institutionId }),
      fetchClassrooms(null),
      fetchTimeSlots(institutionId),
    ])

    teachers.value = teacherList
    classrooms.value = classroomList
    timeSlots.value = timeSlotList
  }

  function formatInstitution(id?: string | null) {
    if (!id) {
      return '未选择'
    }
    return institutionMap.value.get(id) || `机构 #${id}`
  }

  function formatCampus(id?: string | null) {
    if (!id) {
      // 受限角色看到的是「全部可访问校区」，不能写成「全部校区」误导操作员
      return scopeStore.allCampusesLabel
    }
    return campusMap.value.get(id) || `校区 #${id}`
  }

  function formatClass(id?: string | null) {
    return classLabelOf(id)
  }

  function formatClassroom(id?: string | null) {
    if (!id) {
      return '未选择'
    }
    return classroomMap.value.get(id) || `教室 #${id}`
  }

  function formatTimeSlot(id?: string | null, startTime?: string | null, endTime?: string | null) {
    if (id) {
      const slot = timeSlotMap.value.get(id)
      if (slot) {
        return timeSlotOptionLabel(slot.slotName, slot.startTime, slot.endTime, slot.weekday)
      }
    }

    const fallbackRange = compactTimeRange(startTime, endTime)
    return fallbackRange || (id ? `时间段 #${id}` : '未选择')
  }

  function formatTeacherNames(values?: Array<string | number> | null) {
    return formatTeacherIdsAs(values, teacherMap.value)
  }

  return {
    teachers,
    classrooms,
    timeSlots,
    loadSupportingOptions,
    formatInstitution,
    formatCampus,
    formatClass,
    formatClassroom,
    formatTimeSlot,
    formatTeacherNames,
  }
}
