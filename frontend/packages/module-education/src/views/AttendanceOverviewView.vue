<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <!-- 主操作行:按钮一律靠左、口径说明靠右;校区范围在横幅那颗芯片上选 -->
    <div class="table-toolbar">
      <el-button @click="jumpToToday">回到今天</el-button>
      <el-button :icon="Refresh" circle :loading="loading" @click="refreshAll" />
      <div class="education-context-text">
        看各班、各学员在日/月/季三种粒度下点没点名、点出了什么结果；点名与结课的日常操作在
        「课表与点名」，日档展开班级行也能直接补点。
      </div>
    </div>

    <!-- 统一筛选栏:学期/班级/学员点「查询」才生效;日期前后翻页是导航,保持选中即切换 -->
    <div class="page-toolbar">
      <FilterBar :loading="loading" @query="applyFilters" @reset="resetFilters">
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
        <StudentSelect
          v-if="period !== 'day' && groupMode === 'student'"
          v-model="summaryStudentId"
          :institution-id="institutionId"
          placeholder="只看某个学员"
        />
      </FilterBar>
      <div class="page-toolbar-datebar">
        <span class="datebar-label">点名周期</span>
        <el-radio-group v-model="period" size="small" @change="refreshAll">
          <el-radio-button value="day">日</el-radio-button>
          <el-radio-button value="month">月</el-radio-button>
          <el-radio-button value="quarter">季</el-radio-button>
        </el-radio-group>
        <el-radio-group
          v-if="period !== 'day'"
          v-model="groupMode"
          size="small"
          @change="refreshAll"
        >
          <el-radio-button value="class">按班级</el-radio-button>
          <el-radio-button value="student">按学员</el-radio-button>
        </el-radio-group>
        <el-button :icon="ArrowLeft" circle @click="shiftRange(-1)" />
        <el-date-picker
          v-model="anchorDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择日期"
          :clearable="false"
          @change="refreshAll"
        />
        <el-button :icon="ArrowRight" circle @click="shiftRange(1)" />
        <el-button @click="jumpToToday">{{ periodButtonLabel }}</el-button>
      </div>
    </div>

    <div class="dashboard-grid education-stat-grid">
      <article class="stat-card">
        <div class="stat-label">{{ period === 'day' ? '应点课次' : '区间课次' }}</div>
        <div class="stat-value">{{ stats.totalLessons }}</div>
        <div class="stat-hint">排除已停课课次</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">已点名</div>
        <div class="stat-value">{{ stats.fullyMarked }}</div>
        <div class="stat-hint">应到学员已全部点到</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">未点名</div>
        <div class="stat-value">{{ stats.pending }}</div>
        <div class="stat-hint">还有学员没点到，展开班级行处理</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">出勤率</div>
        <div class="stat-value">{{ attendanceRateText }}</div>
        <div class="stat-hint">出勤 {{ stats.present }} / 应到 {{ stats.expected }} 人次</div>
      </article>
    </div>

    <!-- 日档:课次级客户端聚合,展开班级行是每节课的点名明细,可直接补点 -->
    <article v-if="period === 'day'" class="panel-card education-panel">
      <div class="education-table-head">
        <div class="panel-title">按班级汇总</div>
        <div class="education-context-text">
          日期：{{ anchorDate || '未选择' }}；班级：{{ formatClass(classId) }}；范围：{{
            formatCampus(campusId)
          }}
          <el-button v-if="progressMissing > 0" link type="warning" @click="refreshAll">
            {{ progressMissing }} 节课点名进度加载失败，点击重试
          </el-button>
        </div>
      </div>

      <el-table
        :data="classRows"
        border
        row-key="classId"
        v-loading="loading"
        :empty-text="loading ? '' : '当前日期与筛选范围下暂无排课。'"
      >
        <!-- 班级行是客户端聚合,展开看该班每节课的点名明细 -->
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="lesson-expand">
              <div v-for="lesson in row.lessons" :key="lesson.id" class="lesson-expand-item">
                <div class="lesson-expand-main">
                  <strong>{{ compactTimeRange(lesson.startTime, lesson.endTime) || '待定时段' }}</strong>
                  <span class="lesson-expand-title">{{ lesson.lessonTitle || '未设主题' }}</span>
                  <el-tag size="small" :type="lessonStatusTagType(lesson.lessonStatus)">
                    {{ lessonStatusLabel(lesson.lessonStatus) }}
                  </el-tag>
                </div>
                <div class="lesson-expand-progress">
                  <el-tag size="small" :type="lessonMarkTagType(lesson)" effect="plain">
                    {{ lessonMarkText(lesson) }}
                  </el-tag>
                  <span class="lesson-expand-counts">{{ lessonCountText(lesson) }}</span>
                </div>
                <div v-if="canMark && isAttendanceAllowedStatus(lesson.lessonStatus)">
                  <el-button link type="success" @click="openAttendanceDialog(lesson)">点名</el-button>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="班级" min-width="220" fixed="left">
          <template #default="{ row }">
            <div class="class-cell">
              <span>{{ row.className }}</span>
              <span class="class-cell-meta"
                >{{ formatCampus(row.campusId) }} · {{ row.lessonCount }} 节课 ·
                {{ formatTeacherNames(row.teacherIds) }}</span
              >
            </div>
          </template>
        </el-table-column>
        <el-table-column label="点名情况" width="150">
          <template #default="{ row }">
            <el-tag :type="row.markTagType" effect="plain">{{ row.markText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="应到" width="80" align="center" prop="expectedTotal" sortable />
        <el-table-column label="出勤" width="80" align="center" prop="presentTotal" sortable />
        <el-table-column label="迟到" width="80" align="center" prop="lateTotal" sortable />
        <el-table-column label="请假" width="80" align="center" prop="leaveTotal" sortable />
        <el-table-column label="缺勤" width="80" align="center" prop="absentTotal" sortable />
        <el-table-column label="出勤率" width="110" align="center" prop="attendanceRate" sortable>
          <template #default="{ row }">
            <span v-if="row.attendanceRate === null" class="rate-empty">—</span>
            <el-tag v-else size="small" :type="rateTagType(row.attendanceRate)">
              {{ row.attendanceRate }}%
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

    </article>

    <!-- 月/季档:服务端聚合,展开班级行是按月小计;要看单节课切回日档 -->
    <article v-else class="panel-card education-panel">
      <div class="education-table-head">
        <div class="panel-title">{{ groupMode === 'class' ? '班级汇总' : '学员汇总' }}</div>
        <div class="education-context-text">
          区间：{{ summaryRange.dateFrom }} 至 {{ summaryRange.dateTo }}（{{ periodLabel }}）；分组：{{
            groupMode === 'class' ? '按班级' : '按学员'
          }}；班级：{{ formatClass(classId) }}；范围：{{ formatCampus(campusId) }}
        </div>
      </div>

      <el-table
        :data="summaryRows"
        border
        :row-key="summaryRowKey"
        v-loading="loading"
        :empty-text="loading ? '' : '当前区间与筛选范围下暂无考勤课次。'"
      >
        <el-table-column v-if="groupMode === 'class'" type="expand">
          <template #default="{ row }">
            <div class="lesson-expand">
              <div v-for="month in row.months ?? []" :key="month.month" class="lesson-expand-item">
                <div class="lesson-expand-main">
                  <strong>{{ month.month }}</strong>
                  <span class="lesson-expand-title">{{ month.lessonCount }} 节课</span>
                  <el-tag size="small" :type="monthMarkTagType(month)" effect="plain">
                    {{ monthMarkText(month) }}
                  </el-tag>
                </div>
                <div class="lesson-expand-progress">
                  <span class="lesson-expand-counts"
                    >应到 {{ month.expectedCount }} · 出勤 {{ month.presentCount }} · 迟到
                    {{ month.lateCount }} · 请假 {{ month.leaveCount }} · 缺勤
                    {{ month.absentCount }}</span
                  >
                </div>
                <div>
                  <span v-if="month.attendanceRate === null" class="rate-empty">—</span>
                  <el-tag v-else size="small" :type="rateTagType(month.attendanceRate)">
                    {{ month.attendanceRate }}%
                  </el-tag>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="groupMode === 'class'" label="班级" min-width="220" fixed="left">
          <template #default="{ row }">
            <div class="class-cell">
              <span>{{ row.className || `班级 #${row.classId}` }}</span>
              <span class="class-cell-meta">{{ row.lessonCount }} 节课</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-else label="学员" min-width="220" fixed="left">
          <template #default="{ row }">
            <div class="class-cell">
              <span>{{
                row.studentName
                  ? `${row.studentName}（${row.studentNo || '无学号'}）`
                  : `学员 #${row.studentId}`
              }}</span>
              <span v-if="(row.classNames ?? []).length > 0" class="class-cell-meta">{{
                row.classNames.join('、')
              }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="点名情况" width="150">
          <template #default="{ row }">
            <el-tag :type="rowMarkTagType(row)" effect="plain">{{ rowMarkText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="应到" width="80" align="center" prop="expectedCount" sortable />
        <el-table-column label="出勤" width="80" align="center" prop="presentCount" sortable />
        <el-table-column label="迟到" width="80" align="center" prop="lateCount" sortable />
        <el-table-column label="请假" width="80" align="center" prop="leaveCount" sortable />
        <el-table-column label="缺勤" width="80" align="center" prop="absentCount" sortable />
        <el-table-column label="出勤率" width="110" align="center" prop="attendanceRate" sortable>
          <template #default="{ row }">
            <span v-if="row.attendanceRate === null" class="rate-empty">—</span>
            <el-tag v-else size="small" :type="rateTagType(row.attendanceRate)">
              {{ row.attendanceRate }}%
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

    </article>

    <AttendanceDialog
      v-model="attendanceDialogVisible"
      :lesson-id="attendanceLessonId"
      :format-class="formatClass"
      @saved="refreshAll"
      @completed="refreshAll"
    />
  </section>
</template>

<script setup lang="ts">
import { ArrowLeft, ArrowRight, Refresh } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  fetchAttendanceSummary,
  fetchDayTimetable,
  fetchLessonAttendanceProgress,
  type AttendanceSummaryResponse,
  type AttendanceSummaryRow,
  type LessonAttendanceProgress,
  type ScheduleLessonRecord,
} from '@cornerstone/module-education/api/education'
import { useEducationScope } from '@cornerstone/module-education/composables/useEducationScope'
import { useLessonFormatters } from '@cornerstone/module-education/composables/useLessonFormatters'
import { useLessonExecution } from '@cornerstone/module-education/composables/useLessonExecution'
import { ensureClassLabels } from '@cornerstone/module-education/composables/classLabels'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import FilterBar from '@cornerstone/core/components/FilterBar.vue'
import ClassSelect from './components/ClassSelect.vue'
import StudentSelect from './components/StudentSelect.vue'
import AttendanceDialog from './components/AttendanceDialog.vue'
import {
  addDays,
  addMonths,
  compactTimeRange,
  isAttendanceAllowedStatus,
  lastDayOfMonth,
  lessonStatusLabel,
  lessonStatusTagType,
  sortedLessons,
  toMessage,
  todayValue,
} from './components/schedule-utils'

interface ClassAttendanceRow {
  classId: string
  className: string
  campusId?: string | null
  teacherIds: Array<string | number>
  lessons: ScheduleLessonRecord[]
  lessonCount: number
  /** 应到>0 且全部点到 的课次数 */
  fullyMarkedLessons: number
  expectedTotal: number
  presentTotal: number
  lateTotal: number
  leaveTotal: number
  absentTotal: number
  attendanceRate: number | null
  markTagType: 'success' | 'warning' | 'danger' | 'info'
  markText: string
}

type AttendancePeriod = 'day' | 'month' | 'quarter'
type SummaryGroupMode = 'class' | 'student'

const institutionStore = useInstitutionStore()
const scopeStore = useEducationScopeStore()
const { campusId, termId } = storeToRefs(scopeStore)
/**
 * 校区范围在横幅那颗芯片上选，本页跟着它筛课次。
 * 教师/教室/时段的反查字典在 useLessonFormatters 里按机构全量拉，不跟范围收窄。
 */
const { institutionId, terms, loadScope } = useEducationScope()

const formatters = useLessonFormatters()
const { formatClass, formatCampus, formatTeacherNames, loadSupportingOptions } = formatters

const route = useRoute()

const classId = ref<string | null>(null)

/** 周期三档:日档走课次级客户端聚合,月/季档走服务端 summary 聚合 */
const period = ref<AttendancePeriod>('day')
const groupMode = ref<SummaryGroupMode>('class')
const anchorDate = ref(todayValue())
const summaryStudentId = ref<string | null>(null)

const lessons = ref<ScheduleLessonRecord[]>([])
const progress = ref<Record<string, LessonAttendanceProgress>>({})
const progressMissing = ref(0)
const summaryData = ref<AttendanceSummaryResponse | null>(null)

const pageError = ref('')
const loading = ref(false)

const execution = useLessonExecution({
  formatClass,
  refreshAfterChange: refreshAll,
})
const { canMark, attendanceDialogVisible, attendanceLessonId, openAttendanceDialog } = execution

function progressFor(lesson: ScheduleLessonRecord) {
  return progress.value[lesson.id]
}

/** 应到>0 且全部点到：点名完成的判定口径与「课表与点名」一致 */
function isLessonFullyMarked(lesson: ScheduleLessonRecord) {
  const item = progressFor(lesson)
  if (!item) {
    return false
  }
  return item.expectedCount > 0 && item.markedCount >= item.expectedCount
}

function lessonMarkText(lesson: ScheduleLessonRecord) {
  const item = progressFor(lesson)
  if (!item) {
    return '待加载'
  }
  if (item.expectedCount === 0) {
    return '无应到学员'
  }
  if (item.markedCount >= item.expectedCount) {
    return '已点完'
  }
  if (item.markedCount > 0) {
    return `点名中 ${item.markedCount}/${item.expectedCount}`
  }
  return '未点名'
}

function lessonMarkTagType(lesson: ScheduleLessonRecord): 'success' | 'warning' | 'danger' | 'info' {
  const item = progressFor(lesson)
  if (!item) {
    return 'info'
  }
  if (item.expectedCount === 0) {
    return 'info'
  }
  if (item.markedCount >= item.expectedCount) {
    return 'success'
  }
  return item.markedCount > 0 ? 'warning' : 'danger'
}

function lessonCountText(lesson: ScheduleLessonRecord) {
  const item = progressFor(lesson)
  if (!item) {
    return ''
  }
  return `出勤 ${item.presentCount} · 迟到 ${item.lateCount} · 请假 ${item.leaveCount} · 缺勤 ${item.absentCount}`
}

const allowedLessons = computed(() =>
  lessons.value.filter((lesson) => isAttendanceAllowedStatus(lesson.lessonStatus))
)

const daySummary = computed(() => {
  const rows = allowedLessons.value
  // 待点名口径与「课表与点名」一致：应到>0 且还有学员没点到；无应到学员的课次两边都不算
  const pending = rows.filter((lesson) => {
    const item = progressFor(lesson)
    return Boolean(item) && item.expectedCount > 0 && item.markedCount < item.expectedCount
  })
  return {
    totalLessons: rows.length,
    fullyMarked: rows.filter(isLessonFullyMarked).length,
    pending: pending.length,
    expected: rows.reduce((sum, lesson) => sum + (progressFor(lesson)?.expectedCount ?? 0), 0),
    present: rows.reduce((sum, lesson) => sum + (progressFor(lesson)?.presentCount ?? 0), 0),
  }
})

const stats = computed(() => {
  if (period.value === 'day') {
    return daySummary.value
  }
  const summary = summaryData.value?.summary
  return {
    totalLessons: summary?.lessonCount ?? 0,
    fullyMarked: summary?.fullyMarkedLessons ?? 0,
    pending: summary?.pendingLessons ?? 0,
    expected: summary?.expectedCount ?? 0,
    present: summary?.presentCount ?? 0,
  }
})

const attendanceRateText = computed(() => {
  if (stats.value.expected === 0) {
    return '—'
  }
  return `${Math.round((stats.value.present / stats.value.expected) * 100)}%`
})

const classRows = computed<ClassAttendanceRow[]>(() => {
  const groups = new Map<string, ScheduleLessonRecord[]>()
  for (const lesson of allowedLessons.value) {
    const key = lesson.classId
    if (!key) {
      continue
    }
    const list = groups.get(key)
    if (list) {
      list.push(lesson)
    } else {
      groups.set(key, [lesson])
    }
  }

  const rows: ClassAttendanceRow[] = []
  for (const [classIdKey, classLessons] of groups) {
    const ordered = sortedLessons(classLessons)
    const totals = ordered.reduce(
      (acc, lesson) => {
        const item = progressFor(lesson)
        if (item) {
          acc.expected += item.expectedCount
          acc.present += item.presentCount
          acc.late += item.lateCount
          acc.leave += item.leaveCount
          acc.absent += item.absentCount
        }
        return acc
      },
      { expected: 0, present: 0, late: 0, leave: 0, absent: 0 }
    )
    const fullyMarked = ordered.filter(isLessonFullyMarked).length
    const teacherIds: Array<string | number> = []
    for (const lesson of ordered) {
      for (const id of lesson.teacherIds ?? []) {
        if (!teacherIds.includes(id)) {
          teacherIds.push(id)
        }
      }
    }

    const mark = markTagOf(totals.expected, fullyMarked, ordered.length)

    rows.push({
      classId: classIdKey,
      className: formatClass(classIdKey),
      campusId: ordered[0]?.campusId ?? null,
      teacherIds,
      lessons: ordered,
      lessonCount: ordered.length,
      fullyMarkedLessons: fullyMarked,
      expectedTotal: totals.expected,
      presentTotal: totals.present,
      lateTotal: totals.late,
      leaveTotal: totals.leave,
      absentTotal: totals.absent,
      attendanceRate: totals.expected === 0 ? null : Math.round((totals.present / totals.expected) * 100),
      markTagType: mark.type,
      markText: mark.text,
    })
  }

  return rows.sort((a, b) => {
    const left = a.lessons[0]?.startTime || ''
    const right = b.lessons[0]?.startTime || ''
    if (left !== right) {
      return left < right ? -1 : 1
    }
    return a.className.localeCompare(b.className, 'zh-Hans-CN')
  })
})

const summaryRows = computed<AttendanceSummaryRow[]>(() => summaryData.value?.rows ?? [])

const summaryRange = computed(() => {
  if (period.value === 'month') {
    const first = `${anchorDate.value.slice(0, 7)}-01`
    return { dateFrom: first, dateTo: lastDayOfMonth(first) }
  }
  const anchorMonth = Number(anchorDate.value.slice(5, 7))
  const first = `${anchorDate.value.slice(0, 4)}-${String(Math.floor((anchorMonth - 1) / 3) * 3 + 1).padStart(2, '0')}-01`
  return { dateFrom: first, dateTo: lastDayOfMonth(addMonths(first, 2)) }
})

const periodLabel = computed(() =>
  period.value === 'month' ? '月' : period.value === 'quarter' ? '季' : '日'
)

const periodButtonLabel = computed(() =>
  period.value === 'day' ? '今天' : period.value === 'month' ? '本月' : '本季'
)

/** 统一的「点名情况」文案:应到 0 是"没有可点的",不是没点 */
function markTagOf(
  expected: number,
  fullyMarked: number,
  lessonCount: number
): { type: 'success' | 'warning' | 'danger' | 'info'; text: string } {
  if (expected === 0) {
    return { type: 'info', text: '无应到学员' }
  }
  if (fullyMarked >= lessonCount) {
    return { type: 'success', text: `已点完 ${fullyMarked}/${lessonCount} 节` }
  }
  if (fullyMarked === 0) {
    return { type: 'danger', text: '未点名' }
  }
  return { type: 'warning', text: `已点 ${fullyMarked}/${lessonCount} 节` }
}

function rowMarkTagType(row: AttendanceSummaryRow) {
  return markTagOf(row.expectedCount, row.fullyMarkedLessons, row.lessonCount).type
}

function rowMarkText(row: AttendanceSummaryRow) {
  return markTagOf(row.expectedCount, row.fullyMarkedLessons, row.lessonCount).text
}

function monthMarkTagType(month: { expectedCount: number; fullyMarkedLessons: number; lessonCount: number }) {
  return markTagOf(month.expectedCount, month.fullyMarkedLessons, month.lessonCount).type
}

function monthMarkText(month: { expectedCount: number; fullyMarkedLessons: number; lessonCount: number }) {
  return markTagOf(month.expectedCount, month.fullyMarkedLessons, month.lessonCount).text
}

function rateTagType(rate: number | null): 'success' | 'warning' | 'danger' | 'info' {
  if (rate === null) {
    return 'info'
  }
  if (rate >= 85) {
    return 'success'
  }
  return rate >= 60 ? 'warning' : 'danger'
}

function summaryRowKey(row: AttendanceSummaryRow) {
  return row.studentId ? `student-${row.studentId}` : `class-${row.classId}`
}

async function refreshScopeData() {
  if (!institutionId.value) {
    terms.value = []
    classId.value = null
    termId.value = null
    summaryStudentId.value = null
    return
  }

  pageError.value = ''
  try {
    await loadScope()
    // 教师名字典按机构全量拉（口径同「课表与点名」），不拉的话班级行教师渲染成裸 ID
    await loadSupportingOptions()
  } catch (error) {
    pageError.value = toMessage(error, '范围数据加载失败')
  }
}

async function loadProgress(values: ScheduleLessonRecord[]) {
  if (values.length === 0) {
    progress.value = {}
    progressMissing.value = 0
    return
  }

  try {
    const list = await fetchLessonAttendanceProgress(values.map((lesson) => String(lesson.id)))
    progress.value = Object.fromEntries(list.map((item) => [String(item.lessonId), item]))
    progressMissing.value = 0
  } catch {
    progress.value = {}
    progressMissing.value = values.length
  }
}

async function refreshDayView() {
  if (!institutionId.value || !anchorDate.value) {
    lessons.value = []
    progress.value = {}
    progressMissing.value = 0
    return
  }

  pageError.value = ''
  try {
    lessons.value = sortedLessons(
      await fetchDayTimetable({
        institutionId: institutionId.value,
        campusId: campusId.value,
        classId: classId.value,
        lessonDate: anchorDate.value,
      })
    )
    // 表格行只有班级 id，名称走批量回查缓存；补不到按「班级 #id」显示
    await ensureClassLabels(
      institutionId.value,
      lessons.value.map((lesson) => lesson.classId)
    )
    await loadProgress(allowedLessons.value)
  } catch (error) {
    lessons.value = []
    progress.value = {}
    progressMissing.value = 0
    pageError.value = toMessage(error, '点名总览加载失败')
  }
}

async function refreshSummaryView() {
  if (!institutionId.value) {
    summaryData.value = null
    return
  }

  pageError.value = ''
  try {
    summaryData.value = await fetchAttendanceSummary({
      institutionId: institutionId.value,
      campusId: campusId.value,
      classId: classId.value,
      studentId: groupMode.value === 'student' ? summaryStudentId.value : null,
      dateFrom: summaryRange.value.dateFrom,
      dateTo: summaryRange.value.dateTo,
      groupBy: groupMode.value,
    })
  } catch (error) {
    summaryData.value = null
    pageError.value = toMessage(error, '考勤汇总加载失败')
  }
}

async function refreshAll() {
  if (period.value === 'day') {
    await refreshDayView()
  } else {
    await refreshSummaryView()
  }
}

/** 换校区由横幅芯片把全部页签重挂一遍，已选班级随重挂清空，这里只管本页的草稿 */
async function applyFilters() {
  await refreshScopeData()
  await refreshAll()
}

/** 重置 = 取消学期/班级/学员筛选并生效（校区是全站范围，不由本页重置） */
async function resetFilters() {
  termId.value = null
  classId.value = null
  summaryStudentId.value = null
  await applyFilters()
}

/** 日期导航:日 ±1 天、月 ±1 月、季 ±3 月,月末日被 addMonths 钳制后仍落在同一个月里 */
async function shiftRange(delta: number) {
  if (period.value === 'day') {
    anchorDate.value = addDays(anchorDate.value || todayValue(), delta)
  } else if (period.value === 'month') {
    anchorDate.value = addMonths(anchorDate.value || todayValue(), delta)
  } else {
    anchorDate.value = addMonths(anchorDate.value || todayValue(), delta * 3)
  }
  await refreshAll()
}

async function jumpToToday() {
  anchorDate.value = todayValue()
  await refreshAll()
}

async function initializePage() {
  // 支持带 ?date=2026-10-02 从别处跳进来
  const date = route.query.date
  if (typeof date === 'string' && date) {
    anchorDate.value = date
  }

  pageError.value = ''
  try {
    await institutionStore.loadInstitutions()
    if (institutionId.value) {
      await refreshScopeData()
    }
    await refreshAll()
  } catch (error) {
    pageError.value = toMessage(error, '页面初始化失败')
  }
}

watch(
  () => institutionStore.currentInstitutionId,
  async () => {
    // 换机构时 store 的 reset() 已清空校区范围，本页只清自己选的筛选
    termId.value = null
    classId.value = null
    summaryStudentId.value = null
    await refreshScopeData()
    await refreshAll()
  }
)

onMounted(() => {
  initializePage()
})
</script>

<style scoped>
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

.class-cell {
  display: grid;
  gap: 4px;
}

.class-cell-meta {
  color: var(--text-secondary);
  font-size: 12px;
}

.lesson-expand {
  display: grid;
  gap: 10px;
  padding: 4px 12px 8px 48px;
}

.lesson-expand-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 10px;
  background: var(--subtle-bg);
}

.lesson-expand-main {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--text-primary);
}

.lesson-expand-title {
  color: var(--text-secondary);
  font-size: 13px;
}

.lesson-expand-progress {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.lesson-expand-counts {
  color: var(--text-secondary);
  font-size: 13px;
}

.rate-empty {
  color: var(--text-secondary);
}
</style>
