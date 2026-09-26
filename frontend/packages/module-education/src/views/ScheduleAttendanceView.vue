<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <div class="page-toolbar">
      <div class="page-toolbar-filters" v-loading="lookupLoading">
        <el-select
          v-model="campusId"
          placeholder="筛选校区"
          clearable
          filterable
          @change="handleCampusChange"
        >
          <el-option
            v-for="item in campuses"
            :key="item.id"
            :label="item.campusName || item.campusCode || `校区 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="termId"
          placeholder="筛选学期"
          clearable
          filterable
          @change="handleTermChange"
        >
          <el-option
            v-for="item in terms"
            :key="item.id"
            :label="item.termName || item.termCode || `学期 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="classId"
          placeholder="筛选班级"
          clearable
          filterable
          @change="handleClassChange"
        >
          <el-option
            v-for="item in classes"
            :key="item.id"
            :label="item.className || item.classCode || `班级 #${item.id}`"
            :value="item.id"
          />
        </el-select>
      </div>
      <div class="page-toolbar-datebar">
        <template v-if="timetableTab === 'day'">
          <span class="datebar-label">日课表</span>
          <el-button :icon="ArrowLeft" circle @click="shiftDay(-1)" />
          <el-date-picker
            v-model="lessonDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            :clearable="false"
            @change="refreshDayAndMakeup"
          />
          <el-button :icon="ArrowRight" circle @click="shiftDay(1)" />
          <el-button @click="goDayToday">今天</el-button>
        </template>
        <template v-else>
          <span class="datebar-label">课表视图</span>
          <el-radio-group v-model="rangeMode" size="small" @change="refreshRangeTimetable">
            <el-radio-button label="week">周</el-radio-button>
            <el-radio-button label="month">月</el-radio-button>
            <el-radio-button label="quarter">季</el-radio-button>
          </el-radio-group>
          <el-button :icon="ArrowLeft" circle @click="shiftRange(-1)" />
          <el-date-picker
            v-model="anchorDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期定位"
            :clearable="false"
            @change="refreshRangeTimetable"
          />
          <el-button :icon="ArrowRight" circle @click="shiftRange(1)" />
          <el-button @click="goThisPeriod">{{ rangeModeLabel }}</el-button>
          <span class="datebar-hint">{{ timetableRangeLabel }}</span>
        </template>
      </div>
      <div class="page-toolbar-actions">
        <el-button @click="statsVisible = !statsVisible">
          统计
          <el-icon class="stats-toggle-icon" :class="{ 'is-open': statsVisible }"
            ><ArrowDown
          /></el-icon>
        </el-button>
        <el-button
          :icon="Refresh"
          circle
          :loading="lookupLoading || dayLoading || rangeLoading"
          @click="refreshAll"
        />
        <el-button @click="jumpToToday">回到今天</el-button>
        <el-button type="primary" @click="goToScheduling">去排课管理</el-button>
      </div>
    </div>

    <div v-show="statsVisible" class="dashboard-grid education-stat-grid">
      <article class="stat-card">
        <div class="stat-label">今日课次</div>
        <div class="stat-value">{{ filteredDayLessons.length }}</div>
        <div class="stat-hint">{{ lessonDate || '未选择日期' }} 的日课表</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">待点名</div>
        <div class="stat-value">{{ pendingAttendanceLessons.length }}</div>
        <div class="stat-hint">优先处理未完成点名的课次</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">待结课</div>
        <div class="stat-value">{{ readyToCompleteLessons.length }}</div>
        <div class="stat-hint">点名覆盖应到学员后可直接结课</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">待补课</div>
        <div class="stat-value">{{ pendingMakeupTotal }}</div>
        <div class="stat-hint">全部已停课待安排补课的原课次（不限当日）</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">变动提醒</div>
        <div class="stat-value">{{ scheduleChangeReminders.length }}</div>
        <div class="stat-hint">待补课、备注、取消和点名提示都会收敛在这里</div>
      </article>
    </div>

    <article class="panel-card education-panel">
      <div class="daily-center-head">
        <div>
          <div class="panel-title">今日执行分区</div>
          <div class="education-context-text">
            日期：{{ lessonDate || '未选择' }} / 已结课 {{ completedDayLessons.length }} 节 / 已排课
            {{ filteredDayLessons.filter((item) => item.lessonStatus === 'scheduled').length }} 节 /
            待补课 {{ dayPendingMakeupLessons.length }} 节
          </div>
        </div>
      </div>

      <div class="daily-center-grid">
        <section
          class="daily-center-block is-wide"
          :class="{ 'is-highlight': focusedSection === 'pending' }"
        >
          <div class="daily-center-block-head">
            <div>
              <div class="daily-center-title">待点名</div>
              <div class="education-context-text">未完成点名的课次会优先保留在这里。</div>
            </div>
            <el-tag type="warning" effect="plain"
              >待点 {{ pendingAttendanceLessons.length }}</el-tag
            >
          </div>

          <div v-if="pendingAttendanceLessons.length === 0" class="education-empty">
            当前日期下暂无待点名课次。
          </div>

          <div v-else class="daily-lesson-list">
            <button
              v-for="lesson in pendingAttendanceLessons.slice(0, 6)"
              :key="lesson.id"
              type="button"
              class="daily-lesson-item"
              @click="openAttendanceDialog(lesson)"
            >
              <div class="daily-lesson-item-main">
                <div class="daily-lesson-item-title">
                  {{ formatClass(lesson.classId) }}
                  <el-tag
                    v-if="lesson.lessonStatus && lesson.lessonStatus !== 'scheduled'"
                    size="small"
                    :type="lessonStatusTagType(lesson.lessonStatus)"
                  >
                    {{ lessonStatusLabel(lesson.lessonStatus) }}
                  </el-tag>
                  <el-tag size="small" type="warning">待点名</el-tag>
                </div>
                <div class="daily-lesson-item-text">
                  {{ compactTimeRange(lesson.startTime, lesson.endTime) || '待定时段' }} /
                  {{ formatTeacherNames(lesson.teacherIds) }} /
                  {{ formatClassroom(lesson.classroomId) }}
                </div>
              </div>
              <div class="daily-lesson-item-meta">
                {{ attendanceProgressText(lesson) }}
              </div>
            </button>
          </div>

          <div v-if="readyToCompleteLessons.length > 0" class="daily-center-inline-tip">
            <span>已有 {{ readyToCompleteLessons.length }} 节课点名已齐，可直接结课。</span>
            <el-button link type="warning" @click="completeLesson(readyToCompleteLessons[0])"
              >先结最近一节</el-button
            >
          </div>
        </section>

        <section
          class="daily-center-block"
          :class="{ 'is-highlight': focusedSection === 'pending-makeup' }"
        >
          <div class="daily-center-block-head">
            <div>
              <div class="daily-center-title">待补课</div>
              <div class="education-context-text">
                已停课且未关联补课的原课次集中处理，直接进入补课动作。
              </div>
            </div>
            <el-tag type="danger" effect="plain">待补 {{ dayPendingMakeupLessons.length }}</el-tag>
          </div>

          <div v-if="dayPendingMakeupLessons.length === 0" class="education-empty">
            当前日期下暂无待补课课次。
          </div>

          <div v-else class="daily-mini-list">
            <div
              v-for="lesson in dayPendingMakeupLessons.slice(0, 5)"
              :key="lesson.id"
              class="daily-mini-item"
            >
              <strong>{{ formatClass(lesson.classId) }}</strong>
              <span
                >{{ compactTimeRange(lesson.startTime, lesson.endTime) || '待定时段' }} /
                {{ formatTeacherNames(lesson.teacherIds) }}</span
              >
              <span>已停课，待安排补课</span>
              <div class="daily-mini-item-actions">
                <el-button link type="danger" @click="openFormDialog('makeup', lesson)"
                  >安排补课</el-button
                >
              </div>
            </div>
          </div>
        </section>

        <section
          class="daily-center-block"
          :class="{ 'is-highlight': focusedSection === 'completed' }"
        >
          <div class="daily-center-block-head">
            <div>
              <div class="daily-center-title">已结课</div>
              <div class="education-context-text">回看今日已完成课次，便于复盘和回查。</div>
            </div>
            <el-tag type="success" effect="plain">已结 {{ completedDayLessons.length }}</el-tag>
          </div>

          <div v-if="completedDayLessons.length === 0" class="education-empty">
            当前日期下还没有已结课课次。
          </div>

          <div v-else class="daily-mini-list">
            <div
              v-for="lesson in completedDayLessons.slice(0, 5)"
              :key="lesson.id"
              class="daily-mini-item"
            >
              <strong>{{ formatClass(lesson.classId) }}</strong>
              <span>{{ compactTimeRange(lesson.startTime, lesson.endTime) || '待定时段' }}</span>
              <span>{{ formatTeacherNames(lesson.teacherIds) }}</span>
            </div>
          </div>
        </section>

        <section
          class="daily-center-block"
          :class="{ 'is-highlight': focusedSection === 'reminders' }"
        >
          <div class="daily-center-block-head">
            <div>
              <div class="daily-center-title">变动提醒</div>
              <div class="education-context-text">课次备注、取消和点名提示集中提示。</div>
            </div>
            <el-tag type="info" effect="plain">提醒 {{ scheduleChangeReminders.length }}</el-tag>
          </div>

          <div v-if="scheduleChangeReminders.length === 0" class="education-empty">
            当前日期暂无变动提醒。
          </div>

          <div v-else class="daily-mini-list">
            <button
              v-for="item in scheduleChangeReminders.slice(0, 5)"
              :key="item.key"
              type="button"
              class="daily-mini-item is-button"
              @click="handleReminderClick(item.lessonId)"
            >
              <strong>{{ item.title }}</strong>
              <span>{{ item.text }}</span>
            </button>
          </div>
        </section>
      </div>
    </article>

    <article class="panel-card education-panel">
      <el-tabs v-model="timetableTab">
        <el-tab-pane label="日课表" name="day">
          <div class="education-table-head">
            <div class="panel-title">日课表</div>
            <div class="education-context-text">
              课表日期：{{ lessonDate || '未选择' }}；班级：{{ formatClass(classId) }}；范围：{{
                formatInstitution(institutionId)
              }}
              / {{ formatCampus(campusId) }}
              <el-button
                v-if="dayAttendanceMissing > 0"
                link
                type="warning"
                @click="retryDayAttendanceProgress"
              >
                {{ dayAttendanceMissing }} 节课点名进度加载失败，点击重试
              </el-button>
            </div>
          </div>

          <el-table
            :data="filteredDayLessons"
            border
            v-loading="dayLoading"
            :max-height="TABLE_STACK_MAX_HEIGHT"
          >
            <!-- 日课表是一次性全量加载的客户端表,排序走 Element Plus 本地比较,不发请求 -->
            <el-table-column label="时段" min-width="170" prop="startTime" sortable>
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
            <el-table-column
              prop="lessonTitle"
              label="课程主题"
              min-width="160"
              show-overflow-tooltip
              sortable
            />
            <el-table-column label="状态" width="120" prop="lessonStatus" sortable>
              <template #default="{ row }">
                <div class="lesson-status-cell">
                  <el-tag :type="lessonStatusTagType(row.lessonStatus)">
                    {{ lessonStatusLabel(row.lessonStatus) }}
                  </el-tag>
                  <el-tag
                    v-if="isPendingMakeupLesson(row)"
                    size="small"
                    type="danger"
                    effect="plain"
                    >待补课</el-tag
                  >
                </div>
              </template>
            </el-table-column>
            <el-table-column label="停补关联" min-width="220">
              <template #default="{ row }">
                <div class="relation-cell">
                  <span>{{ lessonRelationSummary(row) }}</span>
                  <el-button
                    v-if="relationJumpLabel(row)"
                    link
                    type="primary"
                    @click="jumpToRelatedLesson(row)"
                  >
                    {{ relationJumpLabel(row) }}
                  </el-button>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="执行动作" width="280" fixed="right">
              <template #default="{ row }">
                <el-button
                  link
                  type="success"
                  :disabled="row.lessonStatus === 'cancelled'"
                  @click="openAttendanceDialog(row)"
                  >点名</el-button
                >
                <el-button
                  link
                  type="warning"
                  :disabled="!canCompleteLesson(row)"
                  @click="completeLesson(row)"
                >
                  结课
                </el-button>
                <el-button link type="primary" @click="openFormDialog('reschedule', row)"
                  >调课</el-button
                >
                <el-button link type="info" @click="openFormDialog('cancel', row)">停课</el-button>
                <el-button link type="danger" @click="openFormDialog('makeup', row)"
                  >补课</el-button
                >
              </template>
            </el-table-column>
          </el-table>

          <div v-if="!dayLoading && filteredDayLessons.length === 0" class="education-empty">
            当前日期与筛选范围下暂无排课。
          </div>
        </el-tab-pane>

        <el-tab-pane label="周/月/季课表" name="week">
          <div class="education-table-head">
            <div class="panel-title">周/月/季课表</div>
            <div class="education-context-text">
              日期：{{ timetableRange.start }} 至 {{ timetableRange.end }}（{{
                rangeModeText
              }}）；班级：{{ formatClass(classId) }}；范围：{{ formatInstitution(institutionId) }} /
              {{ formatCampus(campusId) }}。点击空格可跳转排课管理新增。
            </div>
          </div>

          <WeekTimetableGrid
            :mode="rangeMode"
            :days="timetableDays"
            :lessons="filteredWeekLessons"
            :time-slots="timeSlots"
            :classrooms="classrooms"
            :teachers="teachers"
            :loading="rangeLoading"
            :format-class="formatClass"
            :format-classroom="formatClassroom"
            :format-teachers="formatTeacherNames"
            :status-label="lessonStatusLabel"
            :status-tag-type="lessonStatusTagType"
            :relation-summary="lessonRelationSummary"
            @lesson-action="handleWeekLessonAction"
            @cell-click="handleWeekCellClick"
          />
        </el-tab-pane>
      </el-tabs>
    </article>

    <ScheduleLessonFormDialog
      v-model="formDialogVisible"
      :mode="formDialogMode"
      :lesson="formDialogLesson"
      :institution-id="String(institutionId || '')"
      :scope-campus-id="campusId"
      :scope-term-id="termId"
      :scope-class-id="classId"
      :institutions="institutions"
      :campuses="campuses"
      :terms="terms"
      :classes="classes"
      :time-slots="timeSlots"
      :cancelled-options="cancelledLessonOptions"
      :format-class="formatClass"
      :default-date="lessonDate"
      @saved="handleDialogSaved"
    />

    <AttendanceDialog
      v-model="attendanceDialogVisible"
      :lesson-id="attendanceLessonId"
      :format-class="formatClass"
      :relation-summary="lessonRelationSummary(attendanceLessonRecord)"
      :relation-jump-label="relationJumpLabel(attendanceLessonRecord)"
      @saved="refreshDayAndMakeup"
      @completed="refreshAll"
      @relation-jump="jumpToRelatedLesson(attendanceLessonRecord)"
    />
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { TABLE_STACK_MAX_HEIGHT } from '@cornerstone/core/utils/table-view'
import { ArrowDown, ArrowLeft, ArrowRight, Refresh } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  fetchDayTimetable,
  fetchLessonAttendanceProgress,
  fetchPendingMakeupLessonPage,
  fetchTeachers,
  fetchWeekTimetable,
  type LessonAttendanceProgress,
  type ScheduleLessonRecord,
  type TeacherSummary,
} from '@cornerstone/module-education/api/education'
import {
  fetchClassrooms,
  fetchTimeSlots,
  type ClassroomSummary,
  type TimeSlotSummary,
} from '@cornerstone/module-organization/api/organization'
import { useEducationScope } from '@cornerstone/module-education/composables/useEducationScope'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import WeekTimetableGrid, { type WeekLessonAction } from './components/WeekTimetableGrid.vue'
import AttendanceDialog from './components/AttendanceDialog.vue'
import ScheduleLessonFormDialog from './components/ScheduleLessonFormDialog.vue'
import {
  addDays,
  addMonths,
  canCompleteLesson,
  compactDateLabel,
  compactTimeRange,
  enumerateDates,
  formatTeacherIdsAs,
  isAttendanceAllowedStatus,
  lessonStatusLabel,
  lessonStatusTagType,
  rangeFor,
  sortedLessons,
  timeSlotOptionLabel,
  toMessage,
  todayValue,
  updateLessonCompletion,
  weekdayFromDate,
  weekdayLabel,
} from './components/schedule-utils'

