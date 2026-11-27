<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <!-- 主操作行:按钮一律靠左、当前范围回显靠右,筛选行与日期导航在它下面 -->
    <div class="table-toolbar">
      <el-button @click="jumpToToday">回到今天</el-button>
      <el-button @click="statsVisible = !statsVisible">
        统计
        <el-icon class="stats-toggle-icon" :class="{ 'is-open': statsVisible }"
          ><ArrowDown
        /></el-icon>
      </el-button>
      <el-button
        :icon="Refresh"
        circle
        :loading="lookupLoading || dayLoading"
        @click="refreshAll"
      />
      <el-button type="primary" @click="goToScheduling">去排课管理</el-button>
      <div class="education-context-text">
        本页只做点名、结课、停课、补课；改排课计划与新增课次请前往「排课管理」，整周整月的课表看「课表总览」。
      </div>
    </div>

    <!-- 统一筛选栏:校区范围在横幅那颗芯片上选,学期/班级点「查询」才生效;日期前后翻页是导航,保持选中即切换 -->
    <div class="page-toolbar">
      <FilterBar :loading="lookupLoading || dayLoading" @query="applyFilters" @reset="resetFilters">
        <el-select v-model="termId" placeholder="筛选学期" clearable filterable>
          <el-option
            v-for="item in terms"
            :key="item.id"
            :label="item.termName || item.termCode || `学期 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <ClassSelect
          v-model="classId"
          :institution-id="institutionId"
          :campus-id="campusId"
          :term-id="termId"
          placeholder="筛选班级"
        />
      </FilterBar>
      <div class="page-toolbar-datebar">
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
        <div class="daily-center-head-main">
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
              <div class="education-context-text" title="未完成点名的课次会优先保留在这里。">
                未完成点名的课次会优先保留在这里。
              </div>
            </div>
            <el-tag type="warning" effect="plain"
              >待点 {{ pendingAttendanceLessons.length }}</el-tag
            >
          </div>

          <div v-if="pendingAttendanceLessons.length === 0" class="daily-center-empty">
            当前日期下暂无待点名课次。
          </div>

          <div v-else class="daily-lesson-list">
            <button
              v-for="lesson in pendingAttendanceLessons.slice(0, 6)"
              :key="lesson.id"
              type="button"
              class="daily-lesson-item"
              :class="{ 'is-inert': !canMark }"
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

          <div
            v-if="canExecute && readyToCompleteLessons.length > 0"
            class="daily-center-inline-tip"
          >
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
              <div
                class="education-context-text"
                title="已停课且未关联补课的原课次集中处理，直接进入补课动作。"
              >
                已停课且未关联补课的原课次集中处理，直接进入补课动作。
              </div>
            </div>
            <el-tag type="danger" effect="plain">待补 {{ dayPendingMakeupLessons.length }}</el-tag>
          </div>

          <div v-if="dayPendingMakeupLessons.length === 0" class="daily-center-empty">
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
              <div v-if="canExecute" class="daily-mini-item-actions">
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
              <div class="education-context-text" title="回看今日已完成课次，便于复盘和回查。">
                回看今日已完成课次，便于复盘和回查。
              </div>
            </div>
            <el-tag type="success" effect="plain">已结 {{ completedDayLessons.length }}</el-tag>
          </div>

          <div v-if="completedDayLessons.length === 0" class="daily-center-empty">
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
              <div class="education-context-text" title="课次备注、取消和点名提示集中提示。">
                课次备注、取消和点名提示集中提示。
              </div>
            </div>
            <el-tag type="info" effect="plain">提醒 {{ scheduleChangeReminders.length }}</el-tag>
          </div>

          <div v-if="scheduleChangeReminders.length === 0" class="daily-center-empty">
            当前日期暂无变动提醒。
          </div>

          <div v-else class="daily-mini-list">
            <button
              v-for="item in scheduleChangeReminders.slice(0, 5)"
              :key="item.key"
              type="button"
              class="daily-mini-item is-button"
              :class="{ 'is-inert': !canMark && !canExecute }"
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
        :empty-text="dayLoading ? '' : '当前日期与筛选范围下暂无排课。'"
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
              <el-tag v-if="isPendingMakeupLesson(row)" size="small" type="danger" effect="plain"
                >待补课</el-tag
              >
              <!-- 与周课表同一来源：禁排结论由服务端按课次自己的班级与教师算，列表只是转述 -->
              <el-tag
                v-if="(row.ruleConflictMessages || []).length > 0"
                size="small"
                type="danger"
                effect="plain"
                :title="row.ruleConflictMessages.join('；')"
                >禁排</el-tag
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
        <el-table-column v-if="canMark || canExecute" label="执行动作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="canMark"
              link
              type="success"
              :disabled="row.lessonStatus === 'cancelled'"
              @click="openAttendanceDialog(row)"
              >点名</el-button
            >
            <template v-if="canExecute">
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
              <el-button link type="danger" @click="openFormDialog('makeup', row)">补课</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

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
import { ElMessage } from 'element-plus'
import { TABLE_STACK_MAX_HEIGHT } from '@cornerstone/core/utils/table-view'
import { ArrowDown, ArrowLeft, ArrowRight, Refresh } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  fetchDayTimetable,
  fetchLessonAttendanceProgress,
  fetchPendingMakeupLessonPage,
  fetchScheduleLesson,
  type LessonAttendanceProgress,
  type ScheduleLessonRecord,
} from '@cornerstone/module-education/api/education'
import { useEducationScope } from '@cornerstone/module-education/composables/useEducationScope'
import { useLessonFormatters } from '@cornerstone/module-education/composables/useLessonFormatters'
import { useLessonExecution } from '@cornerstone/module-education/composables/useLessonExecution'
import { useLessonRelations } from '@cornerstone/module-education/composables/useLessonRelations'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import FilterBar from '@cornerstone/core/components/FilterBar.vue'
import ClassSelect from '../components/ClassSelect.vue'
import AttendanceDialog from '../components/AttendanceDialog.vue'
import ScheduleLessonFormDialog from './ScheduleLessonFormDialog.vue'
import {
  addDays,
  canCompleteLesson,
  compactTimeRange,
  isAttendanceAllowedStatus,
  lessonStatusLabel,
  lessonStatusTagType,
  sortedLessons,
  todayValue,
} from '@cornerstone/module-education/utils/schedule'
import { toMessage } from '@cornerstone/core/utils/error'

