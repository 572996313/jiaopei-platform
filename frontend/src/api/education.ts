import { requestDelete, requestGet, requestPost, requestPut } from '@/api/request'

export interface StudentSummary {
  id: string
  institutionId?: string | null
  campusId?: string | null
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
  messages?: string[] | null
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
  campusId?: string | null
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
  campusId?: string | null
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

export function fetchStudents(params?: { institutionId?: string | null; campusId?: string | null; keyword?: string | null }) {
  return requestGet<StudentSummary[]>('/education/students', params ?? undefined)
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

export function fetchCourses(params?: { institutionId?: string | null; categoryId?: string | null }) {
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

export function fetchTeachers(params?: { institutionId?: string | null; campusId?: string | null }) {
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

export function fetchTeachingClasses(params?: {
  institutionId?: string | null
  campusId?: string | null
  termId?: string | null
}) {
  return requestGet<TeachingClassSummary[]>('/education/classes', params ?? undefined)
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

export function fetchClassStudents(params?: { classId?: string | null; studentId?: string | null }) {
  return requestGet<ClassStudentRecord[]>('/education/class-students', params ?? undefined)
}

export function createClassStudent(payload: ClassStudentCreatePayload) {
  return requestPost<ClassStudentRecord>('/education/class-students', payload)
}

export function deleteClassStudent(id: string) {
  return requestDelete<null>(`/education/class-students/${id}`)
}

export function fetchClassTeachers(params?: { classId?: string | null; teacherId?: string | null }) {
  return requestGet<ClassTeacherRecord[]>('/education/class-teachers', params ?? undefined)
}

export function fetchClassChangeNotices(params?: {
  institutionId?: string | null
  classId?: string | null
  noticeStatus?: string | null
}) {
  return requestGet<ClassChangeNoticeRecord[]>('/education/class-change-notices', params ?? undefined)
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

export function fetchStudentParents(params?: { institutionId?: string | null; studentId?: string | null }) {
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
