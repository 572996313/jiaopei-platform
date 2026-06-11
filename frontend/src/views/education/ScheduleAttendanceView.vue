<template>
  <section class="education-page">
    <div class="education-hero">
      <div>
        <div class="page-kicker">SCHEDULE / TIMETABLE</div>
        <h2 class="panel-headline">排课与考勤</h2>
        <p class="panel-subtitle">
          当前页面先承接排课主线最小闭环。围绕正式排课接口完成列表、创建编辑删除、冲突检查和日周课表展示。
        </p>
      </div>

      <div class="education-hero-actions">
        <el-button @click="refreshAll" :loading="lookupLoading || lessonsLoading || dayLoading || weekLoading">
          刷新数据
        </el-button>
        <el-button type="primary" @click="openCreateDialog">新增排课</el-button>
      </div>
    </div>

    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <div class="dashboard-grid education-stat-grid">
      <article class="stat-card">
        <div class="stat-label">机构数</div>
        <div class="stat-value">{{ institutions.length }}</div>
        <div class="stat-hint">来自 /api/organizations/institutions</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">班级数</div>
        <div class="stat-value">{{ classes.length }}</div>
        <div class="stat-hint">当前机构/校区/学期范围</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">教师数</div>
        <div class="stat-value">{{ teachers.length }}</div>
        <div class="stat-hint">来自 /api/education/teachers</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">排课记录</div>
        <div class="stat-value">{{ lessons.length }}</div>
        <div class="stat-hint">来自 /api/education/schedules/lessons</div>
      </article>
    </div>

    <article class="panel-card education-panel">
      <div class="panel-title">当前范围</div>
      <div class="education-toolbar" v-loading="lookupLoading">
        <el-select v-model="institutionId" placeholder="选择机构" clearable filterable @change="handleInstitutionChange">
          <el-option
            v-for="item in institutions"
            :key="item.id"
            :label="item.orgName || item.shortName || item.orgCode || `机构 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <el-select v-model="campusId" placeholder="选择校区" clearable filterable @change="handleCampusChange">
          <el-option
            v-for="item in campuses"
            :key="item.id"
            :label="item.campusName || item.campusCode || `校区 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <el-select v-model="termId" placeholder="选择学期" clearable filterable @change="handleTermChange">
          <el-option
            v-for="item in terms"
            :key="item.id"
            :label="item.termName || item.termCode || `学期 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <el-select v-model="classId" placeholder="选择班级" clearable filterable @change="refreshAll">
          <el-option
            v-for="item in classes"
            :key="item.id"
            :label="item.className || item.classCode || `班级 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <el-date-picker
          v-model="lessonDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="列表/日课表日期"
          @change="refreshLessonsAndDay"
        />
        <el-date-picker
          v-model="weekStart"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="周开始"
          @change="handleWeekStartChange"
        />
        <el-date-picker
          v-model="weekEnd"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="周结束"
          @change="refreshWeekTimetable"
        />
      </div>
      <div class="education-context-text">
        创建或编辑排课前会先做冲突检查，班级、教师、教室冲突会在弹窗内明确展示并阻止保存。
      </div>
    </article>

    <article class="panel-card education-panel">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="排课管理" name="management">
          <div class="education-table-head">
            <div class="panel-title">排课列表</div>
            <div class="education-context-text">
              当前机构：{{ formatInstitution(institutionId) }}，校区：{{ formatCampus(campusId) }}，班级：{{ formatClass(classId) }}
            </div>
          </div>

          <el-table :data="lessons" border v-loading="lessonsLoading">
            <el-table-column prop="lessonDate" label="上课日期" width="120" />
            <el-table-column label="星期" width="100">
              <template #default="{ row }">
                {{ weekdayLabel(row.lessonWeekday) }}
              </template>
            </el-table-column>
            <el-table-column label="班级" min-width="160">
              <template #default="{ row }">
                {{ formatClass(row.classId) }}
              </template>
            </el-table-column>
            <el-table-column label="教室" min-width="150">
              <template #default="{ row }">
                {{ formatClassroom(row.classroomId) }}
              </template>
            </el-table-column>
            <el-table-column label="时段" min-width="170">
              <template #default="{ row }">
                {{ formatTimeSlot(row.timeSlotId, row.startTime, row.endTime) }}
              </template>
            </el-table-column>
            <el-table-column label="教师" min-width="200">
              <template #default="{ row }">
                {{ formatTeacherNames(row.teacherIds) }}
              </template>
            </el-table-column>
            <el-table-column prop="lessonTitle" label="课程主题" min-width="160" />
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="lessonStatusTagType(row.lessonStatus)">
                  {{ lessonStatusLabel(row.lessonStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="180" />
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
                  <el-button
                    link
                    type="danger"
                    :loading="deletingLessonId === row.id"
                    @click="removeLesson(row)"
                  >
                    删除
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="!lessonsLoading && lessons.length === 0" class="education-empty">
            当前筛选范围下暂无排课记录。
          </div>
        </el-tab-pane>

        <el-tab-pane label="课表视图" name="timetable">
          <el-tabs v-model="timetableTab">
            <el-tab-pane label="日课表" name="day">
              <div class="education-table-head">
                <div class="panel-title">日课表</div>
                <div class="education-context-text">课表日期：{{ lessonDate || '未选择' }}</div>
              </div>

              <el-table :data="dayLessons" border v-loading="dayLoading">
                <el-table-column label="时段" min-width="170">
                  <template #default="{ row }">
                    {{ formatTimeSlot(row.timeSlotId, row.startTime, row.endTime) }}
                  </template>
                </el-table-column>
                <el-table-column label="班级" min-width="160">
                  <template #default="{ row }">
                    {{ formatClass(row.classId) }}
                  </template>
                </el-table-column>
                <el-table-column label="教室" min-width="150">
                  <template #default="{ row }">
                    {{ formatClassroom(row.classroomId) }}
                  </template>
                </el-table-column>
                <el-table-column label="教师" min-width="200">
                  <template #default="{ row }">
                    {{ formatTeacherNames(row.teacherIds) }}
                  </template>
                </el-table-column>
                <el-table-column prop="lessonTitle" label="课程主题" min-width="160" />
                <el-table-column label="状态" width="120">
                  <template #default="{ row }">
                    <el-tag :type="lessonStatusTagType(row.lessonStatus)">
                      {{ lessonStatusLabel(row.lessonStatus) }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>

              <div v-if="!dayLoading && dayLessons.length === 0" class="education-empty">
                当前日期下暂无排课。
              </div>
            </el-tab-pane>

            <el-tab-pane label="周课表" name="week">
              <div class="education-table-head">
                <div class="panel-title">周课表</div>
                <div class="education-context-text">范围：{{ weekStart || '未选择' }} 至 {{ weekEnd || '未选择' }}</div>
              </div>

              <div v-loading="weekLoading" class="week-board">
                <div v-for="group in weekTimetableGroups" :key="group.date" class="week-board-day">
                  <div class="week-board-title">{{ group.title }}</div>
                  <div v-if="group.lessons.length === 0" class="week-board-empty">无排课</div>
                  <div v-for="lesson in group.lessons" :key="lesson.id" class="week-board-card">
                    <div class="week-board-main">
                      <strong>{{ formatClass(lesson.classId) }}</strong>
                      <el-tag size="small" :type="lessonStatusTagType(lesson.lessonStatus)">
                        {{ lessonStatusLabel(lesson.lessonStatus) }}
                      </el-tag>
                    </div>
                    <div>{{ formatTimeSlot(lesson.timeSlotId, lesson.startTime, lesson.endTime) }}</div>
                    <div>{{ formatClassroom(lesson.classroomId) }}</div>
                    <div>{{ formatTeacherNames(lesson.teacherIds) }}</div>
                    <div v-if="lesson.lessonTitle">{{ lesson.lessonTitle }}</div>
                  </div>
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-tab-pane>
      </el-tabs>
    </article>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="680px" destroy-on-close>
      <div class="education-dialog-context">
        <div><strong>机构：</strong>{{ formatInstitution(form.institutionId) }}</div>
        <div><strong>校区：</strong>{{ formatCampus(form.campusId) }}</div>
        <div><strong>学期：</strong>{{ formatTerm(form.termId) }}</div>
      </div>

      <el-alert v-if="formError" :title="formError" type="error" show-icon :closable="false" class="education-alert" />

      <el-alert
        v-if="conflictResult?.conflict"
        title="冲突检查未通过，请调整后再保存"
        type="warning"
        show-icon
        :closable="false"
        class="education-alert"
      />

      <div v-if="conflictResult?.conflict" class="schedule-conflict-list">
        <div
          v-for="(message, index) in normalizedConflictMessages"
          :key="`${message}-${index}`"
          class="schedule-conflict-item"
        >
          {{ message }}
        </div>
      </div>

      <el-form label-position="top" class="education-form">
        <div class="schedule-form-grid">
          <el-form-item label="机构">
            <el-select v-model="form.institutionId" disabled>
              <el-option
                v-for="item in institutions"
                :key="item.id"
                :label="item.orgName || item.shortName || item.orgCode || `机构 #${item.id}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="校区">
            <el-select v-model="form.campusId" disabled>
              <el-option
                v-for="item in campuses"
                :key="item.id"
                :label="item.campusName || item.campusCode || `校区 #${item.id}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="学期">
            <el-select v-model="form.termId" disabled>
              <el-option
                v-for="item in terms"
                :key="item.id"
                :label="item.termName || item.termCode || `学期 #${item.id}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="班级">
            <el-select v-model="form.classId" placeholder="选择班级" filterable>
              <el-option
                v-for="item in classes"
                :key="item.id"
                :label="item.className || item.classCode || `班级 #${item.id}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="教室">
            <el-select v-model="form.classroomId" placeholder="选择教室" filterable>
              <el-option
                v-for="item in classrooms"
                :key="item.id"
                :label="item.classroomName || item.classroomCode || `教室 #${item.id}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="时间段">
            <el-select v-model="form.timeSlotId" placeholder="选择时间段" filterable>
              <el-option
                v-for="item in timeSlots"
                :key="item.id"
                :label="timeSlotOptionLabel(item.slotName, item.startTime, item.endTime, item.weekday)"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="上课日期">
            <el-date-picker
              v-model="form.lessonDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择上课日期"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="排课状态">
            <el-select v-model="form.lessonStatus" placeholder="选择状态">
              <el-option label="已排课" value="scheduled" />
              <el-option label="已完成" value="completed" />
              <el-option label="已取消" value="cancelled" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="教师（可多选）">
          <el-select v-model="form.teacherIds" multiple collapse-tags collapse-tags-tooltip filterable placeholder="选择教师">
            <el-option
              v-for="item in teachers"
              :key="item.id"
              :label="item.teacherName || item.teacherCode || `教师 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="课程主题">
          <el-input v-model="form.lessonTitle" placeholder="例如 周三数学强化课" maxlength="128" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="补充排课说明" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitLesson">
          {{ dialogMode === 'create' ? '检查冲突并创建' : '检查冲突并保存' }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  checkScheduleConflicts,
  createScheduleLesson,
  deleteScheduleLesson,
  fetchDayTimetable,
  fetchScheduleLesson,
  fetchScheduleLessons,
  fetchTeachers,
  fetchWeekTimetable,
  updateScheduleLesson,
  type ScheduleConflictCheckResult,
  type ScheduleLessonPayload,
  type ScheduleLessonRecord,
  type TeacherSummary,
} from '@/api/education'
import { fetchClassrooms, fetchTimeSlots, type ClassroomSummary, type TimeSlotSummary } from '@/api/organization'
import { useEducationScope } from '@/composables/useEducationScope'

const { institutions, campuses, terms, classes, loadInstitutions, loadScope } = useEducationScope()

const teachers = ref<TeacherSummary[]>([])
const classrooms = ref<ClassroomSummary[]>([])
const timeSlots = ref<TimeSlotSummary[]>([])

const institutionId = ref<string | null>(null)
const campusId = ref<string | null>(null)
const termId = ref<string | null>(null)
const classId = ref<string | null>(null)
const lessonDate = ref(todayValue())
const weekStart = ref(startOfWeekValue())
const weekEnd = ref(endOfWeekValue(weekStart.value))
const activeTab = ref('management')
const timetableTab = ref('day')

const lessons = ref<ScheduleLessonRecord[]>([])
const dayLessons = ref<ScheduleLessonRecord[]>([])
const weekLessons = ref<ScheduleLessonRecord[]>([])

const pageError = ref('')
const formError = ref('')
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingLessonId = ref<string | null>(null)
const lookupLoading = ref(false)
const lessonsLoading = ref(false)
const dayLoading = ref(false)
const weekLoading = ref(false)
const submitting = ref(false)
const deletingLessonId = ref<string | null>(null)
const conflictResult = ref<ScheduleConflictCheckResult | null>(null)

const form = reactive({
  institutionId: '',
  campusId: '',
  termId: '',
  classId: '',
  classroomId: '',
  timeSlotId: '',
  lessonDate: todayValue(),
  lessonTitle: '',
  lessonStatus: 'scheduled',
  teacherIds: [] as string[],
  remark: '',
})

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增排课' : '编辑排课'))

const institutionMap = computed(() => {
  return new Map(
    institutions.value.map((item) => [item.id, item.orgName || item.shortName || item.orgCode || `机构 #${item.id}`]),
  )
})

const campusMap = computed(() => {
  return new Map(campuses.value.map((item) => [item.id, item.campusName || item.campusCode || `校区 #${item.id}`]))
})

const termMap = computed(() => {
  return new Map(terms.value.map((item) => [item.id, item.termName || item.termCode || `学期 #${item.id}`]))
})

const classMap = computed(() => {
  return new Map(classes.value.map((item) => [item.id, item.className || item.classCode || `班级 #${item.id}`]))
})

const classroomMap = computed(() => {
  return new Map(
    classrooms.value.map((item) => [item.id, item.classroomName || item.classroomCode || `教室 #${item.id}`]),
  )
})

const teacherMap = computed(() => {
  return new Map(teachers.value.map((item) => [item.id, item.teacherName || item.teacherCode || `教师 #${item.id}`]))
})

const timeSlotMap = computed(() => {
  return new Map(timeSlots.value.map((item) => [item.id, item]))
})

const normalizedConflictMessages = computed(() => {
  if (!conflictResult.value?.conflict) {
    return []
  }

  if (conflictResult.value.messages && conflictResult.value.messages.length > 0) {
    return conflictResult.value.messages
  }

  return (conflictResult.value.conflictTypes || []).map((item) => `${conflictTypeLabel(item)}冲突`)
})

const weekTimetableGroups = computed(() => {
  const groups = new Map<string, ScheduleLessonRecord[]>()
  const dates = enumerateDates(weekStart.value, weekEnd.value)

  for (const date of dates) {
    groups.set(date, [])
  }

  for (const lesson of sortedLessons(weekLessons.value)) {
    const key = lesson.lessonDate || ''
    if (!groups.has(key)) {
      groups.set(key, [])
    }
    groups.get(key)?.push(lesson)
  }

  return Array.from(groups.entries()).map(([date, items]) => ({
    date,
    title: `${date || '未命名日期'} ${weekdayLabel(weekdayFromDate(date))}`,
    lessons: items,
  }))
})

function todayValue() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}

function startOfWeekValue() {
  return startOfWeek(todayValue())
}

function endOfWeekValue(baseDate: string) {
  return addDays(baseDate, 6)
}

function startOfWeek(value: string) {
  const date = new Date(`${value}T00:00:00`)
  const day = date.getDay()
  const diff = day === 0 ? -6 : 1 - day
  date.setDate(date.getDate() + diff)
  return formatDate(date)
}

function addDays(value: string, days: number) {
  const date = new Date(`${value}T00:00:00`)
  date.setDate(date.getDate() + days)
  return formatDate(date)
}

function formatDate(date: Date) {
  const offset = date.getTimezoneOffset() * 60000
  return new Date(date.getTime() - offset).toISOString().slice(0, 10)
}

function enumerateDates(start: string, end: string) {
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

function weekdayFromDate(value?: string | null) {
  if (!value) {
    return null
  }
  const weekday = new Date(`${value}T00:00:00`).getDay()
  return weekday === 0 ? 7 : weekday
}

function trimToUndefined(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function normalizeTeacherIds(values?: Array<string | number> | null) {
  return Array.from(new Set((values || []).map((item) => String(item))))
}

function timeSlotOptionLabel(name?: string | null, startTime?: string | null, endTime?: string | null, weekday?: number | null) {
  const label = name || '时间段'
  const timeRange = compactTimeRange(startTime, endTime)
  const week = weekdayLabel(weekday)
  return [label, timeRange, week === '未知' ? '' : week].filter(Boolean).join(' / ')
}

function compactTimeRange(startTime?: string | null, endTime?: string | null) {
  if (startTime && endTime) {
    return `${startTime.slice(0, 5)}-${endTime.slice(0, 5)}`
  }
  return startTime?.slice(0, 5) || endTime?.slice(0, 5) || ''
}

function sortedLessons(values: ScheduleLessonRecord[]) {
  return [...values].sort((left, right) => {
    const leftKey = `${left.lessonDate || ''} ${left.startTime || ''} ${left.id || ''}`
    const rightKey = `${right.lessonDate || ''} ${right.startTime || ''} ${right.id || ''}`
    return leftKey.localeCompare(rightKey)
  })
}

function formatInstitution(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return institutionMap.value.get(id) || `机构 #${id}`
}

function formatCampus(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return campusMap.value.get(id) || `校区 #${id}`
}

function formatTerm(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return termMap.value.get(id) || `学期 #${id}`
}

function formatClass(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return classMap.value.get(id) || `班级 #${id}`
}

function formatClassroom(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return classroomMap.value.get(id) || `教室 #${id}`
}

function formatTimeSlot(id?: string | null, startTime?: string | null, endTime?: string | null) {
  if (id) {
    const slot = timeSlotMap.value.get(id)
    if (slot) {
      return timeSlotOptionLabel(slot.slotName, slot.startTime, slot.endTime, slot.weekday)
    }
  }

  const fallbackRange = compactTimeRange(startTime, endTime)
  return fallbackRange || (id ? `时间段 #${id}` : '未选择')
}

function formatTeacherNames(values?: Array<string | number> | null) {
  const ids = normalizeTeacherIds(values)
  if (ids.length === 0) {
    return '未选择'
  }
  return ids.map((id) => teacherMap.value.get(id) || `教师 #${id}`).join('、')
}

function weekdayLabel(weekday?: number | null) {
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

function lessonStatusLabel(status?: string | null) {
  switch (status) {
    case 'completed':
      return '已完成'
    case 'cancelled':
      return '已取消'
    case 'scheduled':
    default:
      return '已排课'
  }
}

function lessonStatusTagType(status?: string | null) {
  switch (status) {
    case 'completed':
      return 'success'
    case 'cancelled':
      return 'info'
    case 'scheduled':
    default:
      return 'warning'
  }
}

function conflictTypeLabel(type?: string | null) {
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

function resetForm() {
  form.institutionId = institutionId.value || ''
  form.campusId = campusId.value || ''
  form.termId = termId.value || ''
  form.classId = classId.value || ''
  form.classroomId = ''
  form.timeSlotId = ''
  form.lessonDate = lessonDate.value || todayValue()
  form.lessonTitle = ''
  form.lessonStatus = 'scheduled'
  form.teacherIds = []
  form.remark = ''
  editingLessonId.value = null
  conflictResult.value = null
  formError.value = ''
}

function populateForm(lesson: ScheduleLessonRecord) {
  form.institutionId = lesson.institutionId || institutionId.value || ''
  form.campusId = lesson.campusId || campusId.value || ''
  form.termId = lesson.termId || termId.value || ''
  form.classId = lesson.classId || ''
  form.classroomId = lesson.classroomId || ''
  form.timeSlotId = lesson.timeSlotId || ''
  form.lessonDate = lesson.lessonDate || lessonDate.value || todayValue()
  form.lessonTitle = lesson.lessonTitle || ''
  form.lessonStatus = lesson.lessonStatus || 'scheduled'
  form.teacherIds = normalizeTeacherIds(lesson.teacherIds)
  form.remark = lesson.remark || ''
  conflictResult.value = null
  formError.value = ''
}

function validateDialogContext() {
  if (!institutionId.value) {
    return '请先选择机构范围'
  }
  if (!campusId.value) {
    return '请先选择校区范围'
  }
  if (!termId.value) {
    return '请先选择学期范围'
  }
  return ''
}

function validateForm() {
  if (!form.institutionId) {
    return '缺少机构信息'
  }
  if (!form.campusId) {
    return '缺少校区信息'
  }
  if (!form.termId) {
    return '缺少学期信息'
  }
  if (!form.classId) {
    return '请选择班级'
  }
  if (!form.classroomId) {
    return '请选择教室'
  }
  if (!form.timeSlotId) {
    return '请选择时间段'
  }
  if (!form.lessonDate) {
    return '请选择上课日期'
  }
  if (form.teacherIds.length === 0) {
    return '请至少选择一位教师'
  }
  return ''
}

function buildLessonPayload(): ScheduleLessonPayload {
  return {
    institutionId: form.institutionId,
    campusId: form.campusId,
    termId: form.termId,
    classId: form.classId,
    classroomId: form.classroomId,
    timeSlotId: form.timeSlotId,
    lessonDate: form.lessonDate,
    lessonTitle: trimToUndefined(form.lessonTitle),
    lessonStatus: trimToUndefined(form.lessonStatus),
    teacherIds: normalizeTeacherIds(form.teacherIds),
    remark: trimToUndefined(form.remark),
  }
}

async function loadSupportingOptions() {
  if (!institutionId.value) {
    teachers.value = []
    classrooms.value = []
    timeSlots.value = []
    return
  }

  const [teacherList, classroomList, timeSlotList] = await Promise.all([
    fetchTeachers({ institutionId: institutionId.value, campusId: campusId.value }),
    campusId.value ? fetchClassrooms(campusId.value) : Promise.resolve([]),
    fetchTimeSlots(institutionId.value),
  ])

  teachers.value = teacherList
  classrooms.value = classroomList
  timeSlots.value = timeSlotList
}

async function refreshScopeData() {
  if (!institutionId.value) {
    campuses.value = []
    terms.value = []
    classes.value = []
    teachers.value = []
    classrooms.value = []
    timeSlots.value = []
    classId.value = null
    termId.value = null
    return
  }

  lookupLoading.value = true
  pageError.value = ''
  try {
    await loadScope(institutionId.value, campusId.value, termId.value)
    await loadSupportingOptions()

    if (campusId.value && !campuses.value.some((item) => item.id === campusId.value)) {
      campusId.value = null
      await loadSupportingOptions()
    }
    if (termId.value && !terms.value.some((item) => item.id === termId.value)) {
      termId.value = null
      await loadScope(institutionId.value, campusId.value, termId.value)
    }
    if (classId.value && !classes.value.some((item) => item.id === classId.value)) {
      classId.value = null
    }
  } catch (error) {
    pageError.value = toMessage(error, '范围数据加载失败')
  } finally {
    lookupLoading.value = false
  }
}

async function refreshLessons() {
  if (!institutionId.value) {
    lessons.value = []
    return
  }

  lessonsLoading.value = true
  pageError.value = ''
  try {
    lessons.value = sortedLessons(
      await fetchScheduleLessons({
        institutionId: institutionId.value,
        campusId: campusId.value,
        classId: classId.value,
        lessonDate: lessonDate.value,
      }),
    )
  } catch (error) {
    lessons.value = []
    pageError.value = toMessage(error, '排课列表加载失败')
  } finally {
    lessonsLoading.value = false
  }
}

async function refreshDayTimetable() {
  if (!lessonDate.value) {
    dayLessons.value = []
    return
  }

  dayLoading.value = true
  pageError.value = ''
  try {
    dayLessons.value = sortedLessons(
      await fetchDayTimetable({
        institutionId: institutionId.value,
        campusId: campusId.value,
        lessonDate: lessonDate.value,
      }),
    )
  } catch (error) {
    dayLessons.value = []
    pageError.value = toMessage(error, '日课表加载失败')
  } finally {
    dayLoading.value = false
  }
}

async function refreshWeekTimetable() {
  if (!weekStart.value || !weekEnd.value || weekStart.value > weekEnd.value) {
    weekLessons.value = []
    return
  }

  weekLoading.value = true
  pageError.value = ''
  try {
    weekLessons.value = sortedLessons(
      await fetchWeekTimetable({
        institutionId: institutionId.value,
        campusId: campusId.value,
        weekStart: weekStart.value,
        weekEnd: weekEnd.value,
      }),
    )
  } catch (error) {
    weekLessons.value = []
    pageError.value = toMessage(error, '周课表加载失败')
  } finally {
    weekLoading.value = false
  }
}

async function refreshLessonsAndDay() {
  await Promise.all([refreshLessons(), refreshDayTimetable()])
}

async function refreshAll() {
  await Promise.all([refreshLessons(), refreshDayTimetable(), refreshWeekTimetable()])
}

async function handleInstitutionChange() {
  campusId.value = null
  termId.value = null
  classId.value = null
  await refreshScopeData()
  await refreshAll()
}

async function handleCampusChange() {
  classId.value = null
  await refreshScopeData()
  await refreshAll()
}

async function handleTermChange() {
  classId.value = null
  await refreshScopeData()
  await refreshAll()
}

async function handleWeekStartChange() {
  if (weekStart.value) {
    weekEnd.value = endOfWeekValue(weekStart.value)
  }
  await refreshWeekTimetable()
}

function openCreateDialog() {
  const contextError = validateDialogContext()
  if (contextError) {
    ElMessage.warning(contextError)
    return
  }

  resetForm()
  dialogMode.value = 'create'
  dialogVisible.value = true
}

async function openEditDialog(lesson: ScheduleLessonRecord) {
  dialogMode.value = 'edit'
  editingLessonId.value = lesson.id
  formError.value = ''
  conflictResult.value = null

  try {
    const detail = await fetchScheduleLesson(lesson.id)
    populateForm(detail)
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(toMessage(error, '排课详情加载失败'))
  }
}

async function runConflictCheck() {
  const payload = buildLessonPayload()
  conflictResult.value = await checkScheduleConflicts({
    excludeLessonId: dialogMode.value === 'edit' ? editingLessonId.value || undefined : undefined,
    institutionId: payload.institutionId,
    campusId: payload.campusId,
    termId: payload.termId,
    classId: payload.classId,
    classroomId: payload.classroomId,
    timeSlotId: payload.timeSlotId,
    lessonDate: payload.lessonDate,
    teacherIds: payload.teacherIds,
  })

  if (conflictResult.value.conflict) {
    formError.value = '存在排课冲突，请调整后重试'
    return false
  }

  return true
}

async function submitLesson() {
  const validationError = validateForm()
  if (validationError) {
    formError.value = validationError
    return
  }

  submitting.value = true
  formError.value = ''
  conflictResult.value = null

  try {
    const passed = await runConflictCheck()
    if (!passed) {
      return
    }

    const payload = buildLessonPayload()
    if (dialogMode.value === 'create') {
      await createScheduleLesson(payload)
      ElMessage.success('排课已创建')
    } else if (editingLessonId.value) {
      await updateScheduleLesson(editingLessonId.value, payload)
      ElMessage.success('排课已更新')
    }

    dialogVisible.value = false
    await refreshAll()
  } catch (error) {
    formError.value = toMessage(error, dialogMode.value === 'create' ? '新增排课失败' : '更新排课失败')
  } finally {
    submitting.value = false
  }
}

async function removeLesson(lesson: ScheduleLessonRecord) {
  try {
    await ElMessageBox.confirm(
      `确认删除 ${lesson.lessonDate || ''} ${formatClass(lesson.classId)} 的排课吗？`,
      '删除排课',
      {
        type: 'warning',
      },
    )
  } catch {
    return
  }

  deletingLessonId.value = lesson.id
  try {
    await deleteScheduleLesson(lesson.id)
    ElMessage.success('排课已删除')
    await refreshAll()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除排课失败'))
  } finally {
    deletingLessonId.value = null
  }
}

async function initializePage() {
  lookupLoading.value = true
  pageError.value = ''
  try {
    await loadInstitutions()
    if (institutions.value.length > 0) {
      institutionId.value = institutions.value[0].id
      await refreshScopeData()
    }
    await refreshAll()
  } catch (error) {
    pageError.value = toMessage(error, '页面初始化失败')
  } finally {
    lookupLoading.value = false
  }
}

onMounted(() => {
  initializePage()
})
</script>

<style scoped>
.table-actions {
  display: flex;
  gap: 8px;
}

.schedule-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.schedule-conflict-list {
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(250, 173, 20, 0.12);
  color: #8f5f00;
}

.schedule-conflict-item + .schedule-conflict-item {
  margin-top: 6px;
}

.week-board {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

.week-board-day {
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 20px;
  padding: 16px;
  background: rgba(248, 250, 252, 0.92);
}

.week-board-title {
  margin-bottom: 12px;
  font-weight: 700;
  color: #0f172a;
}

.week-board-empty {
  color: #64748b;
  font-size: 13px;
}

.week-board-card {
  padding: 12px;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
  color: #334155;
  font-size: 13px;
}

.week-board-card + .week-board-card {
  margin-top: 10px;
}

.week-board-main {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  align-items: center;
}

@media (max-width: 900px) {
  .schedule-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