interface ScheduleReminderItem {
  key: string
  title: string
  text: string
  lessonId: string
}

const institutionStore = useInstitutionStore()
const scopeStore = useEducationScopeStore()
const { campusId, termId } = storeToRefs(scopeStore)
const { institutions, institutionId, campuses, terms, classes, loadScope } = useEducationScope()
const route = useRoute()
const router = useRouter()

/** 执行变更弹窗模式：调课 / 停课 / 补课，与共享弹窗组件的契约一致 */
type ExecutionMode = 'reschedule' | 'cancel' | 'makeup'

const teachers = ref<TeacherSummary[]>([])
const classrooms = ref<ClassroomSummary[]>([])
const timeSlots = ref<TimeSlotSummary[]>([])

const classId = ref<string | null>(null)
const lessonDate = ref(todayValue())
// 周/月/季课表共用一个锚点日期，区间由 rangeFor 派生
const anchorDate = ref(todayValue())
const rangeMode = ref<'week' | 'month' | 'quarter'>('week')
const timetableTab = ref('day')

const statsVisible = ref(false)

const dayLessons = ref<ScheduleLessonRecord[]>([])
const weekLessons = ref<ScheduleLessonRecord[]>([])
const pendingMakeupLessons = ref<ScheduleLessonRecord[]>([])

