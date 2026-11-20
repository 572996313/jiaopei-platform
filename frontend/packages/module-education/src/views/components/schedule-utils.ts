// 排课/执行相关页面的共享纯函数与轻量工具(排课管理页、课表与点名页、共享弹窗组件共用)
import {
  fetchScheduleLesson,
  updateScheduleLesson,
  type ScheduleLessonRecord,
} from '@cornerstone/module-education/api/education'

export function todayValue() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}

export function formatDate(date: Date) {
  const offset = date.getTimezoneOffset() * 60000
  return new Date(date.getTime() - offset).toISOString().slice(0, 10)
}

export function startOfWeek(value: string) {
  const date = new Date(`${value}T00:00:00`)
  const day = date.getDay()
  const diff = day === 0 ? -6 : 1 - day
  date.setDate(date.getDate() + diff)
  return formatDate(date)
}

export function addDays(value: string, days: number) {
  const date = new Date(`${value}T00:00:00`)
  date.setDate(date.getDate() + days)
  return formatDate(date)
}

export function addMonths(value: string, months: number) {
  const date = new Date(`${value}T00:00:00`)
  const day = date.getDate()
  const target = new Date(date.getFullYear(), date.getMonth() + months, 1)
  const daysInTarget = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate()
  target.setDate(Math.min(day, daysInTarget))
  return formatDate(target)
}

export function lastDayOfMonth(firstOfMonth: string) {
  const date = new Date(`${firstOfMonth}T00:00:00`)
  // 传入某月 1 号：目标月下一个月的第 0 天即本月最后一天
  return formatDate(new Date(date.getFullYear(), date.getMonth() + 1, 0))
}

// 周=所在日历周；月/季=首尾对齐到周一~周日，方便日历网格直接每 7 天切一行
export function rangeFor(
  mode: 'week' | 'month' | 'quarter',
  anchor: string
): { start: string; end: string } {
  if (mode === 'week') {
    const start = startOfWeek(anchor)
    return { start, end: addDays(start, 6) }
  }

  let first: string
  let last: string
  if (mode === 'month') {
    first = `${anchor.slice(0, 7)}-01`
    last = lastDayOfMonth(first)
  } else {
    const anchorMonth = Number(anchor.slice(5, 7))
    const quarterFirstMonth = Math.floor((anchorMonth - 1) / 3) * 3 + 1
    first = `${anchor.slice(0, 4)}-${String(quarterFirstMonth).padStart(2, '0')}-01`
    last = lastDayOfMonth(addMonths(first, 2))
  }
  const start = startOfWeek(first)
  return { start, end: addDays(startOfWeek(last), 6) }
}

export function enumerateDates(start: string, end: string) {
  if (!start || !end || start > end) {
    return []
  }

  const values: string[] = []
  const cursor = new Date(`${start}T00:00:00`)
  const endDate = new Date(`${end}T00:00:00`)

  while (cursor <= endDate) {
    values.push(formatDate(cursor))
    cursor.setDate(cursor.getDate() + 1)
  }

  return values
}

export function weekdayFromDate(value?: string | null) {
  if (!value) {
    return null
  }
  const weekday = new Date(`${value}T00:00:00`).getDay()
  return weekday === 0 ? 7 : weekday
}

export function compactDateLabel(value?: string | null) {
  if (!value) {
    return '未命名'
  }
  return value.length >= 10 ? value.slice(5) : value
}

// 文本归一收口在 core 公共工具:可清空控件(el-select 等)清空后是 undefined,不是空串
export { trimToUndefined } from '@cornerstone/core/utils/text'

export function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

export function normalizeTeacherIds(values?: Array<string | number> | null) {
  return Array.from(new Set((values || []).map((item) => String(item))))
}

export function formatTeacherIdsAs(
  values: Array<string | number> | null | undefined,
  teacherMap: Map<string, string>
) {
  const ids = normalizeTeacherIds(values)
  if (ids.length === 0) {
    return '未选择'
  }
  return ids.map((id) => teacherMap.get(id) || `教师 #${id}`).join('、')
}

export function compactTimeRange(startTime?: string | null, endTime?: string | null) {
  if (startTime && endTime) {
    return `${startTime.slice(0, 5)}-${endTime.slice(0, 5)}`
  }
  return startTime?.slice(0, 5) || endTime?.slice(0, 5) || ''
}

const WEEKDAY_PREFIX_RE = /^周[一二三四五六日]/

export function timeSlotOptionLabel(
  name?: string | null,
  startTime?: string | null,
  endTime?: string | null,
  weekday?: number | null
) {
  const label = name || '时间段'
  const timeRange = compactTimeRange(startTime, endTime)
  const week = weekdayLabel(weekday)
  // 节次名自带"周一上1"这类前缀时不再重复拼一遍星期
  const needWeek = week !== '未知' && !WEEKDAY_PREFIX_RE.test(label)
  return [label, timeRange, needWeek ? week : ''].filter(Boolean).join(' / ')
}

