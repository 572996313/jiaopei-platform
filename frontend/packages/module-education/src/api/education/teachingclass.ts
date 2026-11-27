import { requestDelete, requestGet, requestPost, requestPut } from '@cornerstone/core/api/request'
import type { PageParams, PageResult } from '@cornerstone/core/api/common'

/**
 * 教务 · 班级与名册 的接口与类型（由 api/education.ts 按功能域拆出）。
 */

export interface TeachingClassSummary {
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
  currentStudents?: number | null
  /** 每周应上节数（课时目标）＝换算后的结果，完成度分母与补排只读这一列；null=未设定 */
  weeklyLessons?: number | null
  /** 课时目标的录入口径：weekly=按周填，monthly=按月填由后端换算 */
  weeklyLessonsMode?: string | null
  /** 课时目标的录制原值（monthly 时是每月节数），编辑弹窗回显用它，不要拿换算结果覆盖 */
  weeklyLessonsInput?: number | null
  /** 整班总课时（合同/教学计划的总节数），只用于课时进度，不进完成度分母 */
  totalLessons?: number | null
  status?: string | null
  openedAt?: string | null
  remark?: string | null
}

export interface ClassTeacherRecord {
  id: string
  institutionId?: string | null
  classId?: string | null
  teacherId?: string | null
  teacherRole?: string | null
  isPrimary?: boolean | null
  joinedAt?: string | null
  leftAt?: string | null
  status?: string | null
  remark?: string | null
}