// 待补课全局数取服务端总数
const pendingMakeupTotal = ref(0)

const pageError = ref('')
const lookupLoading = ref(false)
const dayLoading = ref(false)
const rangeLoading = ref(false)
const completingLesson = ref(false)

const dayAttendanceProgress = ref<Record<string, LessonAttendanceProgress>>({})
const dayAttendanceMissing = ref(0)

const formDialogVisible = ref(false)
const formDialogMode = ref<ExecutionMode>('reschedule')
const formDialogLesson = ref<ScheduleLessonRecord | null>(null)

const attendanceDialogVisible = ref(false)
const attendanceLessonId = ref<string | null>(null)

const institutionMap = computed(() => {
  return new Map(
    institutions.value.map((item) => [
      item.id,
      item.orgName || item.shortName || item.orgCode || `机构 #${item.id}`,
    ])
  )
})

const campusMap = computed(() => {
  return new Map(
    campuses.value.map((item) => [
      item.id,
      item.campusName || item.campusCode || `校区 #${item.id}`,
    ])
  )
})

const classMap = computed(() => {
  return new Map(
    classes.value.map((item) => [item.id, item.className || item.classCode || `班级 #${item.id}`])
  )
})

const classroomMap = computed(() => {
  return new Map(
    classrooms.value.map((item) => [
      item.id,
      item.classroomName || item.classroomCode || `教室 #${item.id}`,
    ])
  )
})

