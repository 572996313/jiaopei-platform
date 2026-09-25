<template>
  <div class="week-grid">
    <div class="week-grid-toolbar">
      <el-radio-group v-model="view" size="small">
        <el-radio-button label="date">按日期</el-radio-button>
        <el-radio-button label="classroom">按教室</el-radio-button>
        <el-radio-button label="teacher">按老师</el-radio-button>
      </el-radio-group>
      <el-select
        v-if="view === 'classroom'"
        v-model="focusClassroomId"
        class="week-grid-focus"
        size="small"
        clearable
        filterable
        placeholder="选择教室查看占用"
      >
        <el-option
          v-for="item in classrooms"
          :key="item.id"
          :label="item.classroomName || item.classroomCode || `教室 #${item.id}`"
          :value="item.id"
        />
      </el-select>
      <el-select
        v-if="view === 'teacher'"
        v-model="focusTeacherId"
        class="week-grid-focus"
        size="small"
        clearable
        filterable
        placeholder="选择老师查看课表"
      >
        <el-option
          v-for="item in teachers"
          :key="item.id"
          :label="item.teacherName || item.teacherCode || `教师 #${item.id}`"
          :value="item.id"
        />
      </el-select>
      <span v-if="focusHint" class="week-grid-focus-hint">{{ focusHint }}</span>
      <span class="week-grid-tip">点击空格可新增排课，点击课程卡可快捷操作</span>
    </div>

    <div v-loading="loading" class="week-grid-scroll">
      <div
        class="week-grid-board"
        :class="`is-${mode}`"
        :style="{ gridTemplateColumns: boardColumns }"
      >
        <div class="week-grid-cell is-corner">{{ mode === 'week' ? '时段' : '周次' }}</div>
        <div
          v-for="cell in headerCells"
          :key="cell.key"
          class="week-grid-cell is-day-head"
          :class="{ 'is-today': cell.isToday }"
        >
          <span>{{ cell.label }}</span>
          <el-tag v-if="cell.isToday" size="small" effect="light">今日</el-tag>
        </div>

        <template v-for="row in gridRows" :key="row.key">
          <div class="week-grid-cell is-slot" :class="{ 'is-week-label': !row.isTimeRow }">
            {{ row.label }}
          </div>
          <div
            v-for="day in row.days"
            :key="`${row.key}-${day.date}`"
            class="week-grid-cell is-slot-cell"
            :class="{ 'is-dimmed': day.dimmed }"
            :title="cellTitle(row, day)"
            @click="emitCellClick(day.date, row)"
          >
            <div v-if="!row.isTimeRow" class="week-grid-date" :class="{ 'is-dimmed': day.dimmed }">
              {{ compactDate(day.date) }}
            </div>
            <div v-if="rowLessons(row, day.date).length === 0" class="week-grid-empty">＋</div>
            <div v-else class="week-grid-cards">
              <el-popover
                v-for="lesson in rowLessons(row, day.date)"
                :key="lesson.id"
                placement="bottom-start"
                :width="280"
                trigger="click"
              >
                <template #reference>
                  <button
                    type="button"
                    class="week-grid-card"
                    :class="[cardClass(lesson), { 'is-compact': mode !== 'week' }]"
                    @click.stop
                  >
                    <span class="week-grid-card-title">
                      <span v-if="mode !== 'week'" class="week-grid-card-time">{{
                        timeLabel(lesson)
                      }}</span>
                      <span class="week-grid-card-class">{{ formatClass(lesson.classId) }}</span>
                      <span v-if="hasConflict(lesson)" class="week-grid-card-conflict">撞</span>
                    </span>
                    <span v-if="mode === 'week'" class="week-grid-card-meta">{{
                      formatClassroom(lesson.classroomId)
                    }}</span>
                    <span v-if="mode === 'week'" class="week-grid-card-meta">{{
                      formatTeachers(lesson.teacherIds)
                    }}</span>
                  </button>
                </template>

                <div class="week-grid-pop">
                  <div class="week-grid-pop-title">
                    <strong>{{ formatClass(lesson.classId) }}</strong>
                    <el-tag size="small" :type="statusTagType(lesson.lessonStatus)">
                      {{ statusLabel(lesson.lessonStatus) }}
                    </el-tag>
                  </div>
                  <div v-if="lesson.lessonTitle" class="week-grid-pop-line">
                    {{ lesson.lessonTitle }}
                  </div>
                  <div class="week-grid-pop-line">
                    {{ lesson.lessonDate }} {{ timeLabel(lesson) }} /
                    {{ formatClassroom(lesson.classroomId) }}
                  </div>
                  <div class="week-grid-pop-line">
                    教师：{{ formatTeachers(lesson.teacherIds) }}
                  </div>
                  <div v-if="relationSummary(lesson) !== '无关联'" class="week-grid-pop-line">
                    {{ relationSummary(lesson) }}
                  </div>
                  <div v-if="conflictText(lesson)" class="week-grid-pop-conflict">
                    {{ conflictText(lesson) }}
                  </div>
                  <div class="week-grid-pop-actions">
                    <el-button
                      size="small"
                      link
                      type="success"
                      :disabled="lesson.lessonStatus === 'cancelled'"
                      @click="emitAction('attendance', lesson)"
                    >
                      点名
                    </el-button>
                    <el-button
                      size="small"
                      link
                      type="warning"
                      :disabled="!canComplete(lesson)"
                      @click="emitAction('complete', lesson)"
                    >
                      结课
                    </el-button>
                    <el-button
                      size="small"
                      link
                      type="primary"
                      @click="emitAction('reschedule', lesson)"
                      >调课</el-button
                    >
                    <el-button size="small" link type="info" @click="emitAction('cancel', lesson)"
                      >停课</el-button
                    >
                    <el-button size="small" link type="danger" @click="emitAction('makeup', lesson)"
                      >补课</el-button
                    >
                    <el-button size="small" link type="primary" @click="emitAction('edit', lesson)"
                      >编辑</el-button
                    >
                  </div>
                </div>
              </el-popover>
            </div>
          </div>
        </template>
      </div>

      <div v-if="!loading && lessons.length === 0" class="education-empty">
        当前范围暂无排课，点击空格子即可新增。
      </div>
    </div>
  </div>