interface ScheduleReminderItem {
  key: string
  title: string
  text: string
  lessonId: string
}

const institutionStore = useInstitutionStore()
const scopeStore = useEducationScopeStore()
const { campusId, termId } = storeToRefs(scopeStore)
/**
 * 校区范围在横幅那颗芯片上选，本页跟着它筛课次。
 * 教师/教室/时段的反查字典在 useLessonFormatters 里按机构全量拉，不跟范围收窄。
 */
const { institutions, institutionId, campuses, terms, loadScope } = useEducationScope()
const route = useRoute()
const router = useRouter()

const formatters = useLessonFormatters()
const { teachers, classrooms, timeSlots, loadSupportingOptions } = formatters
const {
  formatInstitution,
  formatCampus,
  formatClass,
  formatClassroom,
  formatTimeSlot,
  formatTeacherNames,
} = formatters

const classId = ref<string | null>(null)
const lessonDate = ref(todayValue())

const statsVisible = ref(false)

const dayLessons = ref<ScheduleLessonRecord[]>([])
const pendingMakeupLessons = ref<ScheduleLessonRecord[]>([])

// 待补课全局数取服务端总数
const pendingMakeupTotal = ref(0)

const pageError = ref('')
const lookupLoading = ref(false)
const dayLoading = ref(false)

const dayAttendanceProgress = ref<Record<string, LessonAttendanceProgress>>({})
const dayAttendanceMissing = ref(0)

const relations = useLessonRelations({
  // 停补关联解析只看日课表与待补课聚合；周月季课表已拆到「课表总览」页，
  // 本页补课弹窗的停课原课选项因此收窄为当日停课 + 待补队列（不限当日）+ 按需单拉的关联缓存
  lessonSources: () => [dayLessons.value, pendingMakeupLessons.value],
  formatClass,
})
const {
  lessonMap,
  addRelatedLesson,
  relatedCancelledLesson,
  relatedMakeupLessons,
  lessonRelationSummary,
  relationJumpLabel,
} = relations

const execution = useLessonExecution({
  formatClass,
  refreshAfterChange: refreshAll,
})
const {
  canMark,
  canExecute,
  formDialogVisible,
  formDialogMode,
  formDialogLesson,
  attendanceDialogVisible,
  attendanceLessonId,
  openFormDialog,
  openAttendanceDialog,
  completeLesson,
  handleDialogSaved,
} = execution

// 补课弹窗可选择的停课原课：来自待补课聚合 + 日课表中的停课记录（lessonMap 由 useLessonRelations 维护）
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

// 待补课聚合为全局口径，执行分区内再按当天过滤
const dayPendingMakeupLessons = computed(() =>
  sortedLessons(
    pendingMakeupLessons.value.filter(
      (lesson) => !lessonDate.value || lesson.lessonDate === lessonDate.value
    )
  )
)

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

