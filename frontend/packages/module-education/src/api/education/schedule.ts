import { requestDelete, requestGet, requestPost, requestPut } from '@cornerstone/core/api/request'
import type { PageParams, PageResult } from '@cornerstone/core/api/common'

/**
 * 教务 · 排课 的接口与类型（由 api/education.ts 按功能域拆出）。
 */

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
