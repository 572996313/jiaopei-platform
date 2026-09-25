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

/** 性别取值统一走系统字典 gender(item_value,如 male/female/unknown),不再固定枚举 */
export type Gender = string

export interface StudentOwnProfile {
  bound: boolean
  studentId: string | null
  studentNo: string | null
  studentName: string | null
  gender: Gender | null
  birthday: string | null
  className: string | null
}

export interface DictOptionItem {
  itemCode: string
  itemName: string
  itemValue: string
}

export interface DictOptionGroup {
  typeCode: string
  items: DictOptionItem[]
}

// 字典选项与 PC 同一来源(仅登录即可访问),模块级缓存一次,页面直接复用
let genderOptionsPromise: Promise<DictOptionItem[]> | null = null

// 静态兜底:线上还没种入 gender 字典(V51 未发版)时选项接口会返回空组,
// 整行选择器会消失。兜底值与字典规范值一致(male/female/unknown),发版后自动被字典覆盖。
const GENDER_FALLBACK_OPTIONS: DictOptionItem[] = [
  { itemCode: 'male', itemName: '男', itemValue: 'male' },
  { itemCode: 'female', itemName: '女', itemValue: 'female' },
  { itemCode: 'unknown', itemName: '未知', itemValue: 'unknown' },
]

/** 拉取性别字典选项(缓存整个应用生命周期内一次;字典或接口不可用时回落静态选项,下次调用重试) */
export function fetchGenderOptions() {
  if (!genderOptionsPromise) {
    genderOptionsPromise = requestGet<DictOptionGroup[]>('/system/dicts/options', { typeCodes: 'gender' })
      .then((groups) => groups.find((group) => group.typeCode === 'gender')?.items ?? [])
      .then((items) => (items.length > 0 ? items : GENDER_FALLBACK_OPTIONS))
      .catch(() => {
        genderOptionsPromise = null // 失败不缓存,下次重新拉字典
        return GENDER_FALLBACK_OPTIONS
      })
  }
  return genderOptionsPromise
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