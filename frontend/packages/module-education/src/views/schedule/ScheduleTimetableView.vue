<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <!-- 主操作行:课表总览负责"看",点空格去排课管理新增,点课程卡可就地执行点名与调停补 -->
    <div class="table-toolbar">
      <el-button
        :icon="Refresh"
        circle
        :loading="lookupLoading || rangeLoading"
        @click="refreshAll"
      />
      <div class="education-context-text">
        整周、整月、整季的课怎么排都在这一页看；改排课计划与新增课次请前往「排课管理」。
      </div>
    </div>

    <!-- 统一筛选栏:校区范围在横幅那颗芯片上选,学期/班级点「查询」才生效;周期翻页是导航,保持选中即切换 -->
    <div class="page-toolbar">
      <FilterBar
        :loading="lookupLoading || rangeLoading"
        @query="applyFilters"
        @reset="resetFilters"
      >
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
      </div>
    </div>

    <article class="panel-card education-panel">
      <div class="education-table-head">
        <div class="panel-title">周/月/季课表</div>
        <div class="education-context-text">
          日期：{{ timetableRange.start }} 至 {{ timetableRange.end }}（{{
            rangeModeText
          }}）；班级：{{ formatClass(classId) }}；范围：{{ formatInstitution(institutionId) }} /
          {{ formatCampus(campusId) }}。
          <span v-if="canExecute">点击空格可跳转排课管理新增。</span>
        </div>
      </div>

      <WeekTimetableGrid
        :mode="rangeMode"
        :days="timetableDays"
        :lessons="filteredRangeLessons"
        :time-slots="timeSlots"
        :classrooms="classrooms"
        :teachers="teachers"
        :loading="rangeLoading"
        :can-mark="canMark"
        :can-execute="canExecute"
        :format-class="formatClass"
        :format-classroom="formatClassroom"
        :format-teachers="formatTeacherNames"
        :status-label="lessonStatusLabel"
        :status-tag-type="lessonStatusTagType"
        :relation-summary="lessonRelationSummary"
        @lesson-action="handleLessonAction"
        @cell-click="handleWeekCellClick"
      />
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
      :default-date="anchorDate"
      @saved="handleDialogSaved"
    />

    <AttendanceDialog
      v-model="attendanceDialogVisible"
      :lesson-id="attendanceLessonId"
      :format-class="formatClass"
      :relation-summary="lessonRelationSummary(attendanceLessonRecord)"
      :relation-jump-label="relationJumpLabel(attendanceLessonRecord)"
      @saved="refreshRangeTimetable"
      @completed="refreshRangeTimetable"
      @relation-jump="jumpToRelatedLesson(attendanceLessonRecord)"
    />
  </section>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, Refresh } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  fetchScheduleLesson,
  fetchWeekTimetable,
  type ScheduleLessonRecord,
} from '@cornerstone/module-education/api/education'
import { useEducationScope } from '@cornerstone/module-education/composables/useEducationScope'
import { useLessonFormatters } from '@cornerstone/module-education/composables/useLessonFormatters'
import { useLessonExecution } from '@cornerstone/module-education/composables/useLessonExecution'
import { useLessonRelations } from '@cornerstone/module-education/composables/useLessonRelations'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import WeekTimetableGrid from './WeekTimetableGrid.vue'
import FilterBar from '@cornerstone/core/components/FilterBar.vue'
import ClassSelect from '../components/ClassSelect.vue'
import AttendanceDialog from '../components/AttendanceDialog.vue'
import ScheduleLessonFormDialog from './ScheduleLessonFormDialog.vue'
import {
  addDays,
  addMonths,
  compactDateLabel,
  compactTimeRange,
  enumerateDates,
  lessonStatusLabel,
  lessonStatusTagType,
  rangeFor,
  sortedLessons,
  todayValue,
  weekdayFromDate,
  weekdayLabel,
} from '@cornerstone/module-education/utils/schedule'
import { toMessage } from '@cornerstone/core/utils/error'

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
const { formatInstitution, formatCampus, formatClass, formatClassroom, formatTeacherNames } =
  formatters