const teacherMap = computed(() => {
  return new Map(
    teachers.value.map((item) => [
      item.id,
      item.teacherName || item.teacherCode || `教师 #${item.id}`,
    ])
  )
})

const timeSlotMap = computed(() => {
  return new Map(timeSlots.value.map((item) => [item.id, item]))
})

// 关联关系（补课↔停课原课）只在已加载的日/周/待补课课次内推导
const lessonMap = computed(() => {
  return new Map(
    sortedLessons([...dayLessons.value, ...weekLessons.value, ...pendingMakeupLessons.value]).map(
      (item) => [item.id, item]
    )
  )
})

const makeupLessonsByRelatedId = computed(() => {
  const map = new Map<string, ScheduleLessonRecord[]>()
  for (const lesson of lessonMap.value.values()) {
    if (
      lesson.lessonStatus !== 'makeup' ||
      lesson.changeType !== 'cancelled_makeup' ||
      !lesson.relatedLessonId
    ) {
      continue
    }
    const key = lesson.relatedLessonId
    const list = map.get(key) || []
    list.push(lesson)
    map.set(key, list)
  }
  return map
})

// 补课弹窗可选择的停课原课：来自待补课聚合 + 已加载课次中的停课记录
const cancelledLessonOptions = computed(() => {
  return sortedLessons(
    Array.from(lessonMap.value.values()).filter((lesson) => lesson.lessonStatus === 'cancelled')
  ).map((lesson) => ({
    id: lesson.id,
    label: `${lesson.lessonDate || '未设置日期'} / ${formatClass(lesson.classId)} / ${
      compactTimeRange(lesson.startTime, lesson.endTime) || '待定时段'
    }`,
  }))
})