async function refreshScopeData() {
  if (!institutionId.value) {
    terms.value = []
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
    await loadScope()
    await loadSupportingOptions()

    if (termId.value && !terms.value.some((item) => item.id === termId.value)) {
      termId.value = null
      await loadScope()
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

async function refreshDayAndMakeup() {
  await Promise.all([refreshDayTimetable(), refreshPendingMakeupLessons()])
}

async function refreshAll() {
  await Promise.all([refreshDayTimetable(), refreshPendingMakeupLessons()])
}

/** 换校区由横幅芯片把全部页签重挂一遍，已选班级随重挂清空，这里只管本页的草稿 */
async function applyFilters() {
  await refreshScopeData()
  // 课表请求已带 classId（服务端过滤，客户端过滤兜底），筛选变化后需整体重拉
  await refreshAll()
}

/** 重置 = 取消学期/班级筛选并生效（校区是全站范围，不由本页重置） */
async function resetFilters() {
  termId.value = null
  classId.value = null
  await applyFilters()
}

async function shiftDay(delta: number) {
  lessonDate.value = addDays(lessonDate.value || todayValue(), delta)
  await refreshDayAndMakeup()
}

async function goDayToday() {
  lessonDate.value = todayValue()
  await refreshDayAndMakeup()
}

function goToScheduling() {
  router.push('/scheduling')
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

async function jumpToRelatedLesson(lesson?: ScheduleLessonRecord | null) {
  if (!lesson) {
    return
  }
  let target: ScheduleLessonRecord | null = null
  if (lesson.lessonStatus === 'cancelled') {
    target = relatedMakeupLessons(lesson)[0] || null
  } else if (lesson.lessonStatus === 'makeup') {
    target = relatedCancelledLesson(lesson)
    if (!target && lesson.relatedLessonId) {
      // 原停课课次几乎总在别的日期，当前区间没加载到，按 id 单拉一次并缓存
      try {
        target = await fetchScheduleLesson(lesson.relatedLessonId)
        addRelatedLesson(target)
      } catch {
        target = null
      }
    }
  }
  if (!target) {
    ElMessage.warning('未找到关联课次')
    return
  }
  attendanceDialogVisible.value = false
  lessonDate.value = target.lessonDate || lessonDate.value
  // 目标课次可能与当前班级筛选不同，切换或清空班级，保证跳转后列表可见
  if (target.classId && target.classId !== classId.value) {
    classId.value = target.classId
  }
  refreshAll().then(() => {
    ElMessage.success(`已定位到 ${formatClass(target.classId)} 的关联课次`)
  })
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

function applyRoutePreferences() {
  const date = route.query.date
  if (typeof date === 'string' && date) {
    lessonDate.value = date
  }

  const classQuery = route.query.classId
  if (typeof classQuery === 'string' && classQuery) {
    classId.value = classQuery
  }
}

async function jumpToToday() {
  lessonDate.value = todayValue()
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
    // 换机构时 store 的 reset() 已清空校区范围，本页只清自己选的筛选
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

/* 标题与日期/课次回显同一行：这层是分区导航，不是内容，尽量少占高度 */
.daily-center-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.daily-center-head-main {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 12px;
  min-width: 0;
}

.daily-center-head .panel-title {
  margin-bottom: 0;
  font-size: 16px;
}

.daily-center-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.daily-center-block {
  display: grid;
  gap: 8px;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: 10px;
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
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

/* 分区标题 + 一句说明并排成一行；窄列放不下时省略号截断，悬停看全文 */
.daily-center-block-head > div:first-child {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.daily-center-block-head .education-context-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.daily-center-title {
  flex-shrink: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
}

/* 分区里的空态只是一句说明，不做成整块底色的横幅 */
.daily-center-empty {
  padding: 2px 0;
  color: var(--text-secondary);
  font-size: 12.5px;
}

.daily-center-inline-tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 10px;
  border-radius: 8px;
  font-size: 12.5px;
  color: var(--soft-warn-ink);
  background: rgba(249, 199, 79, 0.16);
}

.daily-lesson-list,
.daily-mini-list {
  display: grid;
  gap: 8px;
}

.daily-lesson-item,
.daily-mini-item.is-button {
  width: 100%;
  border: 0;
  cursor: pointer;
  text-align: left;
}

/* 只读（无点名码也无排课维护码）时卡片只是信息，不是执行入口 */
.daily-lesson-item.is-inert,
.daily-mini-item.is-inert {
  cursor: default;
}

.daily-lesson-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--card-bg);
  box-shadow: var(--shadow-card);
}

.daily-lesson-item-main,
.daily-mini-item {
  display: grid;
  gap: 3px;
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
  padding: 8px 12px;
  border-radius: 8px;
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

/* 日期导航区:日课表按天翻页（周月季的周期翻页在「课表总览」页） */
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
