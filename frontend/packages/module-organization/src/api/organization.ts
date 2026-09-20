import { http } from '@cornerstone/core/api/http'
import { requestDelete, requestGet, requestPost, requestPut } from '@cornerstone/core/api/request'

export interface OrganizationSchemaResponse {
  code: string
  success: boolean
  message: string
  data: {
    tables: string[]
    aggregates: string[]
    migration: string
  }
}

export function getOrganizationSchema() {
  return http.get<OrganizationSchemaResponse>('/organizations/schema')
}

// 机构（租户）类型与接口在底座 @cornerstone/core/api/tenant，本文件只放组织主数据。

export interface CampusSummary {
  id: string
  institutionId?: string | null
  campusCode?: string | null
  campusName?: string | null
  campusType?: string | null
  phone?: string | null
  principalName?: string | null
  address?: string | null
  capacityLimit?: number | null
  status?: string | null
}

export interface TermSummary {
  id: string
  institutionId?: string | null
  termCode?: string | null
  termName?: string | null
  academicYear?: string | null
  season?: string | null
  startDate?: string | null
  endDate?: string | null
  status?: string | null
  defaultTerm?: boolean | null
}

export interface ClassroomSummary {
  id: string
  campusId?: string | null
  classroomCode?: string | null
  classroomName?: string | null
  roomType?: string | null
  floorNo?: string | null
  seatCapacity?: number | null
  areaSqm?: string | null
  equipmentTags?: string | null
  status?: string | null
  allowScheduling?: boolean | null
  remark?: string | null
}

export interface TimeSlotSummary {
  id: string
  institutionId?: string | null
  campusId?: string | null
  slotCode?: string | null
  slotName?: string | null
  weekday?: number | null
  startTime?: string | null
  endTime?: string | null
  durationMinutes?: number | null
  slotType?: string | null
  applicableScene?: string | null
  status?: string | null
  templateId?: string | null
  sortNo?: number | null
  remark?: string | null
}

export interface CampusCreatePayload {
  institutionId: string
  campusCode: string
  campusName: string
  campusType?: string
  phone?: string
  principalName?: string
  address: string
  capacityLimit?: number | null
  status?: string
  openedAt?: string | null
  sortNo?: number | null
  remark?: string
}

export interface ClassroomCreatePayload {
  campusId: string
  classroomCode: string
  classroomName: string
  roomType?: string
  floorNo?: string
  seatCapacity: number
  areaSqm?: number | null
  equipmentTags?: string
  status?: string
  allowScheduling?: boolean
  remark?: string
}

export interface TermCreatePayload {
  institutionId: string
  termCode: string
  termName: string
  academicYear: string
  season?: string
  startDate: string
  endDate: string
  enrollmentStartDate?: string | null
  enrollmentEndDate?: string | null
  status?: string
  defaultTerm?: boolean
  remark?: string
}

export interface TimeSlotCreatePayload {
  institutionId: string
  campusId?: string | null
  slotCode: string
  slotName: string
  weekday: number
  startTime: string
  endTime: string
  durationMinutes: number
  slotType?: string
  applicableScene?: string
  status?: string
  sortNo?: number | null
  remark?: string
}

export interface CampusUpdatePayload {
  campusCode?: string
  campusName?: string
  campusType?: string
  phone?: string
  principalName?: string
  address?: string
  capacityLimit?: number | null
  status?: string
  openedAt?: string | null
  sortNo?: number | null
  remark?: string
}

export interface ClassroomUpdatePayload {
  campusId?: string
  classroomCode?: string
  classroomName?: string
  roomType?: string
  floorNo?: string
  seatCapacity?: number
  areaSqm?: number | null
  equipmentTags?: string
  status?: string
  allowScheduling?: boolean
  remark?: string
}

export interface TermUpdatePayload {
  termCode?: string
  termName?: string
  academicYear?: string
  season?: string
  startDate?: string
  endDate?: string
  enrollmentStartDate?: string | null
  enrollmentEndDate?: string | null
  status?: string
  defaultTerm?: boolean
  remark?: string
}

export interface TimeSlotUpdatePayload {
  campusId?: string | null
  slotCode?: string
  slotName?: string
  weekday?: number
  startTime?: string
  endTime?: string
  durationMinutes?: number
  slotType?: string
  applicableScene?: string
  status?: string
  sortNo?: number | null
  remark?: string
}

export function fetchCampuses(institutionId?: string | null) {
  return requestGet<CampusSummary[]>(
    '/organizations/campuses',
    institutionId ? { institutionId } : undefined
  )
}

export function fetchTerms(institutionId?: string | null) {
  return requestGet<TermSummary[]>(
    '/organizations/terms',
    institutionId ? { institutionId } : undefined
  )
}

export function fetchClassrooms(campusId?: string | null) {
  return requestGet<ClassroomSummary[]>(
    '/organizations/classrooms',
    campusId ? { campusId } : undefined
  )
}