// 班级筛选对课表/执行分区/统计卡统一生效（课表接口暂不支持 classId，先客户端过滤）
function filterLessonsByClass(values: ScheduleLessonRecord[]) {
  if (!classId.value) {
    return values
  }
  return values.filter((lesson) => lesson.classId === classId.value)
}

const filteredDayLessons = computed(() => sortedLessons(filterLessonsByClass(dayLessons.value)))
const filteredWeekLessons = computed(() => sortedLessons(filterLessonsByClass(weekLessons.value)))

// 待补课聚合为全局口径，执行分区内再按当天过滤
const dayPendingMakeupLessons = computed(() =>
  sortedLessons(
    pendingMakeupLessons.value.filter(
      (lesson) => !lessonDate.value || lesson.lessonDate === lessonDate.value
    )
  )
)

// 周=所在日历周；月/季=首尾对齐到周一~周日的日历范围（后端 between 支持任意区间）
const timetableRange = computed(() => rangeFor(rangeMode.value, anchorDate.value))

const timetableDays = computed(() =>
  enumerateDates(timetableRange.value.start, timetableRange.value.end).map((date) => ({
    date,
    title: `${compactDateLabel(date)} ${weekdayLabel(weekdayFromDate(date))}`,
    isToday: date === todayValue(),
    dimmed: isDimmedDate(date),
  }))
)

const timetableRangeLabel = computed(() => {
  const { start, end } = timetableRange.value
  if (!start || !end) {
    return ''
  }
  return `${compactDateLabel(start)} ~ ${compactDateLabel(end)}`
})

const rangeModeLabel = computed(() => {
  switch (rangeMode.value) {
    case 'month':
      return '本月'
    case 'quarter':
      return '本季'
    default:
      return '本周'
  }
})

const rangeModeText = computed(() => rangeModeLabel.value.slice(1))

const focusedSection = computed(() => {
  const value = route.query.focus
  return typeof value === 'string' ? value : ''
})

const pendingAttendanceLessons = computed(() => {
  return sortedLessons(
    filteredDayLessons.value.filter((lesson) => {
      if (!isAttendanceAllowedStatus(lesson.lessonStatus)) {
        return false
      }
      return attendanceMarkedCount(lesson) < attendanceExpectedCount(lesson)
    })
  )
})

const readyToCompleteLessons = computed(() => {
  return sortedLessons(
    filteredDayLessons.value.filter((lesson) => {
      if (!canCompleteLesson(lesson)) {
        return false
      }
      const expectedCount = attendanceExpectedCount(lesson)
      return expectedCount > 0 && attendanceMarkedCount(lesson) >= expectedCount
    })
  )
})

const completedDayLessons = computed(() => {
  return sortedLessons(
    filteredDayLessons.value.filter((lesson) => lesson.lessonStatus === 'completed')
  )
})

const scheduleChangeReminders = computed<ScheduleReminderItem[]>(() => {
  const items: ScheduleReminderItem[] = []

  for (const lesson of filteredDayLessons.value) {
    if (isPendingMakeupLesson(lesson)) {
      items.push({
        key: `${lesson.id}-pending-makeup`,
        title: `${formatClass(lesson.classId)} 待补课`,
        text: '已停课，待安排补课，可直接进入补课动作。',
        lessonId: lesson.id,
      })
    }
    // 批量进度接口不再返回点名页 message,应到 0 等价于原"该班级没有在班学员"提示
    const progress = dayAttendanceProgress.value[lesson.id]
    if (progress && progress.expectedCount === 0) {
      items.push({
        key: `${lesson.id}-empty-roster`,
        title:
          `${formatClass(lesson.classId)} ${compactTimeRange(lesson.startTime, lesson.endTime) || ''}`.trim(),
        text: '该班级没有在班学员',
        lessonId: lesson.id,
      })
    }
    if (lesson.remark) {
      items.push({
        key: `${lesson.id}-remark`,
        title: `${formatClass(lesson.classId)} 备注`,
        text: lesson.remark,
        lessonId: lesson.id,
      })
    }
    if (lesson.lessonStatus === 'rescheduled') {
      items.push({
        key: `${lesson.id}-rescheduled`,
        title: `${formatClass(lesson.classId)} 已调课`,
        text: '该课次已调课，请确认新时段、新教室和授课教师安排。',
        lessonId: lesson.id,
      })
    }
    if (lesson.lessonStatus === 'makeup') {
      items.push({
        key: `${lesson.id}-makeup`,
        title: `${formatClass(lesson.classId)} 已补课`,
        text: '该课次已作为补课执行，请关注点名和后续完成情况。',
        lessonId: lesson.id,
      })
    }
    if (lesson.lessonStatus === 'cancelled') {
      items.push({
        key: `${lesson.id}-cancelled`,
        title: `${formatClass(lesson.classId)} 已停课`,
        text: isPendingMakeupLesson(lesson)
          ? '该课次已停课，待安排补课，请尽快处理。'
          : '该课次已停课，请同步检查补课与通知安排。',
        lessonId: lesson.id,
      })
    }
  }

  return items
})

const attendanceLessonRecord = computed(() => {
  const id = attendanceLessonId.value
  return id ? lessonMap.value.get(id) || null : null
})

