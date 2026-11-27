import { requestGet, requestPost } from '@cornerstone/core/api/request'

/**
 * 教务 · 考勤 的接口与类型（由 api/education.ts 按功能域拆出）。
 */

export interface AttendanceRecord {
  id: string
  institutionId?: string | null
  classId?: string | null
  lessonId?: string | null
  studentId?: string | null
  attendanceDate?: string | null
  attendanceStatus?: string | null
  source?: string | null
  remark?: string | null
}

export interface AttendanceLessonInfo {
  lessonId: string
  institutionId?: string | null
  campusId?: string | null
  classId?: string | null
  className?: string | null
  lessonDate?: string | null
  startTime?: string | null
  endTime?: string | null
  lessonTitle?: string | null
  lessonStatus?: string | null
  remark?: string | null
}

export interface AttendanceLessonStudent {
  studentId: string
  studentNo?: string | null
  studentName?: string | null
  attendanceId?: string | null
  attendanceStatus?: string | null
  source?: string | null
  remark?: string | null
}

export interface AttendanceLessonSummary {
  expectedCount: number
  markedCount: number
  presentCount: number
  absentCount: number
  leaveCount: number
  lateCount: number
}

export interface AttendanceLessonPage {
  lesson: AttendanceLessonInfo
  students: AttendanceLessonStudent[]
  summary: AttendanceLessonSummary
  message?: string | null
}

export interface AttendanceCreatePayload {
  institutionId: string
  classId: string
  lessonId?: string | null
  studentId: string
  attendanceDate: string
  attendanceStatus?: string
  source?: string
  remark?: string
}

export interface AttendanceLessonBatchItemPayload {
  studentId: string
  attendanceStatus?: string
  source?: string
  remark?: string
}

export interface AttendanceLessonBatchSavePayload {
  lessonId: string
  items: AttendanceLessonBatchItemPayload[]
}

export function fetchAttendance(params?: {
  classId?: string | null
  studentId?: string | null
  attendanceDate?: string | null
}) {
  return requestGet<AttendanceRecord[]>('/education/attendance', params ?? undefined)
}

export function createAttendance(payload: AttendanceCreatePayload) {
  return requestPost<AttendanceRecord>('/education/attendance', payload)
}

export function fetchLessonAttendancePage(lessonId: string) {
  return requestGet<AttendanceLessonPage>('/education/attendance/lesson-page', { lessonId })
}

export function saveLessonAttendanceBatch(payload: AttendanceLessonBatchSavePayload) {
  return requestPost<AttendanceLessonPage>('/education/attendance/lesson-batch', payload)
}

/** 考勤汇总合计，summary 接口与学员考勤明细接口共用 */
export interface AttendanceSummaryTotals {
  lessonCount: number
  fullyMarkedLessons: number
  pendingLessons: number
  expectedCount: number
  presentCount: number
  lateCount: number
  leaveCount: number
  absentCount: number
}

/** 考勤按月小计，季档展开行用，不再额外拉课次明细 */
export interface AttendanceMonthSubtotal extends AttendanceSummaryTotals {
  month: string
  attendanceRate: number | null
}

/**
 * 考勤汇总行：按班级分组时填 classId/className，按学员分组时填 studentId 一侧。
 * 名称由后端内存填充（同完成度报表 ClassRow.className 先例），前端不用按 id 回查。
 */
export interface AttendanceSummaryRow {
  classId?: string | null
  className?: string | null
  studentId?: string | null
  studentNo?: string | null
  studentName?: string | null
  /** 区间内上课的班级名称（按学员分组时有值） */
  classNames?: string[] | null
  lessonCount: number
  fullyMarkedLessons: number
  pendingLessons: number
  expectedCount: number
  presentCount: number
  lateCount: number
  leaveCount: number
  absentCount: number
  attendanceRate: number | null
  months: AttendanceMonthSubtotal[] | null
}

export interface AttendanceSummaryResponse {
  rangeStart: string
  rangeEnd: string
  groupBy: 'class' | 'student'
  summary: AttendanceSummaryTotals
  rows: AttendanceSummaryRow[]
}

/** 学员考勤明细行：attendanceStatus 为 null 表示该课次还没点到这位学员 */
export interface AttendanceStudentLesson {
  lessonId: string
  classId: string
  className?: string | null
  lessonDate?: string | null
  startTime?: string | null
  endTime?: string | null
  lessonStatus?: string | null
  lessonTitle?: string | null
  attendanceStatus?: string | null
  source?: string | null
  remark?: string | null
}

export interface AttendanceStudentDetailResponse {
  studentId: string
  studentNo?: string | null
  studentName?: string | null
  rangeStart: string
  rangeEnd: string
  summary: AttendanceSummaryTotals
  lessons: AttendanceStudentLesson[]
}

export function fetchAttendanceSummary(params: {
  institutionId?: string | null
  campusId?: string | null
  classId?: string | null
  studentId?: string | null
  dateFrom: string
  dateTo: string
  groupBy?: 'class' | 'student'
}) {
  return requestGet<AttendanceSummaryResponse>('/education/attendance/summary', params)
}

export function fetchAttendanceStudentDetail(params: { studentId: string; dateFrom: string; dateTo: string }) {
  return requestGet<AttendanceStudentDetailResponse>('/education/attendance/student-detail', params)
}

/** 课次点名进度,日课表批量拉取用,替代逐课次请求点名页 */
export interface LessonAttendanceProgress {
  lessonId: string
  expectedCount: number
  markedCount: number
  presentCount: number
  absentCount: number
  leaveCount: number
  lateCount: number
}

export function fetchLessonAttendanceProgress(lessonIds: string[]) {
  return requestGet<LessonAttendanceProgress[]>('/education/attendance/lesson-progress', {
    lessonIds: lessonIds.join(','),
  })
}
