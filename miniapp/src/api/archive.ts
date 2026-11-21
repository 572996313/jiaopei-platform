import { requestDelete, requestGet, requestPost, requestPut } from '@/utils/request'
import type { PageResult } from './enterprise'

/**
 * 小程序企业端（学员 / 家长联系人 / 班级 / 入班）接口。
 *
 * <p>全部复用管理后台已有端点，不新增后端接口。学员、家长、班级的写操作带机构范围，
 * 后端还会再做一次 InstitutionScopeGuard / CampusScopeGuard 校验，前端传的机构只用于组装请求。</p>
 */

// ---------------------------------------------------------------- 编号建议

/** 取下一个建议编号；只读不落库，资源未启用编码规则时会报错，调用方静默忽略即可 */
export function fetchNextCode(resource: 'student' | 'class') {
  return requestGet<string>('/system/code-rules/next', { resource })
}

// ---------------------------------------------------------------- 学员

export interface StudentRecord {
  id: string
  institutionId?: string | null
  userId?: string | null
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

export interface StudentPageParams {
  page?: number
  pageSize?: number
  institutionId?: string | null
  keyword?: string
  status?: string
}

export interface StudentCreatePayload {
  institutionId: string
  studentNo?: string
  studentName: string
  gender?: string | null
  birthday?: string | null
  guardianName?: string | null
  guardianPhone?: string | null
  status?: string
  enrolledAt?: string | null
  remark?: string
}

export type StudentUpdatePayload = Omit<StudentCreatePayload, 'institutionId'>

export function fetchStudentPage(params: StudentPageParams) {
  return requestGet<PageResult<StudentRecord>>(
    '/education/students/page',
    params as unknown as Record<string, unknown>,
  )
}

export function createStudent(payload: StudentCreatePayload) {
  return requestPost<StudentRecord>('/education/students', payload as unknown as Record<string, unknown>)
}

export function updateStudent(id: string, payload: StudentUpdatePayload) {
  return requestPut<StudentRecord>(`/education/students/${id}`, payload as unknown as Record<string, unknown>)
}

export function deleteStudent(id: string) {
  return requestDelete<null>(`/education/students/${id}`)
}

// 学员没有按 id 取详情的端点，列表 → 详情 → 编辑之间用一次性的本地交接单，
// 详情页每次 onShow 取一次；取不到就提示回列表重新进入，绝不按 id 猜档案内容。
const STUDENT_HANDOFF_KEY = 'cornerstone.mini.enterprise.student-handoff'

export function stageStudentRecord(record: StudentRecord) {
  uni.setStorageSync(STUDENT_HANDOFF_KEY, JSON.stringify(record))
}

/** 取一次即清：它只服务于紧邻的那一次页面跳转 */
export function takeStudentRecord(): StudentRecord | null {
  const raw = String(uni.getStorageSync(STUDENT_HANDOFF_KEY) || '')
  uni.removeStorageSync(STUDENT_HANDOFF_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as StudentRecord
  } catch {
    return null
  }
}

// ---------------------------------------------------------------- 家长联系人

export interface StudentParentRecord {
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

export interface StudentParentPayload {
  institutionId: string
  studentId: string
  parentName: string
  relationType?: string | null
  parentPhone?: string | null
  wechatNo?: string | null
  alipayPhone?: string | null
  isPrimary: boolean
  isEmergencyContact: boolean
  status?: string
  remark?: string
}

export function fetchStudentParents(params: { institutionId?: string | null; studentId: string }) {
  return requestGet<StudentParentRecord[]>('/education/student-parents', params)
}

/** 更新与新增共用同一个请求体：institutionId / studentId 都必须带 */
export function createStudentParent(payload: StudentParentPayload) {
  return requestPost<StudentParentRecord>(
    '/education/student-parents',
    payload as unknown as Record<string, unknown>,
  )
}

export function updateStudentParent(id: string, payload: StudentParentPayload) {
  return requestPut<StudentParentRecord>(
    `/education/student-parents/${id}`,
    payload as unknown as Record<string, unknown>,
  )
}

export function deleteStudentParent(id: string) {
  return requestDelete<null>(`/education/student-parents/${id}`)
}

// ---------------------------------------------------------------- 班级

export interface TeachingClassRecord {
  id: string
  institutionId?: string | null
  campusId?: string | null
  termId?: string | null
  courseId?: string | null
  classTypeId?: string | null
  classCode?: string | null
  className?: string | null
  teacherId?: string | null
  capacityLimit?: number | null
  weeklyLessons?: number | null
  weeklyLessonsMode?: string | null
  weeklyLessonsInput?: number | null
  totalLessons?: number | null
  currentStudents?: number | null
  status?: string | null
  openedAt?: string | null
  remark?: string | null
}

export interface TeachingClassPayload {
  institutionId: string
  campusId: string
  termId?: string | null
  courseId?: string | null
  classTypeId?: string | null
  classCode?: string
  className: string
  teacherId?: string | null
  capacityLimit: number
  weeklyLessonsMode: string
  weeklyLessonsInput?: number | null
  totalLessons?: number | null
  status: string
  openedAt?: string | null
  remark?: string
}

export function fetchClasses(params: {
  institutionId?: string | null
  campusId?: string | null
  termId?: string | null
}) {
  return requestGet<TeachingClassRecord[]>('/education/classes', params)
}

export function fetchClassDetail(institutionId: string, id: string) {
  return requestGet<TeachingClassRecord>(`/education/classes/${id}`, { institutionId })
}

export function createTeachingClass(payload: TeachingClassPayload) {
  return requestPost<TeachingClassRecord>(
    '/education/classes',
    payload as unknown as Record<string, unknown>,
  )
}

export function updateTeachingClass(id: string, payload: TeachingClassPayload) {
  return requestPut<TeachingClassRecord>(
    `/education/classes/${id}`,
    payload as unknown as Record<string, unknown>,
  )
}

export function deleteTeachingClass(id: string) {
  return requestDelete<null>(`/education/classes/${id}`)
}

// ---------------------------------------------------------------- 入班名册

export interface ClassStudentRecord {
  id: string
  institutionId?: string | null
  classId: string
  classCode?: string | null
  className?: string | null
  courseId?: string | null
  classTypeId?: string | null
  studentId: string
  studentNo?: string | null
  studentName?: string | null
  status?: string | null
  joinedAt?: string | null
  leftAt?: string | null
  remark?: string | null
}

export interface ClassStudentPageParams {
  page?: number
  pageSize?: number
  institutionId?: string | null
  campusId?: string | null
  classId?: string | null
  studentId?: string | null
  status?: string
}

export interface ClassStudentPayload {
  classId: string
  studentId: string
  institutionId?: string
  status?: string
  joinedAt?: string | null
  leftAt?: string | null
  remark?: string
}

export function fetchClassStudentPage(params: ClassStudentPageParams) {
  return requestGet<PageResult<ClassStudentRecord>>(
    '/education/class-students/page',
    params as unknown as Record<string, unknown>,
  )
}

export function createClassStudent(payload: ClassStudentPayload) {
  return requestPost<ClassStudentRecord>(
    '/education/class-students',
    payload as unknown as Record<string, unknown>,
  )
}

/** 没有更新端点：退班/移出都走删除，删除后同班可重新入班 */
export function deleteClassStudent(id: string) {
  return requestDelete<null>(`/education/class-students/${id}`)
}

// ---------------------------------------------------------------- 班级表单用的主数据

export interface CourseOptionRecord {
  id: string
  courseCode?: string | null
  courseName?: string | null
  status?: string | null
}

export interface ClassTypeOptionRecord {
  id: string
  typeCode?: string | null
  typeName?: string | null
  status?: string | null
}

export interface TeacherOptionRecord {
  id: string
  teacherCode?: string | null
  teacherName?: string | null
  campusId?: string | null
  status?: string | null
}

export function fetchCourses(params?: { institutionId?: string | null }) {
  return requestGet<CourseOptionRecord[]>('/education/courses', params)
}

export function fetchClassTypes(params?: { institutionId?: string | null }) {
  return requestGet<ClassTypeOptionRecord[]>('/education/class-types', params)
}

export function fetchTeachers(params?: { institutionId?: string | null; campusId?: string | null }) {
  return requestGet<TeacherOptionRecord[]>('/education/teachers', params)
}

// ---------------------------------------------------------------- 展示辅助

export function classStatusLabel(status?: string | null) {
  switch (status) {
    case 'active':
      return '启用'
    case 'inactive':
      return '停用'
    case 'closed':
      return '已结课'
    case 'planning':
    default:
      return '规划中'
  }
}

export function classStatusTone(status?: string | null) {
  switch (status) {
    case 'active':
      return 'green'
    case 'inactive':
      return 'orange'
    case 'closed':
      return ''
    case 'planning':
    default:
      return 'blue'
  }
}

export function classStudentStatusLabel(status?: string | null) {
  switch (status) {
    case 'quit':
      return '退班'
    case 'graduated':
      return '结业'
    case 'reserved':
      return '预占'
    case 'suspended':
      return '停班'
    case 'left':
      return '已离班'
    case 'active':
    default:
      return '在班'
  }
}

export function studentStatusLabel(status?: string | null) {
  switch (status) {
    case 'studying':
      return '在读'
    case 'suspended':
      return '停课'
    case 'graduated':
      return '已结业'
    case 'withdrawn':
      return '已退学'
    default:
      return status || '未知'
  }
}

export function studentStatusTone(status?: string | null) {
  switch (status) {
    case 'studying':
      return 'green'
    case 'suspended':
      return 'orange'
    case 'graduated':
    case 'withdrawn':
      return ''
    default:
      return 'blue'
  }
}

export function parentStatusLabel(status?: string | null) {
  return status === 'disabled' || status === 'inactive' ? '停用' : '启用'
}

/** 空串转 undefined，供可选文本字段使用 */
export function textOrUndefined(value?: string | null) {
  const trimmed = (value ?? '').trim()
  return trimmed === '' ? undefined : trimmed
}

/** 空串转 null，供日期/数值这类“留空即清空”的字段使用 */
export function numberOrNull(value: string): number | null {
  const trimmed = (value ?? '').trim()
  if (trimmed === '') {
    return null
  }
  const parsed = Number(trimmed)
  return Number.isNaN(parsed) ? null : parsed
}