// 月/季视图里落在前后月（季）的补位日期置灰展示
function isDimmedDate(date: string) {
  if (rangeMode.value === 'month') {
    return date.slice(0, 7) !== anchorDate.value.slice(0, 7)
  }
  if (rangeMode.value === 'quarter') {
    const month = Number(date.slice(5, 7))
    const anchorMonth = Number(anchorDate.value.slice(5, 7))
    const first = Math.floor((anchorMonth - 1) / 3) * 3 + 1
    return month < first || month > first + 2
  }
  return false
}

function attendanceProgressFor(lessonId: string) {
  return dayAttendanceProgress.value[lessonId]
}

function attendanceExpectedCount(lesson: ScheduleLessonRecord) {
  return attendanceProgressFor(lesson.id)?.expectedCount || 0
}

function attendanceMarkedCount(lesson: ScheduleLessonRecord) {
  return attendanceProgressFor(lesson.id)?.markedCount || 0
}

function attendanceProgressText(lesson: ScheduleLessonRecord) {
  const progress = attendanceProgressFor(lesson.id)
  if (!progress) {
    return lesson.lessonStatus === 'completed' ? '已结课，待回查点名' : '待加载点名进度'
  }
  return `已点 ${progress.markedCount} / 应到 ${progress.expectedCount}`
}

// 待补课统一以服务端派生字段为准，避免多处口径打架
function isPendingMakeupLesson(lesson?: ScheduleLessonRecord | null) {
  return lesson?.pendingMakeup === true
}

function relatedCancelledLesson(lesson?: ScheduleLessonRecord | null) {
  if (!lesson?.relatedLessonId) {
    return null
  }
  return lessonMap.value.get(lesson.relatedLessonId) || null
}

function relatedMakeupLessons(lesson?: ScheduleLessonRecord | null) {
  if (!lesson) {
    return []
  }
  return sortedLessons(makeupLessonsByRelatedId.value.get(lesson.id) || [])
}

function lessonRelationSummary(lesson?: ScheduleLessonRecord | null) {
  if (!lesson) {
    return '无关联'
  }
  if (lesson.lessonStatus === 'cancelled') {
    const related = relatedMakeupLessons(lesson)
    return related.length === 0 ? '未关联补课' : `已关联 ${related.length} 节补课`
  }
  if (lesson.lessonStatus === 'makeup' && lesson.changeType === 'cancelled_makeup') {
    const related = relatedCancelledLesson(lesson)
    if (!related) {
      return '补课原课未加载'
    }
    return `补 ${related.lessonDate || '未设置日期'} ${formatClass(related.classId)}`
  }
  return '无关联'
}

function relationJumpLabel(lesson?: ScheduleLessonRecord | null) {
  if (!lesson) {
    return ''
  }
  if (lesson.lessonStatus === 'cancelled' && relatedMakeupLessons(lesson).length > 0) {
    return '查看补课'
  }
  if (
    lesson.lessonStatus === 'makeup' &&
    lesson.changeType === 'cancelled_makeup' &&
    lesson.relatedLessonId
  ) {
    return '查看原停课'
  }
  return ''
}