/** 节次名的星期前缀在网格列头/下拉分组里已经说过,展示时剥掉 */
export function timeSlotShortName(name?: string | null) {
  if (!name) {
    return ''
  }
  const stripped = name.replace(WEEKDAY_PREFIX_RE, '')
  return stripped || name
}

/** 节次所属时段,下拉分组用。slot_type 只分早自习/授课/晚自习,授课再按开始时间拆上下午 */
export function timeSlotSectionOf(slot: {
  slotType?: string | null
  startTime?: string | null
}): string {
  if (slot.slotType === 'morning_study') {
    return '早自习'
  }
  if (slot.slotType === 'self_study') {
    return '晚自习'
  }
  if (slot.slotType === 'break') {
    return '课间'
  }
  const hour = Number(slot.startTime?.slice(0, 2))
  if (!Number.isFinite(hour)) {
    return '其他'
  }
  if (hour < 12) {
    return '上午'
  }
  return hour < 18 ? '下午' : '晚上'
}

export const TIME_SLOT_SECTION_ORDER = ['早自习', '上午', '下午', '晚上', '课间', '其他']

export function weekdayLabel(weekday?: number | null) {
  switch (weekday) {
    case 1:
      return '周一'
    case 2:
      return '周二'
    case 3:
      return '周三'
    case 4:
      return '周四'
    case 5:
      return '周五'
    case 6:
      return '周六'
    case 7:
      return '周日'
    default:
      return '未知'
  }
}

export function lessonStatusLabel(status?: string | null) {
  switch (status) {
    case 'completed':
      return '已结课'
    case 'rescheduled':
      return '已调课'
    case 'makeup':
      return '已补课'
    case 'cancelled':
      return '已停课'
    case 'scheduled':
    default:
      return '已排课'
  }
}

// 已排课/已调课为常态用中性展示;黄色只留给已停课,红色留给待补课
export function lessonStatusTagType(status?: string | null) {
  switch (status) {
    case 'completed':
      return 'success'
    case 'makeup':
      return 'danger'
    case 'cancelled':
      return 'warning'
    case 'rescheduled':
    case 'scheduled':
    default:
      return 'info'
  }
}

export function attendanceStatusLabel(status?: string | null) {
  switch (status) {
    case 'present':
      return '到课'
    case 'absent':
      return '缺勤'
    case 'leave':
      return '请假'
    case 'late':
      return '迟到'
    default:
      return '未点名'
  }
}

export function attendanceStatusTagType(status?: string | null) {
  switch (status) {
    case 'present':
      return 'success'
    case 'absent':
      return 'danger'
    case 'leave':
      return 'info'
    case 'late':
      return 'warning'
    default:
      return ''
  }
}

export function conflictTypeLabel(type?: string | null) {
  switch (type) {
    case 'class':
      return '班级'
    case 'classroom':
      return '教室'
    case 'teacher':
      return '教师'
    default:
      return type || '未知'
  }
}

// 调课/补课课次仍要实际上课，可以点名；停课课次不产生考勤，已结课仅回查
export function isAttendanceAllowedStatus(status?: string | null) {
  return (
    status === 'scheduled' ||
    status === 'rescheduled' ||
    status === 'makeup' ||
    status === 'completed'
  )
}

export function canCompleteLesson(lesson: Pick<ScheduleLessonRecord, 'lessonStatus'>) {
  return (
    lesson.lessonStatus === 'scheduled' ||
    lesson.lessonStatus === 'rescheduled' ||
    lesson.lessonStatus === 'makeup'
  )
}

export function sortedLessons(values: ScheduleLessonRecord[]) {
  return [...values].sort((left, right) => {
    const leftKey = `${left.lessonDate || ''} ${left.startTime || ''} ${left.id || ''}`
    const rightKey = `${right.lessonDate || ''} ${right.startTime || ''} ${right.id || ''}`
    return leftKey.localeCompare(rightKey)
  })
}

// 结课/回退仍走全量 PUT（后端暂无独立状态端点）：先取详情再改写状态字段
export async function updateLessonCompletion(
  id: string,
  status: 'completed' | 'scheduled',
  remarkOverride?: string
) {
  const detail = await fetchScheduleLesson(id)
  await updateScheduleLesson(id, {
    institutionId: detail.institutionId || '',
    campusId: detail.campusId || '',
    termId: detail.termId || '',
    classId: detail.classId || '',
    classroomId: detail.classroomId || '',
    timeSlotId: detail.timeSlotId || '',
    lessonDate: detail.lessonDate || '',
    lessonTitle: detail.lessonTitle || undefined,
    lessonStatus: status,
    teacherIds: normalizeTeacherIds(detail.teacherIds),
    remark: remarkOverride ?? (detail.remark || undefined),
  })
}
