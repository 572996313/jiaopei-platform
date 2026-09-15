import { requestDelete, requestGet, requestPost } from '@/utils/request'

export interface StudentBrief {
  id: string
  studentNo: string | null
  studentName: string
  gender: string | null
  className: string | null
  remark: string | null
}

export interface MiniappLesson {
  lessonId: string
  lessonDate: string
  weekday: number
  startTime: string
  endTime: string
  lessonTitle: string | null
  lessonStatus: string
  className: string | null
  courseName: string | null
  classroomName: string | null
  teacherName: string | null
  teacherPhone: string | null
}

export type BindMethod = 'STUDENT_NO' | 'PARENT_PHONE'

/** 绑定学员档案(学号/手机号 + 学员姓名双因子验证,防陌生人乱绑) */
export function bindStudent(method: BindMethod, value: string, studentName: string, remark?: string) {
  return requestPost<StudentBrief>('/miniapp/students/bind', {
    method,
    value,
    studentName,
    remark: remark?.trim() ? remark.trim() : undefined,
  })
}

/** 当前用户已关联的学员列表 */
export function listMyStudents() {
  return requestGet<StudentBrief[]>('/miniapp/students')
}

/** 解绑学员档案(解绑后可重新绑定) */
export function unbindStudent(studentId: string) {
  return requestDelete<void>('/miniapp/students/' + studentId)
}

export type Gender = 'MALE' | 'FEMALE' | 'UNKNOWN'

export interface StudentOwnProfile {
  bound: boolean
  studentId: string | null
  studentNo: string | null
  studentName: string | null
  gender: Gender | null
  birthday: string | null
  className: string | null
}

/** 学生端-我的档案(未绑定 bound=false) */
export function fetchMyStudentProfile() {
  return requestGet<StudentOwnProfile>('/miniapp/student/profile')
}

/** 学生端-绑定自己的档案(学号+姓名,不可换绑) */
export function bindSelfStudent(studentNo: string, studentName: string) {
  return requestPost<StudentOwnProfile>('/miniapp/student/bind-self', { studentNo, studentName })
}

/** 学生端-新建/编辑本人档案(姓名/性别/生日) */
export function saveStudentProfile(payload: { studentName: string; gender: Gender; birthday?: string }) {
  return requestPost<StudentOwnProfile>('/miniapp/student/profile', payload)
}

/** 某位学员在日期范围内的课表 */
export function fetchStudentLessons(studentId: string, start: string, end: string) {
  return requestGet<MiniappLesson[]>('/miniapp/students/' + studentId + '/lessons', {
    start,
    end,
  })
}