export interface ClassChangeNoticeRecord {
  id: string
  institutionId?: string | null
  campusId?: string | null
  classId?: string | null
  sourceType?: string | null
  sourceId?: string | null
  noticeType?: string | null
  noticeLevel?: string | null
  noticeStatus?: string | null
  noticeMessage?: string | null
  suggestedAction?: string | null
  resolvedAt?: string | null
  resolvedBy?: string | null
  remark?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface ClassStudentRecord {
  id: string
  institutionId?: string | null
  classId?: string | null
  studentId?: string | null
  status?: string | null
  channel?: string | null
  joinedAt?: string | null
  leftAt?: string | null
  remark?: string | null
}

/** 班级学员关系分页视图,后端已回带班级与学员名称,分页列表无需再全量加载班级/学员做名称回显 */
export interface ClassStudentViewRecord {
  id: string
  institutionId?: string | null
  classId?: string | null
  classCode?: string | null
  className?: string | null
  courseId?: string | null
  classTypeId?: string | null
  studentId?: string | null
  studentNo?: string | null
  studentName?: string | null
  status?: string | null
  channel?: string | null
  joinedAt?: string | null
  leftAt?: string | null
  remark?: string | null
}

export interface ClassStudentCreatePayload {
  institutionId: string
  classId: string
  studentId: string
  status?: string
  channel?: string
  joinedAt?: string | null
  leftAt?: string | null
  remark?: string
}

export interface TeachingClassCreatePayload {
  institutionId: string
  campusId: string
  termId?: string | null
  courseId?: string | null
  classTypeId?: string | null
  classCode: string
  className: string
  teacherId?: string | null
  capacityLimit?: number
  /** 每周应上节数（课时目标），null/缺省=未设定；mode=monthly 时由后端换算，不用自己填 */
  weeklyLessons?: number | null
  /** 课时目标录入口径(weekly=按周,monthly=按月)，不传按 weekly */
  weeklyLessonsMode?: string | null
  /** 课时目标录制原值：weekly 时填每周节数，monthly 时填每月节数 */
  weeklyLessonsInput?: number | null
  /** 整班总课时（合同/教学计划的总节数），留空=不显示课时进度 */
  totalLessons?: number | null
  status?: string
  openedAt?: string | null
  remark?: string
}

export interface TeachingClassUpdatePayload {
  institutionId: string
  campusId: string
  termId?: string | null
  courseId?: string | null
  classTypeId?: string | null
  classCode: string
  className: string
  teacherId?: string | null
  capacityLimit?: number
  /** 每周应上节数（课时目标），null=清空回落为未设定 */
  weeklyLessons?: number | null
  weeklyLessonsMode?: string | null
  weeklyLessonsInput?: number | null
  /** 整班总课时，null=取消设定 */
  totalLessons?: number | null
  status?: string
  openedAt?: string | null
  remark?: string
}

export interface ClassTeacherCreatePayload {
  institutionId: string
  classId: string
  teacherId: string
  teacherRole?: string
  isPrimary?: boolean
  joinedAt?: string | null
  leftAt?: string | null
  status?: string
  remark?: string
}

/** 班级下拉选项:服务端只回一屏(30 条),关键字模糊匹配班级名称/编码 */
export interface ClassOption {
  id: string
  classCode?: string | null
  className?: string | null
  /** 排课弹窗按班级所属校区带出时间段,所以选项要带上它 */
  campusId?: string | null
  /** 排课计划弹窗预填班级所属学期 */
  termId?: string | null
}

export function fetchClassOptions(params: {
  institutionId?: string | null
  campusId?: string | null
  termId?: string | null
  keyword?: string | null
  /** 按 id 批量回查名称用(表格行只有 id 时补回显),传了它后端会忽略 keyword */
  ids?: string
}) {
  return requestGet<ClassOption[]>('/education/classes/options', params)
}

/** 单个班级整条档案:班级主档页一次只看一个班,不必把全机构班级拉回来当字典 */
export function fetchTeachingClass(institutionId: string, id: string) {
  return requestGet<TeachingClassSummary>(`/education/classes/${id}`, { institutionId })
}

/** 班级条数:下拉改成按需搜索后本机没有全量数组,统计卡的"班级数"要走服务端 */
export function fetchClassCount(params: {
  institutionId?: string | null
  campusId?: string | null
  termId?: string | null
}) {
  return requestGet<number>('/education/classes/count', params)
}

export function updateTeachingClass(id: string, payload: TeachingClassUpdatePayload) {
  return requestPut<TeachingClassSummary>(`/education/classes/${id}`, payload)
}

export function deleteTeachingClass(id: string) {
  return requestDelete<null>(`/education/classes/${id}`)
}

export function createTeachingClass(payload: TeachingClassCreatePayload) {
  return requestPost<TeachingClassSummary>('/education/classes', payload)
}

export interface ClassStudentPageParams extends PageParams {
  institutionId?: string | null
  campusId?: string | null
  classId?: string | null
  studentId?: string | null
  status?: string | null
}

export function fetchClassStudentPage(params: ClassStudentPageParams) {
  return requestGet<PageResult<ClassStudentViewRecord>>('/education/class-students/page', {
    ...params,
  })
}

/** 班级在班人数:分页表只装当前页,人数必须由服务端按整班统计 */
export function fetchClassStudentActiveCount(institutionId: string, classId: string) {
  return requestGet<number>('/education/class-students/active-count', { institutionId, classId })
}

export function createClassStudent(payload: ClassStudentCreatePayload) {
  return requestPost<ClassStudentRecord>('/education/class-students', payload)
}

export function deleteClassStudent(id: string) {
  return requestDelete<null>(`/education/class-students/${id}`)
}

export function fetchClassTeachers(params?: {
  classId?: string | null
  teacherId?: string | null
}) {
  return requestGet<ClassTeacherRecord[]>('/education/class-teachers', params ?? undefined)
}

export function fetchClassChangeNotices(params?: {
  institutionId?: string | null
  classId?: string | null
  noticeStatus?: string | null
}) {
  return requestGet<ClassChangeNoticeRecord[]>(
    '/education/class-change-notices',
    params ?? undefined
  )
}

export function createClassTeacher(payload: ClassTeacherCreatePayload) {
  return requestPost<ClassTeacherRecord>('/education/class-teachers', payload)
}

export function updateClassTeacher(id: string, payload: ClassTeacherCreatePayload) {
  return requestPut<ClassTeacherRecord>(`/education/class-teachers/${id}`, payload)
}

export function deleteClassTeacher(id: string) {
  return requestDelete<null>(`/education/class-teachers/${id}`)
}
