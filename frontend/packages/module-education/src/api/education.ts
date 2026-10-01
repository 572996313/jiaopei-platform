import { requestDelete, requestGet, requestPost, requestPut } from '@cornerstone/core/api/request'
import type { PageParams, PageResult } from '@cornerstone/core/api/common'

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

export interface CourseCategoryRecord {
  id: string
  institutionId?: string | null
  categoryCode?: string | null
  categoryName?: string | null
  status?: string | null
  sortOrder?: number | null
  remark?: string | null
}

export interface CourseRecord {
  id: string
  institutionId?: string | null
  categoryId?: string | null
  courseCode?: string | null
  courseName?: string | null
  subjectName?: string | null
  teachingMethod?: string | null
  status?: string | null
  remark?: string | null
}

export interface ClassTypeRecord {
  id: string
  institutionId?: string | null
  typeCode?: string | null
  typeName?: string | null
  teachingMethod?: string | null
  minStudents?: number | null
  maxStudents?: number | null
  status?: string | null
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

export interface StudentParentRecord {
  id: string
  institutionId?: string | null
  studentId?: string | null
  parentName?: string | null
  parentPhone?: string | null
  relationType?: string | null
  wechatNo?: string | null
  primaryContact?: boolean | null
  emergencyContact?: boolean | null
  status?: string | null
  remark?: string | null
}

export interface EnrollmentRecord {
  id: string
  institutionId?: string | null
  studentId?: string | null
  classId?: string | null
  enrollmentNo?: string | null
  channel?: string | null
  status?: string | null
  enrolledAt?: string | null
  remark?: string | null
}

export interface ClassStudentRecord {
  id: string
  institutionId?: string | null
  classId?: string | null
  studentId?: string | null
  status?: string | null
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
  joinedAt?: string | null
  leftAt?: string | null
  remark?: string | null
}

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

export interface ScheduleLessonRecord {
  id: string
  institutionId?: string | null
  campusId?: string | null
  termId?: string | null
  classId?: string | null
  classroomId?: string | null
  timeSlotId?: string | null
  lessonDate?: string | null
  lessonWeekday?: number | null
  startTime?: string | null
  endTime?: string | null
  lessonTitle?: string | null
  lessonStatus?: string | null
  changeType?: string | null
  relatedLessonId?: string | null
  pendingMakeup?: boolean | null
  teacherIds?: Array<string | number> | null
  remark?: string | null
  /** 命中的禁排规则说明；课次早于规则建立时也在这里显形 */
  ruleConflictMessages?: string[] | null
}

export interface ScheduleLessonPayload {
  institutionId: string
  campusId: string
  termId: string
  classId: string
  classroomId: string
  timeSlotId: string
  lessonDate: string
  lessonTitle?: string
  lessonStatus?: string
  changeType?: string | null
  relatedLessonId?: string | null
  teacherIds: string[]
  remark?: string
}

export interface ScheduleConflictCheckPayload {
  excludeLessonId?: string
  institutionId: string
  campusId: string
  termId: string
  classId: string
  classroomId: string
  timeSlotId: string
  lessonDate: string
  teacherIds: string[]
}

export interface ScheduleConflictCheckResult {
  conflict?: boolean | null
  conflictTypes?: string[] | null
  /** 硬冲突说明，仅 conflict=true 时有值，保存会被拦下 */
  messages?: string[] | null
  /** 软提示（学员跨班撞课/教师超周课时或日上限/撞校区日历停排日），只提示不拦保存 */
  notices?: string[] | null
}

export interface EnrollmentCreatePayload {
  institutionId: string
  studentId: string
  classId: string
  enrollmentNo: string
  channel?: string
  status?: string
  enrolledAt?: string | null
  remark?: string
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

export interface CourseCategoryCreatePayload {
  institutionId: string
  categoryCode: string
  categoryName: string
  status?: string
  sortOrder?: number
  remark?: string
}

export interface CourseCreatePayload {
  institutionId: string
  categoryId?: string | null
  courseCode: string
  courseName: string
  subjectName?: string
  teachingMethod?: string
  status?: string
  remark?: string
}

export interface ClassTypeCreatePayload {
  institutionId: string
  typeCode: string
  typeName: string
  teachingMethod?: string
  minStudents?: number
  maxStudents?: number
  status?: string
  remark?: string
}

export interface CourseCategoryUpdatePayload {
  categoryCode?: string
  categoryName?: string
  status?: string
  sortOrder?: number
  remark?: string
}

export interface CourseUpdatePayload {
  categoryId?: string | null
  courseCode?: string
  courseName?: string
  subjectName?: string
  teachingMethod?: string
  status?: string
  remark?: string
}

export interface ClassTypeUpdatePayload {
  typeCode?: string
  typeName?: string
  teachingMethod?: string
  minStudents?: number
  maxStudents?: number
  status?: string
  remark?: string
}

export interface ClassStudentCreatePayload {
  institutionId: string
  classId: string
  studentId: string
  status?: string
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

export interface StudentParentCreatePayload {
  institutionId: string
  studentId: string
  parentName: string
  relationType?: string
  parentPhone?: string
  wechatNo?: string
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

/** 学员下拉选项:服务端只回一屏(30 条),关键字模糊匹配姓名/学号 */
export interface StudentOption {
  id: string
  studentNo?: string | null
  studentName?: string | null
}

export function fetchStudentOptions(params: {
  institutionId?: string | null
  keyword?: string | null
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

export function fetchCourseCategories(params?: { institutionId?: string | null }) {
  return requestGet<CourseCategoryRecord[]>('/education/course-categories', params ?? undefined)
}

export function createCourseCategory(payload: CourseCategoryCreatePayload) {
  return requestPost<CourseCategoryRecord>('/education/course-categories', payload)
}

export function updateCourseCategory(id: string, payload: CourseCategoryUpdatePayload) {
  return requestPut<CourseCategoryRecord>(`/education/course-categories/${id}`, payload)
}

export function deleteCourseCategory(id: string) {
  return requestDelete<null>(`/education/course-categories/${id}`)
}

export function fetchCourses(params?: {
  institutionId?: string | null
  categoryId?: string | null
}) {
  return requestGet<CourseRecord[]>('/education/courses', params ?? undefined)
}

export function createCourse(payload: CourseCreatePayload) {
  return requestPost<CourseRecord>('/education/courses', payload)
}

export function updateCourse(id: string, payload: CourseUpdatePayload) {
  return requestPut<CourseRecord>(`/education/courses/${id}`, payload)
}

export function deleteCourse(id: string) {
  return requestDelete<null>(`/education/courses/${id}`)
}

export function fetchClassTypes(params?: { institutionId?: string | null }) {
  return requestGet<ClassTypeRecord[]>('/education/class-types', params ?? undefined)
}

export function createClassType(payload: ClassTypeCreatePayload) {
  return requestPost<ClassTypeRecord>('/education/class-types', payload)
}

export function updateClassType(id: string, payload: ClassTypeUpdatePayload) {
  return requestPut<ClassTypeRecord>(`/education/class-types/${id}`, payload)
}

export function deleteClassType(id: string) {
  return requestDelete<null>(`/education/class-types/${id}`)
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

export function fetchEnrollments(params?: {
  institutionId?: string | null
  classId?: string | null
  studentId?: string | null
}) {
  return requestGet<EnrollmentRecord[]>('/education/enrollments', params ?? undefined)
}

export function createEnrollment(payload: EnrollmentCreatePayload) {
  return requestPost<EnrollmentRecord>('/education/enrollments', payload)
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

export function fetchScheduleLessons(params?: {
  institutionId?: string | null
  campusId?: string | null
  classId?: string | null
  lessonDate?: string | null
  pendingMakeupOnly?: boolean | null
}) {
  return requestGet<ScheduleLessonRecord[]>('/education/schedules/lessons', params ?? undefined)
}

export function fetchScheduleLesson(id: string) {
  return requestGet<ScheduleLessonRecord>(`/education/schedules/lessons/${id}`)
}

export function createScheduleLesson(payload: ScheduleLessonPayload) {
  return requestPost<ScheduleLessonRecord>('/education/schedules/lessons', payload)
}

export function updateScheduleLesson(id: string, payload: ScheduleLessonPayload) {
  return requestPut<ScheduleLessonRecord>(`/education/schedules/lessons/${id}`, payload)
}

export function deleteScheduleLesson(id: string) {
  return requestDelete<null>(`/education/schedules/lessons/${id}`)
}

export function fetchDayTimetable(params: {
  institutionId?: string | null
  campusId?: string | null
  classId?: string | null
  lessonDate: string
  pendingMakeupOnly?: boolean | null
}) {
  return requestGet<ScheduleLessonRecord[]>('/education/schedules/timetable/day', params)
}

export function fetchWeekTimetable(params: {
  institutionId?: string | null
  campusId?: string | null
  classId?: string | null
  weekStart: string
  weekEnd: string
  pendingMakeupOnly?: boolean | null
}) {
  return requestGet<ScheduleLessonRecord[]>('/education/schedules/timetable/week', params)
}

export function checkScheduleConflicts(payload: ScheduleConflictCheckPayload) {
  return requestPost<ScheduleConflictCheckResult>('/education/schedules/conflicts/check', payload)
}

/** 周期排课计划的节次模式条目(星期几+时段) */
export interface SchedulePlanSlotItem {
  weekday: number
  timeSlotId: string
}

export interface SchedulePlanRecord {
  id: string
  institutionId?: string | null
  campusId?: string | null
  termId?: string | null
  classId?: string | null
  classroomId?: string | null
  teacherIds?: Array<string | number> | null
  slots?: SchedulePlanSlotItem[] | null
  weekFrequency?: string | null
  startDate?: string | null
  endDate?: string | null
  skipDates?: string[] | null
  status?: string | null
  remark?: string | null
  createdAt?: string | null
}

export interface SchedulePlanPayload {
  institutionId: string
  campusId: string
  termId: string
  classId: string
  classroomId: string
  teacherIds: string[]
  slots: SchedulePlanSlotItem[]
  weekFrequency?: string
  startDate: string
  endDate: string
  skipDates?: string[]
  status?: string
  remark?: string
}

export interface SchedulePlanPreviewItem {
  weekNo: number
  lessonDate: string
  weekday?: number | null
  timeSlotId: string
  slotName?: string | null
  startTime?: string | null
  endTime?: string | null
  conflict: boolean
  conflictMessages?: string[] | null
  skipped: boolean
  suggestedChecked: boolean
}

export interface SchedulePlanPreviewResult {
  items: SchedulePlanPreviewItem[]
  totalCount: number
  conflictCount: number
  skippedCount: number
  /** 必排规则未被本计划每周模式覆盖的提示，仅提示不拦提交 */
  ruleNotices?: string[] | null
  /** 软提示（学员跨班撞课/教师超周课时或日上限/撞校区日历停排日），只提示不拦生成 */
  notices?: string[] | null
}

export interface SchedulePlanCommitPayload {
  planId?: string | null
  plan: SchedulePlanPayload
  lessons: Array<{ lessonDate: string; timeSlotId: string }>
}

export interface SchedulePlanCommitResult {
  planId?: string | null
  createdCount: number
  skippedCount: number
  skipMessages?: string[] | null
}

export interface SchedulePlanPageParams extends PageParams {
  institutionId?: string | null
  campusId?: string | null
  classId?: string | null
  status?: string | null
  activeFrom?: string | null
  activeTo?: string | null
}

export function fetchSchedulePlanPage(params: SchedulePlanPageParams) {
  return requestGet<PageResult<SchedulePlanRecord>>('/education/schedule-plans/page', {
    ...params,
  })
}

export function fetchSchedulePlan(id: string) {
  return requestGet<SchedulePlanRecord>(`/education/schedule-plans/${id}`)
}

export function previewSchedulePlan(payload: SchedulePlanPayload) {
  return requestPost<SchedulePlanPreviewResult>('/education/schedule-plans/preview', payload)
}

export function commitSchedulePlan(payload: SchedulePlanCommitPayload) {
  return requestPost<SchedulePlanCommitResult>('/education/schedule-plans/commit', payload)
}

export function updateSchedulePlan(id: string, payload: SchedulePlanPayload) {
  return requestPut<SchedulePlanRecord>(`/education/schedule-plans/${id}`, payload)
}

export function deleteSchedulePlan(id: string) {
  return requestDelete<null>(`/education/schedule-plans/${id}`)
}

export function cancelSchedulePlanLessons(id: string) {
  return requestDelete<number>(`/education/schedule-plans/${id}/lessons`)
}

/** 排课规则：班级/教师/教室在"星期×节次"网格上的禁排或必排约束，格子结构与周期排课计划的 slots 同构 */
export interface ScheduleRuleRecord {
  id: string
  institutionId?: string | null
  ruleType?: string | null
  /** 规则对象类型(class=班级,teacher=教师,classroom=教室) */
  targetType?: string | null
  targetId?: string | null
  slots?: SchedulePlanSlotItem[] | null
  startDate?: string | null
  endDate?: string | null
  status?: string | null
  remark?: string | null
  createdAt?: string | null
}

export interface ScheduleRulePayload {
  institutionId: string
  ruleType: string
  targetType: string
  targetId: string
  slots: SchedulePlanSlotItem[]
  startDate?: string
  endDate?: string
  status?: string
  remark?: string
}

export interface ScheduleRulePageParams extends PageParams {
  institutionId?: string | null
  ruleType?: string | null
  targetType?: string | null
  targetId?: string | null
  status?: string | null
}

export function fetchScheduleRulePage(params: ScheduleRulePageParams) {
  return requestGet<PageResult<ScheduleRuleRecord>>('/education/schedule-rules/page', {
    ...params,
  })
}

export function createScheduleRule(payload: ScheduleRulePayload) {
  return requestPost<ScheduleRuleRecord>('/education/schedule-rules', payload)
}

export function updateScheduleRule(id: string, payload: ScheduleRulePayload) {
  return requestPut<ScheduleRuleRecord>(`/education/schedule-rules/${id}`, payload)
}

export function deleteScheduleRule(id: string) {
  return requestDelete<null>(`/education/schedule-rules/${id}`)
}

/** 课时完成度：行=班级、列=自然周，应排优先取班级课时目标(weeklyLessons×周数)，未设目标时回落到该班启用中的排课计划展开 */
export interface ScheduleCompletenessWeekColumn {
  index: number
  weekStart: string
  weekEnd: string
}

export interface ScheduleCompletenessGap {
  lessonDate: string
  weekday?: number | null
  timeSlotId: string
  slotName?: string | null
}

export interface ScheduleCompletenessCell {
  /** false 表示计划没排到这一周，格内数字无意义 */
  covered: boolean
  expected: number
  generated: number
  /** 本周缺口数；按课时目标统计时可能多于 gaps 条数（目标比计划格子多，缺口没有落点日期） */
  missing: number
  completed: number
  cancelled: number
  extra: number
  gaps: ScheduleCompletenessGap[]
}

export interface ScheduleCompletenessRowSummary {
  expected: number
  generated: number
  completed: number
  missing: number
  extra: number
}

export interface ScheduleCompletenessClassRow {
  classId: string
  className?: string | null
  campusId?: string | null
  termId?: string | null
  planCount: number
  /** 分母来源(weekly_target=班级课时目标,plan_expand=启用中的排课计划展开) */
  denominatorSource?: string | null
  /** 班级课时目标(每周应上节数)，为空时后端回落到 plan_expand 口径 */
  targetLessons?: number | null
  /** 整班总课时(未设定为空)；只用于课时进度，不参与应排/缺口 */
  totalLessons?: number | null
  /** 已上课节数：日期已过且没停课的课次全量计数，不按计划格子匹配，与格子里的「已完成」口径不同 */
  heldLessons?: number | null
  /** 预计结课日(按近四周节奏推算)；算不出时为空 */
  estimatedFinishDate?: string | null
  /** 该班启用中计划的最晚结束日，与预计结课日一比就知道按现在的节奏上不上得完 */
  planEndDate?: string | null
  cells: ScheduleCompletenessCell[]
  summary: ScheduleCompletenessRowSummary
}

export interface ScheduleCompletenessSummary {
  classCount: number
  expected: number
  generated: number
  completed: number
  missing: number
  extra: number
  generatedPercent: number
  completedPercent: number
}

export interface ScheduleCompletenessResult {
  weekFrom: string
  weekTo: string
  weeks: ScheduleCompletenessWeekColumn[]
  classes: ScheduleCompletenessClassRow[]
  summary: ScheduleCompletenessSummary
}

export interface ScheduleCompletenessParams {
  institutionId?: string | null
  campusId?: string | null
  termId?: string | null
  classId?: string | null
  dateFrom?: string | null
  dateTo?: string | null
}

export function fetchScheduleCompleteness(params: ScheduleCompletenessParams) {
  return requestGet<ScheduleCompletenessResult>('/education/schedule-completeness', { ...params })
}

/** 报名记录分页视图,后端已回带学员与班级名称,无需再用全量学员/班级做名称回显 */
export interface EnrollmentViewRecord {
  id: string
  institutionId?: string | null
  studentId?: string | null
  studentNo?: string | null
  studentName?: string | null
  classId?: string | null
  className?: string | null
  enrollmentNo?: string | null
  channel?: string | null
  status?: string | null
  enrolledAt?: string | null
  remark?: string | null
}

export interface EnrollmentPageParams extends PageParams {
  institutionId?: string | null
  classId?: string | null
  studentId?: string | null
}

export function fetchEnrollmentPage(params: EnrollmentPageParams) {
  return requestGet<PageResult<EnrollmentViewRecord>>('/education/enrollments/page', { ...params })
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

export interface TeacherPageParams extends PageParams {
  institutionId?: string | null
  campusId?: string | null
  keyword?: string | null
  status?: string | null
}

export function fetchTeacherPage(params: TeacherPageParams) {
  return requestGet<PageResult<TeacherSummary>>('/education/teachers/page', { ...params })
}

export interface CoursePageParams extends PageParams {
  institutionId?: string | null
  categoryId?: string | null
  keyword?: string | null
  status?: string | null
}

export function fetchCoursePage(params: CoursePageParams) {
  return requestGet<PageResult<CourseRecord>>('/education/courses/page', { ...params })
}

export interface ScheduleLessonPageParams extends PageParams {
  institutionId?: string | null
  campusId?: string | null
  classId?: string | null
  lessonDate?: string | null
}

export function fetchScheduleLessonPage(params: ScheduleLessonPageParams) {
  return requestGet<PageResult<ScheduleLessonRecord>>('/education/schedules/lessons/page', {
    ...params,
  })
}

export function fetchPendingMakeupLessonPage(params: ScheduleLessonPageParams) {
  return requestGet<PageResult<ScheduleLessonRecord>>(
    '/education/schedules/lessons/pending-makeup-page',
    { ...params }
  )
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

/** 校区日历（停排日）：campusId=0 表示机构级（全校区停排），不是 null */
export interface SchoolCalendarDayRecord {
  id: string
  institutionId?: string | null
  campusId?: string | number | null
  dayDate?: string | null
  dayName?: string | null
  remark?: string | null
}

export interface SchoolCalendarDayPayload {
  institutionId: string
  /** 0 = 机构级（全校区停排） */
  campusId: string | number
  dayDate: string
  dayName: string
  remark?: string
}

/** 按年份拉取停排日，一次返回全年数组（后端不分页） */
export function fetchSchoolCalendar(params: { institutionId: string; year: number }) {
  return requestGet<SchoolCalendarDayRecord[]>('/education/school-calendar', {
    institutionId: params.institutionId,
    year: params.year,
  })
}

export function createSchoolCalendarDay(payload: SchoolCalendarDayPayload) {
  return requestPost<SchoolCalendarDayRecord>('/education/school-calendar', payload)
}

export function updateSchoolCalendarDay(id: string, payload: SchoolCalendarDayPayload) {
  return requestPut<SchoolCalendarDayRecord>(`/education/school-calendar/${id}`, payload)
}

export function deleteSchoolCalendarDay(id: string) {
  return requestDelete<null>(`/education/school-calendar/${id}`)
}

/** 自动排课（按缺口补排）：引擎不换人也不换房，教师与教室沿用班级既有计划 */
export interface ScheduleAutoStrategy {
  /** 优先落在该班这一周已排过课的日子，空=是 */
  concentrateClassDays?: boolean | null
  /** 同一天内优先贴该班已有课次的相邻节次，空=是 */
  preferAdjacentSlot?: boolean | null
  /** 该班单日最多排几节（只算本批补的，留空=不限） */
  maxDailyLessonsPerClass?: number | null
}

export interface ScheduleAutoBatchPayload {
  institutionId: string
  campusId: string
  termId: string
  startDate: string
  endDate: string
  /** 空数组=该校区全部有缺口的班级 */
  classIds?: string[]
  strategy?: ScheduleAutoStrategy
  remark?: string
}

export interface ScheduleAutoPlacement {
  classId: string
  className?: string | null
  weekNo: number
  lessonDate: string
  weekday?: number | null
  timeSlotId: string
  slotName?: string | null
  startTime?: string | null
  endTime?: string | null
  classroomId?: string | null
  classroomName?: string | null
  teacherIds: string[]
  teacherNames?: string | null
  /** plan_gap=补上计划里没展开出的格子，free_cell=计划外的空位 */
  source?: string | null
  /** 落停排日时的提示语，无提示为空 */
  notice?: string | null
}

export interface ScheduleAutoFailure {
  classId: string
  className?: string | null
  /** 整班没纳入时为空 */
  weekNo?: number | null
  reason: string
}

export interface ScheduleAutoPlanResult {
  executed: boolean
  batchId?: string | null
  startDate: string
  endDate: string
  requestedCount: number
  generatedCount: number
  unscheduledCount: number
  placements: ScheduleAutoPlacement[]
  failures: ScheduleAutoFailure[]
}

export interface ScheduleAutoBatchRecord {
  id: string
  institutionId?: string | null
  campusId?: string | null
  termId?: string | null
  startDate?: string | null
  endDate?: string | null
  classIds?: string[] | null
  strategy?: ScheduleAutoStrategy | null
  requestedCount?: number | null
  generatedCount?: number | null
  unscheduledCount?: number | null
  failures?: ScheduleAutoFailure[] | null
  /** completed=已执行，rolled_back=已撤销 */
  status?: string | null
  rolledBackAt?: string | null
  remark?: string | null
  createdAt?: string | null
}

export interface ScheduleAutoRollbackResult {
  batchId: string
  generatedCount?: number | null
  canceledCount: number
  keptCount: number
  message: string
}

export function previewScheduleAutoBatch(payload: ScheduleAutoBatchPayload) {
  return requestPost<ScheduleAutoPlanResult>('/education/schedule-auto-batches/preview', payload)
}

export function runScheduleAutoBatch(payload: ScheduleAutoBatchPayload) {
  return requestPost<ScheduleAutoPlanResult>('/education/schedule-auto-batches/run', payload)
}

/** 最近 20 个补排批次（后端不分页），按执行时间倒序 */
export function fetchScheduleAutoBatches(params: { institutionId: string; campusId?: string | null }) {
  return requestGet<ScheduleAutoBatchRecord[]>('/education/schedule-auto-batches', { ...params })
}

export function rollbackScheduleAutoBatch(id: string) {
  return requestDelete<ScheduleAutoRollbackResult>(`/education/schedule-auto-batches/${id}`)
}