function jumpToRelatedLesson(lesson?: ScheduleLessonRecord | null) {
  if (!lesson) {
    return
  }
  let target: ScheduleLessonRecord | null = null
  if (lesson.lessonStatus === 'cancelled') {
    target = relatedMakeupLessons(lesson)[0] || null
  } else if (lesson.lessonStatus === 'makeup') {
    target = relatedCancelledLesson(lesson)
  }
  if (!target) {
    ElMessage.warning('未找到关联课次')
    return
  }
  attendanceDialogVisible.value = false
  timetableTab.value = 'day'
  lessonDate.value = target.lessonDate || lessonDate.value
  // 目标课次可能与当前班级筛选不同，切换或清空班级，保证跳转后列表可见
  if (target.classId && target.classId !== classId.value) {
    classId.value = classes.value.some((item) => item.id === target.classId) ? target.classId : null
  }
  refreshAll().then(() => {
    ElMessage.success(`已定位到 ${formatClass(target.classId)} 的关联课次`)
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
  return formatTeacherIdsAs(values, teacherMap.value)
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
    // 不传校区时后端返回全部教室，保证未选校区时课表仍能显示教室名称
    fetchClassrooms(campusId.value),
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
    await loadScope(campusId.value, termId.value)
    await loadSupportingOptions()

    if (campusId.value && !campuses.value.some((item) => item.id === campusId.value)) {
      campusId.value = null
      await loadSupportingOptions()
    }
    if (termId.value && !terms.value.some((item) => item.id === termId.value)) {
      termId.value = null
      await loadScope(campusId.value, termId.value)
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

// 待补课按全局口径拉取（不限当天），统计卡用服务端总数，执行分区内再按日过滤
async function refreshPendingMakeupLessons() {
  if (!institutionId.value) {
    pendingMakeupLessons.value = []
    pendingMakeupTotal.value = 0
    return
  }

  try {
    const result = await fetchPendingMakeupLessonPage({
      institutionId: institutionId.value,
      campusId: campusId.value,
      classId: classId.value,
      page: 1,
      pageSize: 100,
    })
    pendingMakeupLessons.value = sortedLessons(result.records)
    pendingMakeupTotal.value = result.total
  } catch (error) {
    pendingMakeupLessons.value = []
    pendingMakeupTotal.value = 0
    pageError.value = toMessage(error, '待补课聚合加载失败')
  }
}

async function refreshDayTimetable() {
  if (!lessonDate.value) {
    dayLessons.value = []
    dayAttendanceProgress.value = {}
    return
  }

  dayLoading.value = true
  pageError.value = ''
  try {
    dayLessons.value = sortedLessons(
      await fetchDayTimetable({
        institutionId: institutionId.value,
        campusId: campusId.value,
        classId: classId.value,
        lessonDate: lessonDate.value,
      })
    )
    await loadDayAttendanceProgress(dayLessons.value)
  } catch (error) {
    dayLessons.value = []
    dayAttendanceProgress.value = {}
    pageError.value = toMessage(error, '日课表加载失败')
  } finally {
    dayLoading.value = false
  }
}

async function refreshRangeTimetable() {
  if (!institutionId.value) {
    weekLessons.value = []
    return
  }

  rangeLoading.value = true
  pageError.value = ''
  try {
    weekLessons.value = sortedLessons(
      await fetchWeekTimetable({
        institutionId: institutionId.value,
        campusId: campusId.value,
        classId: classId.value,
        weekStart: timetableRange.value.start,
        weekEnd: timetableRange.value.end,
      })
    )
  } catch (error) {
    weekLessons.value = []
    pageError.value = toMessage(error, '课表加载失败')
  } finally {
    rangeLoading.value = false
  }
}

async function refreshDayAndMakeup() {
  await Promise.all([refreshDayTimetable(), refreshPendingMakeupLessons()])
}

async function refreshAll() {
  await Promise.all([refreshDayTimetable(), refreshRangeTimetable(), refreshPendingMakeupLessons()])
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

async function handleClassChange() {
  // 课表请求已带 classId（服务端过滤，客户端过滤兜底），班级变化后需整体重拉
  await refreshAll()
}

async function shiftDay(delta: number) {
  lessonDate.value = addDays(lessonDate.value || todayValue(), delta)
  await refreshDayAndMakeup()
}

async function goDayToday() {
  lessonDate.value = todayValue()
  await refreshDayAndMakeup()
}

async function shiftRange(delta: number) {
  if (rangeMode.value === 'week') {
    anchorDate.value = addDays(anchorDate.value, delta * 7)
  } else if (rangeMode.value === 'month') {
    anchorDate.value = addMonths(anchorDate.value, delta)
  } else {
    anchorDate.value = addMonths(anchorDate.value, delta * 3)
  }
  await refreshRangeTimetable()
}

async function goThisPeriod() {
  anchorDate.value = todayValue()
  await refreshRangeTimetable()
}

function goToScheduling() {
  router.push('/scheduling')
}

function handleWeekLessonAction(payload: WeekLessonAction) {
  switch (payload.type) {
    case 'attendance':
      openAttendanceDialog(payload.lesson)
      break
    case 'complete':
      completeLesson(payload.lesson)
      break
    case 'reschedule':
      openFormDialog('reschedule', payload.lesson)
      break
    case 'cancel':
      openFormDialog('cancel', payload.lesson)
      break
    case 'makeup':
      openFormDialog('makeup', payload.lesson)
      break
    case 'edit':
      // 基本信息编辑归排课管理页，带上下文跳转
      router.push({
        path: '/scheduling',
        query: { editId: payload.lesson.id, date: payload.lesson.lessonDate || undefined },
      })
      break
  }
}

function handleWeekCellClick(payload: {
  date: string
  timeSlotId?: string | null
  classroomId?: string | null
}) {
  // 排课是计划动作，跳转排课管理页并预填空格上下文
  router.push({
    path: '/scheduling',
    query: {
      create: '1',
      date: payload.date,
      timeSlotId: payload.timeSlotId || undefined,
      classroomId: payload.classroomId || undefined,
      classId: classId.value || undefined,
    },
  })
}

function openFormDialog(mode: ExecutionMode, lesson: ScheduleLessonRecord) {
  formDialogMode.value = mode
  formDialogLesson.value = lesson
  formDialogVisible.value = true
}

function handleDialogSaved(message: string) {
  ElMessage.success(message)
  refreshAll()
}

function openAttendanceDialog(lesson: ScheduleLessonRecord) {
  attendanceLessonId.value = lesson.id
  attendanceDialogVisible.value = true
}

function handleReminderClick(lessonId: string) {
  const lesson = filteredDayLessons.value.find((item) => item.id === lessonId)
  if (!lesson) {
    return
  }
  if (isPendingMakeupLesson(lesson) || lesson.lessonStatus === 'cancelled') {
    openFormDialog('makeup', lesson)
    return
  }
  openAttendanceDialog(lesson)
}

// 点名进度改走批量接口,替代原先逐课次拉点名页的 N+1;停课课次不产生考勤,不计入缺失
async function loadDayAttendanceProgress(values: ScheduleLessonRecord[], merge = false) {
  const allowed = values.filter((lesson) => isAttendanceAllowedStatus(lesson.lessonStatus))
  if (allowed.length === 0) {
    if (!merge) {
      dayAttendanceProgress.value = {}
      dayAttendanceMissing.value = 0
    }
    return
  }

  let progressMap: Record<string, LessonAttendanceProgress> = {}
  try {
    const list = await fetchLessonAttendanceProgress(allowed.map((lesson) => String(lesson.id)))
    progressMap = Object.fromEntries(list.map((item) => [String(item.lessonId), item]))
  } catch {
    progressMap = {}
  }
  dayAttendanceProgress.value = merge
    ? { ...dayAttendanceProgress.value, ...progressMap }
    : progressMap
  dayAttendanceMissing.value = allowed.filter((lesson) => !progressMap[String(lesson.id)]).length
}

async function retryDayAttendanceProgress() {
  const missing = dayLessons.value.filter(
    (lesson) =>
      isAttendanceAllowedStatus(lesson.lessonStatus) && !dayAttendanceProgress.value[lesson.id]
  )
  if (missing.length === 0) {
    dayAttendanceMissing.value = 0
    return
  }
  dayLoading.value = true
  try {
    await loadDayAttendanceProgress(missing, true)
  } finally {
    dayLoading.value = false
  }
}

async function completeLesson(lesson: ScheduleLessonRecord) {
  if (!canCompleteLesson(lesson)) {
    ElMessage.warning('已停课或已结课的课次不支持结课')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认将 ${lesson.lessonDate || ''} ${formatClass(lesson.classId)} 标记为已结课吗？`,
      '课次结课',
      { type: 'warning' }
    )
  } catch {
    return
  }

  completingLesson.value = true
  try {
    await updateLessonCompletion(lesson.id, 'completed')
    ElMessage.success('课次已结课')
    await refreshAll()
  } catch (error) {
    ElMessage.error(toMessage(error, '课次结课失败'))
  } finally {
    completingLesson.value = false
  }
}

function applyRoutePreferences() {
  const date = route.query.date
  if (typeof date === 'string' && date) {
    lessonDate.value = date
    anchorDate.value = date
  }

  const classQuery = route.query.classId
  if (typeof classQuery === 'string' && classQuery) {
    classId.value = classQuery
  }

  if (route.query.focus === 'day') {
    timetableTab.value = 'day'
  }
}

async function jumpToToday() {
  lessonDate.value = todayValue()
  anchorDate.value = todayValue()
  await refreshAll()
}

async function initializePage() {
  lookupLoading.value = true
  pageError.value = ''
  try {
    applyRoutePreferences()
    await institutionStore.loadInstitutions()
    if (institutionId.value) {
      await refreshScopeData()
    }
    await refreshAll()
  } catch (error) {
    pageError.value = toMessage(error, '页面初始化失败')
  } finally {
    lookupLoading.value = false
  }
}

watch(
  () => institutionStore.currentInstitutionId,
  async () => {
    campusId.value = null
    termId.value = null
    classId.value = null
    await refreshScopeData()
    await refreshAll()
  }
)

// 页面停留时从工作台等入口带参跳转也要生效（初始化时由 initializePage 处理）
watch(
  () => route.query,
  async () => {
    if (route.name !== 'schedule') {
      return
    }
    applyRoutePreferences()
    await refreshAll()
  }
)

onMounted(() => {
  initializePage()
})
</script>

<style scoped>
.relation-cell {
  display: grid;
  gap: 4px;
}

.daily-center-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.daily-center-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.daily-center-block {
  display: grid;
  gap: 14px;
  padding: 18px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--subtle-bg);
}

.daily-center-block.is-wide {
  grid-column: span 2;
}

.daily-center-block.is-highlight {
  border-color: rgba(249, 199, 79, 0.9);
  box-shadow: 0 0 0 3px rgba(249, 199, 79, 0.18);
}

.daily-center-block-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.daily-center-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.daily-center-inline-tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 10px;
  color: var(--soft-warn-ink);
  background: rgba(249, 199, 79, 0.16);
}

.daily-lesson-list,
.daily-mini-list {
  display: grid;
  gap: 12px;
}

.daily-lesson-item,
.daily-mini-item.is-button {
  width: 100%;
  border: 0;
  cursor: pointer;
  text-align: left;
}

.daily-lesson-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 10px;
  background: var(--card-bg);
  box-shadow: var(--shadow-card);
}

.daily-lesson-item-main,
.daily-mini-item {
  display: grid;
  gap: 6px;
}

.daily-lesson-item-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-weight: 700;
  color: var(--text-primary);
}

.daily-lesson-item-text,
.daily-mini-item span {
  color: var(--text-secondary);
  font-size: 13px;
}

.daily-lesson-item-meta {
  align-self: center;
  white-space: nowrap;
  font-weight: 700;
  color: var(--tint-ink);
}

.daily-mini-item {
  padding: 12px 14px;
  border-radius: 10px;
  color: var(--tint-ink);
  background: var(--card-bg);
}

.daily-mini-item strong {
  color: var(--text-primary);
}

.daily-mini-item-actions {
  display: flex;
  justify-content: flex-end;
}

.lesson-status-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

/* 日期导航区:控件随当前视图切换(日课表翻日 / 周月季课表翻周期) */
.page-toolbar-datebar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.page-toolbar-datebar .el-date-editor {
  width: 150px;
}

.datebar-label {
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
}

.datebar-hint {
  color: var(--text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .daily-center-head,
  .daily-center-block-head,
  .daily-center-inline-tip,
  .daily-lesson-item {
    flex-direction: column;
    align-items: flex-start;
  }

  .daily-center-grid {
    grid-template-columns: 1fr;
  }

  .daily-center-block.is-wide {
    grid-column: span 1;
  }
}
</style>