</template>

<script lang="ts">
// 类型通过普通 script 块导出，供父组件复用
export interface WeekGridDay {
  date: string
  title: string
  isToday: boolean
  /** 月/季视图里属于前后月（季）的补位日期，展示置灰 */
  dimmed?: boolean
}

export type WeekLessonActionType =
  'attendance' | 'complete' | 'reschedule' | 'cancel' | 'makeup' | 'edit'

export interface WeekLessonAction {
  type: WeekLessonActionType
  lesson: ScheduleLessonRecord
}
</script>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type {
  ScheduleLessonRecord,
  TeacherSummary,
} from '@cornerstone/module-education/api/education'
import type {
  ClassroomSummary,
  TimeSlotSummary,
} from '@cornerstone/module-organization/api/organization'

interface TimeRow {
  key: string
  label: string
  start: string
  end: string
  slotId?: string
}

interface GridRow {
  key: string
  label: string
  /** 周模式按"时段"匹配课次；月/季日历行按日期聚合全天课次 */
  isTimeRow: boolean
  start?: string
  end?: string
  slotId?: string
  days: WeekGridDay[]
}

const props = defineProps<{
  mode: 'week' | 'month' | 'quarter'
  days: WeekGridDay[]
  lessons: ScheduleLessonRecord[]
  timeSlots: TimeSlotSummary[]
  classrooms: ClassroomSummary[]
  teachers: TeacherSummary[]
  loading?: boolean
  formatClass: (id?: string | null) => string
  formatClassroom: (id?: string | null) => string
  formatTeachers: (ids?: Array<string | number> | null) => string
  statusLabel: (status?: string | null) => string
  statusTagType: (status?: string | null) => string
  relationSummary: (lesson?: ScheduleLessonRecord | null) => string
}>()

const emit = defineEmits<{
  (event: 'lesson-action', payload: WeekLessonAction): void
  (
    event: 'cell-click',
    payload: { date: string; timeSlotId?: string | null; classroomId?: string | null }
  ): void
}>()

const view = ref<'date' | 'classroom' | 'teacher'>('date')
const focusClassroomId = ref<string | null>(null)
const focusTeacherId = ref<string | null>(null)

const focusHint = computed(() => {
  if (view.value === 'classroom' && !focusClassroomId.value) {
    return '未选教室，当前展示全部课次'
  }
  if (view.value === 'teacher' && !focusTeacherId.value) {
    return '未选老师，当前展示全部课次'
  }
  return ''
})

