import { requestDelete, requestGet, requestPost, requestPut } from '@cornerstone/core/api/request'
import type { PageParams, PageResult } from '@cornerstone/core/api/common'

/**
 * 教务 · 教师 的接口与类型（由 api/education.ts 按功能域拆出）。
 */

export interface TeacherSummary {
  id: string
  institutionId?: string | null
  campusId?: string | null
  teacherCode?: string | null
  teacherName?: string | null
  phone?: string | null
  gender?: string | null
  title?: string | null
  employmentType?: string | null
  status?: string | null
  hiredAt?: string | null
  remark?: string | null
  /** 关联登录账号ID：null = 尚未开通或未绑定账号 */
  userId?: string | null
  /** 每周负载上限（节），null=不限；超出时排课只提示不拦 */
  maxWeeklyLessons?: number | null
  /** 每日负载上限（节），null=不限；超出时排课只提示不拦 */
  maxDailyLessons?: number | null
}

export interface TeacherCreatePayload {
  institutionId: string
  campusId?: string
  teacherCode: string
  teacherName: string
  phone?: string
  gender?: string
  title?: string
  employmentType?: string
  status?: string
  hiredAt?: string | null
  remark?: string
  /** 每周负载上限（节），null/缺省=不限 */
  maxWeeklyLessons?: number | null
  /** 每日负载上限（节），null/缺省=不限 */
  maxDailyLessons?: number | null
}

export interface TeacherUpdatePayload {
  campusId?: string
  teacherCode?: string
  teacherName?: string
  phone?: string
  gender?: string
  title?: string
  employmentType?: string
  status?: string
  hiredAt?: string | null
  remark?: string
  /** 每周负载上限（节），null=清空回落为不限 */
  maxWeeklyLessons?: number | null
  /** 每日负载上限（节），null=清空回落为不限 */
  maxDailyLessons?: number | null
}

export function fetchTeachers(params?: {
  institutionId?: string | null
  campusId?: string | null
}) {
  return requestGet<TeacherSummary[]>('/education/teachers', params ?? undefined)
}

export function createTeacher(payload: TeacherCreatePayload) {
  return requestPost<TeacherSummary>('/education/teachers', payload)
}

export function updateTeacher(id: string, payload: TeacherUpdatePayload) {
  return requestPut<TeacherSummary>(`/education/teachers/${id}`, payload)
}

export function deleteTeacher(id: string) {
  return requestDelete<null>(`/education/teachers/${id}`)
}

export interface TeacherPageParams extends PageParams {
  institutionId?: string | null
  campusId?: string | null
  keyword?: string | null
  status?: string | null
}

export function fetchTeacherPage(params: TeacherPageParams) {
  return requestGet<PageResult<TeacherSummary>>('/education/teachers/page', { ...params })
}
