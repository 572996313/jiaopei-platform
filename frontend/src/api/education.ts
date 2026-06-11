import { requestDelete, requestGet, requestPost, requestPut } from '@/api/request'

export interface StudentSummary {
  id: string
  institutionId?: string | null
  campusId?: string | null
  studentNo?: string | null
  studentName?: string | null
  guardianName?: string | null
  guardianPhone?: string | null
  status?: string | null
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
  classCode?: string | null
  className?: string | null
  teacherId?: string | null
  capacityLimit?: number | null
  currentStudents?: number | null
  status?: string | null
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
  studentId?: string | null
  attendanceDate?: string | null
  attendanceStatus?: string | null
  source?: string | null
  remark?: string | null
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

export interface ClassStudentCreatePayload {
  institutionId: string
  classId: string
  studentId: string
  status?: string
  joinedAt?: string | null
  leftAt?: string | null
  remark?: string
}

export interface AttendanceCreatePayload {
  institutionId: string
  classId: string
  studentId: string
  attendanceDate: string
  attendanceStatus?: string
  source?: string
  remark?: string
}

export function fetchStudents(params?: { institutionId?: string | null; campusId?: string | null }) {
  return requestGet<StudentSummary[]>('/education/students', params ?? undefined)
}

export function fetchTeachers(params?: { institutionId?: string | null; campusId?: string | null }) {
  return requestGet<TeacherSummary[]>('/education/teachers', params ?? undefined)
}

export function createTeacher(payload: TeacherCreatePayload) {
  return requestPost<TeacherSummary>('/education/teachers', payload)
}

export function fetchTeachingClasses(params?: {
  institutionId?: string | null
  campusId?: string | null
  termId?: string | null
}) {
  return requestGet<TeachingClassSummary[]>('/education/classes', params ?? undefined)
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

export function fetchScheduleLessons(params?: {
  institutionId?: string | null
  campusId?: string | null
  classId?: string | null
  lessonDate?: string | null
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
  lessonDate: string
}) {
  return requestGet<ScheduleLessonRecord[]>('/education/schedules/timetable/day', params)
}

export function fetchWeekTimetable(params: {
  institutionId?: string | null
  campusId?: string | null
  weekStart: string
  weekEnd: string
}) {
  return requestGet<ScheduleLessonRecord[]>('/education/schedules/timetable/week', params)
}

export function checkScheduleConflicts(payload: ScheduleConflictCheckPayload) {
  return requestPost<ScheduleConflictCheckResult>('/education/schedules/conflicts/check', payload)
}