export function fetchTimeSlots(institutionId?: string | null) {
  return requestGet<TimeSlotSummary[]>(
    '/organizations/time-slots',
    institutionId ? { institutionId } : undefined
  )
}

export function createCampus(payload: CampusCreatePayload) {
  return requestPost<CampusSummary>('/organizations/campuses', payload)
}

export function updateCampus(id: string, payload: CampusUpdatePayload) {
  return requestPut<CampusSummary>(`/organizations/campuses/${id}`, payload)
}

export function deleteCampus(id: string) {
  return requestDelete<null>(`/organizations/campuses/${id}`)
}

export function createClassroom(payload: ClassroomCreatePayload) {
  return requestPost<ClassroomSummary>('/organizations/classrooms', payload)
}

export function updateClassroom(id: string, payload: ClassroomUpdatePayload) {
  return requestPut<ClassroomSummary>(`/organizations/classrooms/${id}`, payload)
}

export function deleteClassroom(id: string) {
  return requestDelete<null>(`/organizations/classrooms/${id}`)
}

export function createTerm(payload: TermCreatePayload) {
  return requestPost<TermSummary>('/organizations/terms', payload)
}

export function updateTerm(id: string, payload: TermUpdatePayload) {
  return requestPut<TermSummary>(`/organizations/terms/${id}`, payload)
}

export function deleteTerm(id: string) {
  return requestDelete<null>(`/organizations/terms/${id}`)
}

export function createTimeSlot(payload: TimeSlotCreatePayload) {
  return requestPost<TimeSlotSummary>('/organizations/time-slots', payload)
}

export function updateTimeSlot(id: string, payload: TimeSlotUpdatePayload) {
  return requestPut<TimeSlotSummary>(`/organizations/time-slots/${id}`, payload)
}

export function deleteTimeSlot(id: string) {
  return requestDelete<null>(`/organizations/time-slots/${id}`)
}

/** 单一时段类型的节次参数(节数/时长/课间/首节开始时间) */
export interface TimeSlotSectionConfig {
  count: number
  durationMinutes: number
  breakMinutes: number
  startTime: string
}

/** 大课间参数(afterSection=0 表示不设置) */
export interface TimeSlotBigBreakConfig {
  minutes: number
  afterSection: number
}

/** 时段模板节次参数快照 */
export interface TimeSlotConfig {
  morningStudy: TimeSlotSectionConfig
  morning: TimeSlotSectionConfig
  morningBigBreak: TimeSlotBigBreakConfig
  afternoon: TimeSlotSectionConfig
  afternoonBigBreak: TimeSlotBigBreakConfig
  eveningStudy: TimeSlotSectionConfig
  weekdays: number[]
}

export interface TimeSlotTemplateSummary {
  id: string
  institutionId?: string | null
  campusId?: string | null
  templateName: string
  config: TimeSlotConfig | Record<string, unknown>
  status?: string | null
  remark?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface TimeSlotTemplateCreatePayload {
  institutionId: string
  campusId?: string | null
  templateName: string
  config: TimeSlotConfig
  remark?: string
}

export interface TimeSlotTemplateUpdatePayload {
  templateName?: string
  config?: TimeSlotConfig
  status?: string
  remark?: string
}

export interface TimeSlotApplySlotPayload {
  weekday: number
  slotCode: string
  slotName: string
  startTime: string
  endTime: string
  slotType?: string
  sortNo?: number | null
}

export interface TimeSlotApplyPayload {
  config?: TimeSlotConfig
  slots: TimeSlotApplySlotPayload[]
}

export interface TimeSlotApplyResult {
  createdCount: number
  updatedCount: number
  removedCount: number
  deactivatedCount: number
  slots: TimeSlotSummary[]
}

export function fetchTimeSlotTemplates(institutionId?: string | null, campusId?: string | null) {
  return requestGet<TimeSlotTemplateSummary[]>(
    '/organizations/time-slot-templates',
    institutionId ? { institutionId, campusId: campusId || undefined } : undefined
  )
}

export function createTimeSlotTemplate(payload: TimeSlotTemplateCreatePayload) {
  return requestPost<TimeSlotTemplateSummary>('/organizations/time-slot-templates', payload)
}

export function updateTimeSlotTemplate(id: string, payload: TimeSlotTemplateUpdatePayload) {
  return requestPut<TimeSlotTemplateSummary>(`/organizations/time-slot-templates/${id}`, payload)
}

export function deleteTimeSlotTemplate(id: string) {
  return requestDelete<null>(`/organizations/time-slot-templates/${id}`)
}

export function applyTimeSlotTemplate(id: string, payload: TimeSlotApplyPayload) {
  return requestPost<TimeSlotApplyResult>(`/organizations/time-slot-templates/${id}/apply`, payload)
}
