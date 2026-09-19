import { http } from '@/api/http'
import { requestDelete, requestGet, requestPost, requestPut } from '@/api/request'

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

export interface InstitutionSummary {
  id: string
  orgCode?: string | null
  orgName?: string | null
  shortName?: string | null
  status?: string | null
}

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

export function fetchInstitutions() {
  return requestGet<InstitutionSummary[]>('/organizations/institutions')
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