const visibleLessons = computed(() => {
  if (view.value === 'classroom' && focusClassroomId.value) {
    return props.lessons.filter((lesson) => lesson.classroomId === focusClassroomId.value)
  }
  if (view.value === 'teacher' && focusTeacherId.value) {
    const teacherId = focusTeacherId.value
    return props.lessons.filter((lesson) =>
      normalizeTeacherIds(lesson.teacherIds).includes(teacherId)
    )
  }
  return props.lessons
})

const todayColumnWeekday = computed(() => {
  const day = new Date(`${todayValue()}T00:00:00`).getDay()
  return day === 0 ? 7 : day
})

// 表头：周模式为 7 个具体日期；月/季模式为周一~周日
const headerCells = computed(() => {
  if (props.mode === 'week') {
    return props.days.map((day) => ({ key: day.date, label: day.title, isToday: day.isToday }))
  }
  return ['周一', '周二', '周三', '周四', '周五', '周六', '周日'].map((label, index) => ({
    key: `weekday-${index}`,
    label,
    isToday: todayColumnWeekday.value === index + 1,
  }))
})

// 周模式：行 = 去重后的时段区间（时间槽与课次共同兜底）
const timeRows = computed<TimeRow[]>(() => {
  const rows = new Map<string, TimeRow>()

  const ensure = (start: string, end: string) => {
    const key = `${start}-${end}`
    let row = rows.get(key)
    if (!row) {
      row = { key, label: slotRangeLabel(start, end), start, end }
      rows.set(key, row)
    }
    return row
  }

  for (const slot of props.timeSlots) {
    if (!slot.startTime) {
      continue
    }
    const row = ensure(slot.startTime, slot.endTime || '')
    if (!row.slotId && slot.id) {
      row.slotId = slot.id
    }
  }
  for (const lesson of props.lessons) {
    if (!lesson.startTime) {
      continue
    }
    ensure(lesson.startTime, lesson.endTime || '')
  }

  return Array.from(rows.values()).sort(
    (left, right) => left.start.localeCompare(right.start) || left.end.localeCompare(right.end)
  )
})

const unslottedLessons = computed(() => visibleLessons.value.filter((lesson) => !lesson.startTime))

// 月/季模式：父组件给的日期已按周一~周日对齐，直接每 7 天切一行（日历周）
const calendarRows = computed<GridRow[]>(() => {
  const rows: GridRow[] = []
  for (let index = 0; index < props.days.length; index += 7) {
    const chunk = props.days.slice(index, index + 7)
    if (chunk.length === 0) {
      continue
    }
    const first = compactDate(chunk[0].date)
    const last = compactDate(chunk[chunk.length - 1].date)
    rows.push({
      key: `weekrow-${chunk[0].date}`,
      label: `第${rows.length + 1}周\n${first}~${last}`,
      isTimeRow: false,
      days: chunk,
    })
  }
  return rows
})

const gridRows = computed<GridRow[]>(() => {
  if (props.mode === 'week') {
    const rows: GridRow[] = timeRows.value.map((row) => ({
      key: row.key,
      label: row.label,
      isTimeRow: true,
      start: row.start,
      end: row.end,
      slotId: row.slotId,
      days: props.days,
    }))
    if (unslottedLessons.value.length > 0) {
      rows.push({
        key: '__unslotted__',
        label: '未分时段',
        isTimeRow: true,
        start: '',
        end: '',
        days: props.days,
      })
    }
    return rows
  }
  return calendarRows.value
})

const boardColumns = computed(
  () => `92px repeat(7, minmax(${props.mode === 'week' ? 128 : 104}px, 1fr))`
)

