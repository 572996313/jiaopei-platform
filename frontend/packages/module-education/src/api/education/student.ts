import { requestDelete, requestGet, requestPost, requestPut } from '@cornerstone/core/api/request'
import type { PageParams, PageResult } from '@cornerstone/core/api/common'

/**
 * 教务 · 学员与家长 的接口与类型（由 api/education.ts 按功能域拆出）。
 */

export interface StudentSummary {
  id: string
  institutionId?: string | null
  studentNo?: string | null
  studentName?: string | null
  gender?: string | null
  birthday?: string | null
  guardianName?: string | null
  guardianPhone?: string | null
  status?: string | null
  enrolledAt?: string | null
  remark?: string | null
}

export interface StudentParentRecord {
  id: string
  institutionId?: string | null
  studentId?: string | null
  parentName?: string | null
  parentPhone?: string | null
  relationType?: string | null
  wechatNo?: string | null
  alipayPhone?: string | null
  isPrimary?: boolean | null
  isEmergencyContact?: boolean | null
  status?: string | null
  remark?: string | null
}

export interface StudentParentCreatePayload {
  institutionId: string
  studentId: string
  parentName: string
  relationType?: string
  parentPhone?: string
  wechatNo?: string
  alipayPhone?: string
  isPrimary?: boolean
  isEmergencyContact?: boolean
  status?: string
  remark?: string
}

export interface StudentCreatePayload {
  institutionId: string
  studentNo: string
  studentName: string
  gender?: string
  birthday?: string | null
  guardianName?: string
  guardianPhone?: string
  status?: string
  enrolledAt?: string | null
  remark?: string
}

export interface StudentUpdatePayload {
  studentNo?: string
  studentName?: string
  gender?: string
  birthday?: string | null
  guardianName?: string
  guardianPhone?: string
  status?: string
  enrolledAt?: string | null
  remark?: string
}

/** 学员下拉选项:服务端只回一屏(30 条),关键字模糊匹配姓名/学号 */
export interface StudentOption {
  id: string
  studentNo?: string | null
  studentName?: string | null
}

export function fetchStudentOptions(params: {
  institutionId?: string | null
  keyword?: string | null
  /** 按 id 批量回查名称用（表格行只有 id 时补回显），传了它后端会忽略 keyword */
  ids?: string
}) {
  return requestGet<StudentOption[]>('/education/students/options', params)
}

export function createStudent(payload: StudentCreatePayload) {
  return requestPost<StudentSummary>('/education/students', payload)
}

export function updateStudent(id: string, payload: StudentUpdatePayload) {
  return requestPut<StudentSummary>(`/education/students/${id}`, payload)
}

export function deleteStudent(id: string) {
  return requestDelete<null>(`/education/students/${id}`)
}

export function fetchStudentParents(params?: {
  institutionId?: string | null
  studentId?: string | null
}) {
  return requestGet<StudentParentRecord[]>('/education/student-parents', params ?? undefined)
}

export function createStudentParent(payload: StudentParentCreatePayload) {
  return requestPost<StudentParentRecord>('/education/student-parents', payload)
}

export function updateStudentParent(id: string, payload: StudentParentCreatePayload) {
  return requestPut<StudentParentRecord>(`/education/student-parents/${id}`, payload)
}

export function deleteStudentParent(id: string) {
  return requestDelete<null>(`/education/student-parents/${id}`)
}

/** 家长联系人分页视图,后端已回带学员名称 */
export interface StudentParentViewRecord {
  id: string
  institutionId?: string | null
  studentId?: string | null
  studentNo?: string | null
  studentName?: string | null
  parentName?: string | null
  relationType?: string | null
  parentPhone?: string | null
  wechatNo?: string | null
  alipayPhone?: string | null
  isPrimary?: boolean | null
  isEmergencyContact?: boolean | null
  status?: string | null
  remark?: string | null
}

export interface StudentParentPageParams extends PageParams {
  institutionId?: string | null
  studentId?: string | null
}

export function fetchStudentParentPage(params: StudentParentPageParams) {
  return requestGet<PageResult<StudentParentViewRecord>>('/education/student-parents/page', {
    ...params,
  })
}

export interface StudentPageParams extends PageParams {
  institutionId?: string | null
  keyword?: string | null
  status?: string | null
}

export function fetchStudentPage(params: StudentPageParams) {
  return requestGet<PageResult<StudentSummary>>('/education/students/page', { ...params })
}