const classId = ref<string | null>(null)
// 周/月/季课表共用一个锚点日期，区间由 rangeFor 派生
const anchorDate = ref(todayValue())
const rangeMode = ref<'week' | 'month' | 'quarter'>('week')

const rangeLessons = ref<ScheduleLessonRecord[]>([])

const pageError = ref('')
const lookupLoading = ref(false)
const rangeLoading = ref(false)

const relations = useLessonRelations({
  lessonSources: () => [rangeLessons.value],
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
  refreshAfterChange: refreshRangeTimetable,
})
const {
  canMark,
  canExecute,
  formDialogVisible,
  formDialogMode,
  formDialogLesson,
  attendanceDialogVisible,
  attendanceLessonId,
  handleDialogSaved,
  handleLessonAction,
} = execution

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

// 班级筛选对课表统一生效（课表请求已带 classId，客户端过滤兜底）
const filteredRangeLessons = computed(() => sortedLessons(filterLessonsByClass(rangeLessons.value)))

function filterLessonsByClass(values: ScheduleLessonRecord[]) {
  if (!classId.value) {
    return values
  }
  return values.filter((lesson) => lesson.classId === classId.value)
}

// 补课弹窗的停课原课选项：当前区间内已加载的停课课次 + 按需单拉的关联缓存
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

async function refreshRangeTimetable() {
  if (!institutionId.value) {
    rangeLessons.value = []
    return
  }

  rangeLoading.value = true
  pageError.value = ''
  try {
    rangeLessons.value = sortedLessons(
      await fetchWeekTimetable({
        institutionId: institutionId.value,
        campusId: campusId.value,
        classId: classId.value,
        weekStart: timetableRange.value.start,
        weekEnd: timetableRange.value.end,
      })
    )
  } catch (error) {
    rangeLessons.value = []
    pageError.value = toMessage(error, '课表加载失败')
  } finally {
    rangeLoading.value = false
  }
}

async function refreshAll() {
  await refreshRangeTimetable()
}

/** 换校区由横幅芯片把全部页签重挂一遍，已选班级随重挂清空，这里只管本页的草稿 */
async function applyFilters() {
  await refreshScopeData()
  await refreshAll()
}

/** 重置 = 取消学期/班级筛选并生效（校区是全站范围，不由本页重置） */
async function resetFilters() {
  termId.value = null
  classId.value = null
  await applyFilters()
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

function handleWeekCellClick(payload: {
  date: string
  timeSlotId?: string | null
  classroomId?: string | null
}) {
  // 排课是计划动作，跳转排课管理页并预填空格上下文
  // 校区/学期一起带上：直达页刷新后共享范围是空的，只靠对方内存范围会重新撞上「缺少校区信息」
  router.push({
    path: '/scheduling',
    query: {
      create: '1',
      date: payload.date,
      timeSlotId: payload.timeSlotId || undefined,
      classroomId: payload.classroomId || undefined,
      classId: classId.value || undefined,
      // 校区是全站范围，排课管理读的是同一个 store，跳转不用再带 campusId
      termId: termId.value || undefined,
    },
  })
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
  anchorDate.value = target.lessonDate || anchorDate.value
  // 目标课次可能与当前班级筛选不同，切换班级保证定位后网格可见
  if (target.classId && target.classId !== classId.value) {
    classId.value = target.classId
  }
  refreshAll().then(() => {
    ElMessage.success(`已定位到 ${formatClass(target?.classId)} 的关联课次所在周期`)
  })
}

function applyRoutePreferences() {
  const date = route.query.date
  if (typeof date === 'string' && date) {
    anchorDate.value = date
  }

  const classQuery = route.query.classId
  if (typeof classQuery === 'string' && classQuery) {
    classId.value = classQuery
  }
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

// 页面停留时从其他入口带参跳转也要生效（初始化时由 initializePage 处理）
watch(
  () => route.query,
  async () => {
    if (route.name !== 'schedule-timetable') {
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
/* 日期导航区:周月季周期翻页（日页的 day 日期条样式各留一份，scoped 互不依赖） */
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
</style>