// 客户端近似冲突检测：同一天内同教室 / 同教师时段重叠（提交时仍以服务端校验为准）
const conflictReasons = computed(() => {
  const map = new Map<string, string[]>()

  const push = (lesson: ScheduleLessonRecord, reason: string) => {
    const list = map.get(lesson.id) || []
    if (!list.includes(reason)) {
      list.push(reason)
    }
    map.set(lesson.id, list)
  }

  const byDate = new Map<string, ScheduleLessonRecord[]>()
  for (const lesson of visibleLessons.value) {
    if (!lesson.lessonDate || !lesson.startTime) {
      continue
    }
    const list = byDate.get(lesson.lessonDate) || []
    list.push(lesson)
    byDate.set(lesson.lessonDate, list)
  }

  for (const dayLessons of byDate.values()) {
    for (let i = 0; i < dayLessons.length; i++) {
      for (let j = i + 1; j < dayLessons.length; j++) {
        const left = dayLessons[i]
        const right = dayLessons[j]
        if (!timeOverlaps(left, right)) {
          continue
        }
        if (left.classroomId && left.classroomId === right.classroomId) {
          const reason = `教室 ${props.formatClassroom(left.classroomId)} 时段重叠`
          push(left, reason)
          push(right, reason)
        }
        const leftTeachers = normalizeTeacherIds(left.teacherIds)
        const sharedTeachers = leftTeachers.filter((id) =>
          normalizeTeacherIds(right.teacherIds).includes(id)
        )
        if (sharedTeachers.length > 0) {
          const reason = `教师 ${sharedTeachers.map((id) => props.formatTeachers([id])).join('、')} 时段重叠`
          push(left, reason)
          push(right, reason)
        }
      }
    }
  }

  return map
})

function normalizeTeacherIds(values?: Array<string | number> | null) {
  return Array.from(new Set((values || []).map((item) => String(item))))
}

function todayValue() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}

function slotRangeLabel(start: string, end: string) {
  if (!end) {
    return start.slice(0, 5)
  }
  return `${start.slice(0, 5)}-${end.slice(0, 5)}`
}

function compactDate(value: string) {
  return value.slice(5)
}

function timeOverlaps(left: ScheduleLessonRecord, right: ScheduleLessonRecord) {
  if (!left.startTime || !right.startTime) {
    return false
  }
  const leftEnd = left.endTime || left.startTime
  const rightEnd = right.endTime || right.startTime
  return left.startTime < rightEnd && right.startTime < leftEnd
}

function hasConflict(lesson: ScheduleLessonRecord) {
  return (conflictReasons.value.get(lesson.id)?.length || 0) > 0
}

function conflictText(lesson: ScheduleLessonRecord) {
  const reasons = conflictReasons.value.get(lesson.id) || []
  return reasons.length > 0 ? `疑似冲突：${reasons.join('；')}` : ''
}

function rowLessons(row: GridRow, date: string) {
  return visibleLessons.value.filter((lesson) => {
    if (lesson.lessonDate !== date) {
      return false
    }
    if (!row.isTimeRow) {
      return true
    }
    if (!row.start) {
      return !lesson.startTime
    }
    return lesson.startTime === row.start && (lesson.endTime || '') === row.end
  })
}

function cardClass(lesson: ScheduleLessonRecord) {
  return `is-${lesson.lessonStatus || 'scheduled'}`
}

// 调课/补课课次仍要实际上课，可结课；停课课次不产生考勤
function canComplete(lesson: Pick<ScheduleLessonRecord, 'lessonStatus'>) {
  return (
    lesson.lessonStatus === 'scheduled' ||
    lesson.lessonStatus === 'rescheduled' ||
    lesson.lessonStatus === 'makeup'
  )
}

function timeLabel(lesson: ScheduleLessonRecord) {
  return slotRangeLabel(lesson.startTime || '', lesson.endTime || '') || '待定'
}

function cellTitle(row: GridRow, day: WeekGridDay) {
  const prefix = `新增排课：${day.date}`
  return row.isTimeRow ? `${prefix} ${row.label}` : prefix
}

function emitAction(type: WeekLessonActionType, lesson: ScheduleLessonRecord) {
  emit('lesson-action', { type, lesson })
}

function emitCellClick(date: string, row: GridRow) {
  const classroomId =
    view.value === 'classroom' && focusClassroomId.value ? focusClassroomId.value : null
  emit('cell-click', { date, timeSlotId: row.isTimeRow ? row.slotId || null : null, classroomId })
}
</script>

<style scoped>
.week-grid {
  display: grid;
  gap: 12px;
}

.week-grid-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.week-grid-focus {
  width: 200px;
}

.week-grid-focus-hint {
  color: var(--text-faint);
  font-size: 12px;
}

.week-grid-tip {
  margin-left: auto;
  color: var(--text-faint);
  font-size: 12px;
}

.week-grid-scroll {
  overflow-x: auto;
}

.week-grid-board {
  display: grid;
  gap: 6px;
  min-width: 980px;
}

.week-grid-cell {
  min-height: 68px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--card-bg);
  font-size: 12px;
}

/* 月/季日历模式：单元格更密，季度进一步压缩 */
.week-grid-board.is-month .week-grid-cell {
  min-height: 84px;
}

.week-grid-board.is-quarter .week-grid-cell {
  min-height: 62px;
}

.week-grid-cell.is-corner,
.week-grid-cell.is-slot {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 6px 8px;
  background: var(--subtle-bg);
  color: var(--text-strong);
  font-weight: 600;
  white-space: nowrap;
}

.week-grid-cell.is-slot.is-week-label {
  white-space: pre-line;
  line-height: 1.5;
  text-align: center;
}

.week-grid-cell.is-day-head {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px;
  background: var(--subtle-bg);
  color: var(--text-primary);
  font-weight: 700;
  white-space: nowrap;
}

.week-grid-cell.is-day-head.is-today {
  border-color: var(--hover-line);
  background: var(--hover-bg);
}

.week-grid-cell.is-slot-cell {
  display: grid;
  align-content: start;
  gap: 4px;
  padding: 6px;
  cursor: pointer;
  transition:
    border-color 0.15s,
    background 0.15s;
}

.week-grid-cell.is-slot-cell:hover {
  border-color: var(--hover-line);
  background: var(--tint-bg);
}

.week-grid-cell.is-slot-cell.is-dimmed {
  background: var(--subtle-bg);
}

.week-grid-date {
  text-align: right;
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 600;
}

.week-grid-date.is-dimmed {
  color: var(--text-faint);
}

.week-grid-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 52px;
  color: var(--text-faint);
  font-size: 16px;
}

/* 月/季模式下空格提示只在悬停时出现，避免整屏 ＋ 太吵 */
.is-month .week-grid-empty,
.is-quarter .week-grid-empty {
  min-height: 32px;
  opacity: 0;
  transition: opacity 0.15s;
}

.is-month .week-grid-cell.is-slot-cell:hover .week-grid-empty,
.is-quarter .week-grid-cell.is-slot-cell:hover .week-grid-empty {
  opacity: 1;
}

.week-grid-cards {
  display: grid;
  gap: 6px;
}

.week-grid-card {
  display: grid;
  gap: 2px;
  width: 100%;
  padding: 8px;
  border: 1px solid var(--border);
  border-left: 3px solid var(--accent);
  border-radius: 8px;
  background: var(--card-bg);
  color: var(--text-strong);
  cursor: pointer;
  font-size: 12px;
  text-align: left;
}

/* 左边框色跟随课次状态：已排课蓝 / 已结课绿 / 已调课灰 / 已停课黄 / 已补课红 */
.week-grid-card.is-scheduled {
  border-left-color: var(--accent);
}

.week-grid-card.is-completed {
  border-left-color: var(--el-color-success);
}

.week-grid-card.is-rescheduled {
  border-left-color: var(--el-color-info);
}

.week-grid-card.is-cancelled {
  border-left-color: var(--el-color-warning);
  background: rgba(250, 219, 20, 0.08);
}

.week-grid-card.is-makeup {
  border-left-color: var(--el-color-danger);
}

.week-grid-card.is-compact {
  gap: 1px;
  padding: 3px 6px;
  font-size: 11px;
}

.week-grid-card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  color: var(--text-primary);
  font-weight: 700;
}

.week-grid-card-time {
  flex-shrink: 0;
  color: var(--text-secondary);
  font-weight: 600;
}

.week-grid-card-class {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.week-grid-card-meta {
  color: var(--text-secondary);
}

.week-grid-card-conflict {
  flex-shrink: 0;
  padding: 0 5px;
  border-radius: 6px;
  background: var(--soft-danger-bg);
  color: var(--soft-danger-ink);
  font-size: 11px;
  font-weight: 600;
}

.week-grid-pop-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.week-grid-pop-line {
  margin-bottom: 4px;
  color: var(--text-secondary);
  font-size: 12px;
}

.week-grid-pop-conflict {
  margin: 6px 0;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--soft-danger-bg);
  color: var(--soft-danger-ink);
  font-size: 12px;
}

.week-grid-pop-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 8px;
  margin-top: 8px;
}

.week-grid-pop-actions .el-button + .el-button {
  margin-left: 0;
}
</style>
