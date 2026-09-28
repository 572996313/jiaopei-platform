<template>
  <section class="education-page">
    <!-- 本页动作靠左、圆形刷新居中作分隔、会跳走的入口在它右边、说明靠右 -->
    <div class="table-toolbar">
      <el-button :icon="Refresh" circle :loading="boardLoading" @click="refreshBoard" />
      <el-button type="primary" @click="goToSchedule()">进入日常教务中心</el-button>
      <div class="education-context-text">
        先看今天的课次执行、点名进度和变动提醒，再进入排课、报名、入班和教师档案。
      </div>
    </div>

    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <div class="dashboard-grid education-stat-grid">
      <article
        v-for="card in workbenchCards"
        :key="card.label"
        class="stat-card"
        v-loading="boardLoading"
      >
        <div class="stat-label">{{ card.label }}</div>
        <div class="stat-value">{{ card.value }}</div>
        <div class="stat-hint">{{ card.hint }}</div>
      </article>
    </div>

    <div class="dashboard-grid workbench-grid">
      <article class="panel-card panel-card-wide">
        <div class="workbench-section-head">
          <div>
            <div class="panel-title">今日执行清单</div>
            <div class="education-context-text">
              {{ todayLabel }} / {{ formatInstitution(institutionId) }} /
              {{ formatCampus(campusId) }}
            </div>
          </div>
          <el-tag type="warning" effect="plain"
            >待点名 {{ pendingAttendanceLessons.length }}</el-tag
          >
        </div>

        <div v-if="todayQueueItems.length === 0" class="education-empty">
          当前范围下暂无课次执行项，可以先去处理报名、入班或教师档案。
        </div>

        <div v-else class="workbench-list">
          <button
            v-for="item in todayQueueItems"
            :key="item.key"
            class="workbench-item"
            type="button"
            @click="handleQueueAction(item)"
          >
            <div class="workbench-item-main">
              <div class="workbench-item-title">{{ item.title }}</div>
              <div class="workbench-item-text">{{ item.text }}</div>
            </div>
            <div class="workbench-item-meta">{{ item.meta }}</div>
          </button>
        </div>
      </article>

      <article class="panel-card">
        <div class="workbench-section-head">
          <div>
            <div class="panel-title">待点名课次</div>
            <div class="education-context-text">未完成点名的课次会优先出现在这里。</div>
          </div>
          <el-button link type="primary" @click="goToSchedule('pending')">去处理</el-button>
        </div>

        <div v-if="pendingAttendanceLessons.length === 0" class="education-empty">
          当前日期暂无待点名课次。
        </div>

        <div v-else class="mini-list">
          <button
            v-for="lesson in pendingAttendanceLessons.slice(0, 4)"
            :key="lesson.id"
            type="button"
            class="mini-list-item"
            @click="goToSchedule('pending')"
          >
            <strong>{{ formatClass(lesson.classId) }}</strong>
            <span>{{ compactTimeRange(lesson.startTime, lesson.endTime) || '待定时段' }}</span>
            <span>{{ attendanceProgressText(lesson) }}</span>
          </button>
        </div>
      </article>

      <article class="panel-card">
        <div class="workbench-section-head">
          <div>
            <div class="panel-title">变动提醒</div>
            <div class="education-context-text">备注、缺学员点名和课次提示都会收敛到这里。</div>
          </div>
          <el-button link type="primary" @click="goToSchedule('reminders')">查看</el-button>
        </div>

        <div v-if="reminderItems.length === 0" class="education-empty">今天暂无额外变动提醒。</div>

        <div v-else class="mini-list">
          <button
            v-for="item in reminderItems.slice(0, 4)"
            :key="item.key"
            type="button"
            class="mini-list-item is-warning"
            @click="goToSchedule('reminders')"
          >
            <strong>{{ item.title }}</strong>
            <span>{{ item.text }}</span>
          </button>
        </div>
      </article>

      <article class="panel-card panel-card-wide">
        <div class="workbench-section-head">
          <div>
            <div class="panel-title">今日课次节奏</div>
            <div class="education-context-text">按今日课次时间顺序快速回看执行节奏。</div>
          </div>
          <el-button link type="primary" @click="goToSchedule('day')">打开日课表</el-button>
        </div>

        <div v-if="todayLessons.length === 0" class="education-empty">
          当前范围下今天还没有课次排课。
        </div>

        <div v-else class="lesson-rhythm-list">
          <div v-for="lesson in todayLessons" :key="lesson.id" class="lesson-rhythm-item">
            <div class="lesson-rhythm-time">
              {{ compactTimeRange(lesson.startTime, lesson.endTime) || '待定' }}
            </div>
            <div class="lesson-rhythm-main">
              <div class="lesson-rhythm-title">
                {{ formatClass(lesson.classId) }}
                <el-tag size="small" :type="lessonStatusTagType(lesson.lessonStatus)">
                  {{ lessonStatusLabel(lesson.lessonStatus) }}
                </el-tag>
              </div>
              <div class="lesson-rhythm-text">
                {{ formatTeacherNames(lesson.teacherIds) }} /
                {{ formatClassroom(lesson.classroomId) }}
              </div>
            </div>
            <div class="lesson-rhythm-progress">
              {{ attendanceProgressText(lesson) }}
            </div>
          </div>
        </div>
      </article>

      <article class="panel-card">
        <div class="panel-title">范围与资源</div>
        <ul class="panel-list">
          <li>当前机构：{{ formatInstitution(institutionId) }}</li>
          <li>当前校区：{{ formatCampus(campusId) }}</li>
          <li>班级数量：{{ classTotal }}</li>
          <li>教师数量：{{ teachers.length }}</li>
          <li>学员数量（全机构）：{{ studentTotal }}</li>
          <li>教室数量：{{ classrooms.length }}</li>
        </ul>
      </article>

      <article class="panel-card">
        <div class="panel-title">常用入口</div>
        <div class="quick-link-list">
          <button
            v-for="item in quickLinks"
            :key="item.label"
            class="quick-link-card"
            type="button"
            @click="goTo(item.to)"
          >
            <div class="quick-link-title">{{ item.label }}</div>
            <div class="quick-link-text">{{ item.hint }}</div>
          </button>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import {
  fetchClassCount,
  fetchDayTimetable,
  fetchLessonAttendancePage,
  fetchStudentPage,
  fetchTeachers,
  type AttendanceLessonPage,
  type ScheduleLessonRecord,
  type TeacherSummary,
} from '@cornerstone/module-education/api/education'
import {
  fetchClassrooms,
  type ClassroomSummary,
} from '@cornerstone/module-organization/api/organization'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'
import { useEducationScope } from '@cornerstone/module-education/composables/useEducationScope'
import {
  classLabelOf,
  ensureClassLabels,
} from '@cornerstone/module-education/composables/classLabels'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'

interface QueueItem {
  key: string
  title: string
  text: string
  meta: string
  action: 'schedule'
  focus?: 'pending' | 'reminders' | 'completed' | 'day'
}

interface ReminderItem {
  key: string
  title: string
  text: string
}

const router = useRouter()
const institutionStore = useInstitutionStore()
/** 与组织/教务各页共用同一份校区范围，默认值是当前账号在本机构设的默认校区 */
const scopeStore = useEducationScopeStore()
const { campusId } = storeToRefs(scopeStore)
const { institutions, institutionId, campuses, loadScope } = useEducationScope()

const today = ref(todayValue())
const boardLoading = ref(false)
const pageError = ref('')
const teachers = ref<TeacherSummary[]>([])
const classrooms = ref<ClassroomSummary[]>([])
/** 学员条数按分页接口的 total 取：全量学员有数千条，只为一个数字把它们拉下来不值得 */
const studentTotal = ref(0)
/** 班级改成按需搜索后本机不再有全量数组，班级数量走 /education/classes/count */
const classTotal = ref(0)
const todayLessons = ref<ScheduleLessonRecord[]>([])
const attendancePages = ref<Record<string, AttendanceLessonPage>>({})

// 今日课表只有 classId，班级名按 id 回查补进缓存（下拉不再持有全量班级当字典）
watch(todayLessons, (lessons) => {
  void ensureClassLabels(
    institutionId.value,
    lessons.map((item) => item.classId)
  )
})

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

const teacherMap = computed(() => {
  return new Map(
    teachers.value.map((item) => [
      item.id,
      item.teacherName || item.teacherCode || `教师 #${item.id}`,
    ])
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

// 与日常教务中心口径一致：调课/补课课次仍要实际上课，可点名可结课；停课不产生考勤
function isAttendanceAllowedStatus(status?: string | null) {
  return (
    status === 'scheduled' ||
    status === 'rescheduled' ||
    status === 'makeup' ||
    status === 'completed'
  )
}

function canCompleteLesson(lesson: Pick<ScheduleLessonRecord, 'lessonStatus'>) {
  return (
    lesson.lessonStatus === 'scheduled' ||
    lesson.lessonStatus === 'rescheduled' ||
    lesson.lessonStatus === 'makeup'
  )
}

const pendingAttendanceLessons = computed(() => {
  return sortedLessons(
    todayLessons.value.filter((lesson) => {
      if (!isAttendanceAllowedStatus(lesson.lessonStatus) || !canCompleteLesson(lesson)) {
        return false
      }
      const page = attendancePages.value[lesson.id]
      if (!page) {
        return true
      }
      return page.summary.markedCount < page.summary.expectedCount
    })
  )
})

const readyToCompleteLessons = computed(() => {
  return sortedLessons(
    todayLessons.value.filter((lesson) => {
      if (!canCompleteLesson(lesson)) {
        return false
      }
      const page = attendancePages.value[lesson.id]
      return Boolean(
        page &&
        page.summary.expectedCount > 0 &&
        page.summary.markedCount >= page.summary.expectedCount
      )
    })
  )
})

const reminderItems = computed<ReminderItem[]>(() => {
  const items: ReminderItem[] = []

  for (const lesson of todayLessons.value) {
    const page = attendancePages.value[lesson.id]
    if (page?.message) {
      items.push({
        key: `${lesson.id}-attendance-message`,
        title:
          `${formatClass(lesson.classId)} ${compactTimeRange(lesson.startTime, lesson.endTime) || ''}`.trim(),
        text: page.message,
      })
    }
    if (lesson.remark) {
      items.push({
        key: `${lesson.id}-remark`,
        title: `${formatClass(lesson.classId)} 变动备注`,
        text: lesson.remark,
      })
    }
    if (lesson.lessonStatus === 'cancelled') {
      items.push({
        key: `${lesson.id}-cancelled`,
        title: `${formatClass(lesson.classId)} 已取消`,
        text: '该课次已取消，请同步关注补课与通知安排。',
      })
    }
  }

  return items.slice(0, 8)
})

const workbenchCards = computed(() => {
  const nextLesson = sortedLessons(todayLessons.value).find(
    (lesson) => lesson.lessonStatus === 'scheduled'
  )

  return [
    {
      label: '今日课次',
      value: String(todayLessons.value.length),
      hint: `${todayLabel.value} 的日课表`,
    },
    {
      label: '待点名课次',
      value: String(pendingAttendanceLessons.value.length),
      hint: '优先处理未完成点名的课次',
    },
    {
      label: '待结课课次',
      value: String(readyToCompleteLessons.value.length),
      hint: '点名完成后可直接结课',
    },
    {
      label: '下一节课',
      value: nextLesson
        ? compactTimeRange(nextLesson.startTime, nextLesson.endTime) || '待定'
        : '暂无',
      hint: nextLesson
        ? `${formatClass(nextLesson.classId)} / ${formatTeacherNames(nextLesson.teacherIds)}`
        : '当前没有待执行课次',
    },
  ]
})

const todayQueueItems = computed<QueueItem[]>(() => {
  const queue: QueueItem[] = []

  for (const lesson of pendingAttendanceLessons.value.slice(0, 3)) {
    queue.push({
      key: `${lesson.id}-pending`,
      title: `${compactTimeRange(lesson.startTime, lesson.endTime) || '待定时段'} ${formatClass(lesson.classId)} 待点名`,
      text: `${formatTeacherNames(lesson.teacherIds)} / ${formatClassroom(lesson.classroomId)} / ${attendanceProgressText(lesson)}`,
      meta: '立即点名',
      action: 'schedule',
      focus: 'pending',
    })
  }

  for (const lesson of readyToCompleteLessons.value.slice(0, 2)) {
    queue.push({
      key: `${lesson.id}-complete`,
      title: `${compactTimeRange(lesson.startTime, lesson.endTime) || '待定时段'} ${formatClass(lesson.classId)} 可结课`,
      text: '点名状态已覆盖应到学员，可以进入日常教务中心完成结课。',
      meta: '去结课',
      action: 'schedule',
      focus: 'completed',
    })
  }

  for (const item of reminderItems.value.slice(0, 2)) {
    queue.push({
      key: `${item.key}-reminder`,
      title: item.title,
      text: item.text,
      meta: '查看提醒',
      action: 'schedule',
      focus: 'reminders',
    })
  }

  return queue
})

const quickLinks = [
  { label: '日常教务中心', hint: '排课、点名、结课、日周课表', to: '/schedule' },
  { label: '学员报名', hint: '处理报名记录，报上名再去入班', to: '/enrollments' },
  { label: '学员档案', hint: '录入与维护学员本身、家长联系人', to: '/students' },
  { label: '班级名册', hint: '查看班级人数与在班状态', to: '/class-roster' },
  { label: '班级档案', hint: '维护班级主档与任课教师', to: '/classes' },
  { label: '教师档案', hint: '维护教师基础信息和所属校区', to: '/teachers' },
]

const todayLabel = computed(() => {
  const date = new Date(`${today.value}T00:00:00`)
  return `${today.value} ${weekdayLabel(date.getDay() === 0 ? 7 : date.getDay())}`
})

function todayValue() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}

function sortedLessons(values: ScheduleLessonRecord[]) {
  return [...values].sort((left, right) => {
    const leftKey = `${left.startTime || ''} ${left.id}`
    const rightKey = `${right.startTime || ''} ${right.id}`
    return leftKey.localeCompare(rightKey)
  })
}

function normalizeTeacherIds(values?: Array<string | number> | null) {
  return Array.from(new Set((values || []).map((item) => String(item))))
}

function compactTimeRange(startTime?: string | null, endTime?: string | null) {
  if (startTime && endTime) {
    return `${startTime.slice(0, 5)}-${endTime.slice(0, 5)}`
  }
  return startTime?.slice(0, 5) || endTime?.slice(0, 5) || ''
}

function formatInstitution(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return institutionMap.value.get(id) || `机构 #${id}`
}

function formatCampus(id?: string | null) {
  if (!id) {
    return '全部校区'
  }
  return campusMap.value.get(id) || `校区 #${id}`
}

function formatClass(id?: string | null) {
  if (!id) {
    return '未分配班级'
  }
  return classLabelOf(id)
}

function formatClassroom(id?: string | null) {
  if (!id) {
    return '未分配教室'
  }
  return classroomMap.value.get(id) || `教室 #${id}`
}

function formatTeacherNames(values?: Array<string | number> | null) {
  const ids = normalizeTeacherIds(values)
  if (ids.length === 0) {
    return '未分配教师'
  }
  return ids.map((id) => teacherMap.value.get(id) || `教师 #${id}`).join('、')
}

function lessonStatusLabel(status?: string | null) {
  switch (status) {
    case 'completed':
      return '已结课'
    case 'cancelled':
      return '已停课'
    case 'rescheduled':
      return '已调课'
    case 'makeup':
      return '已补课'
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
    case 'rescheduled':
      return 'primary'
    case 'makeup':
      return 'danger'
    case 'scheduled':
    default:
      return 'warning'
  }
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

function attendanceProgressText(lesson: ScheduleLessonRecord) {
  if (lesson.lessonStatus === 'cancelled') {
    return '已停课，无需点名'
  }
  const page = attendancePages.value[lesson.id]
  if (!page) {
    return lesson.lessonStatus === 'completed' ? '已结课，待回查点名' : '待加载点名进度'
  }
  return `已点 ${page.summary.markedCount} / 应到 ${page.summary.expectedCount}`
}

function goTo(path: string) {
  router.push(path)
}

function goToSchedule(focus?: 'pending' | 'reminders' | 'completed' | 'day') {
  router.push({
    path: '/schedule',
    query: {
      date: today.value,
      tab: focus === 'day' ? 'timetable' : 'management',
      focus: focus || undefined,
    },
  })
}

function handleQueueAction(item: QueueItem) {
  if (item.action === 'schedule') {
    goToSchedule(item.focus)
  }
}

async function loadSupportingResources() {
  if (!institutionId.value) {
    teachers.value = []
    classrooms.value = []
    studentTotal.value = 0
    return
  }

  const [teacherList, classroomList, studentPage, classCount] = await Promise.all([
    fetchTeachers({ institutionId: institutionId.value, campusId: campusId.value }),
    // 不传校区时后端返回全部教室；之前未选校区会跳过加载，导致"教室数量：0"
    fetchClassrooms(campusId.value),
    // 学员按需搜索后本页不再持有全量列表，数量只取分页结果的 total。
    // 学员档案是机构级（校区由班级推导），所以这一条不随校区收窄，标签上写明
    fetchStudentPage({
      institutionId: institutionId.value,
      page: 1,
      pageSize: 1,
    }),
    // 班级同样改成按需搜索，条数走服务端统计接口
    fetchClassCount({ institutionId: institutionId.value, campusId: campusId.value }),
  ])

  teachers.value = teacherList
  classrooms.value = classroomList
  studentTotal.value = studentPage.total
  classTotal.value = classCount
}

async function loadAttendancePages(lessons: ScheduleLessonRecord[]) {
  if (lessons.length === 0) {
    attendancePages.value = {}
    return
  }

  const pages = await Promise.all(
    lessons.map(async (lesson) => {
      try {
        return [lesson.id, await fetchLessonAttendancePage(lesson.id)] as const
      } catch {
        return null
      }
    })
  )

  attendancePages.value = Object.fromEntries(
    pages.filter((item): item is readonly [string, AttendanceLessonPage] => Boolean(item))
  )
}

async function refreshBoard() {
  if (!institutionId.value) {
    todayLessons.value = []
    attendancePages.value = {}
    return
  }

  boardLoading.value = true
  pageError.value = ''
  try {
    // 校区范围由横幅芯片初始化；这里 await 一次，保证首屏那趟查询按已落定的范围取数
    await scopeStore.initScope(institutionId.value)
    await loadScope()
    await loadSupportingResources()
    const lessons = sortedLessons(
      await fetchDayTimetable({
        institutionId: institutionId.value,
        campusId: campusId.value,
        lessonDate: today.value,
      })
    )
    todayLessons.value = lessons
    await loadAttendancePages(lessons)
  } catch (error) {
    todayLessons.value = []
    attendancePages.value = {}
    pageError.value = error instanceof Error && error.message ? error.message : '工作台加载失败'
  } finally {
    boardLoading.value = false
  }
}

/** 首进只做「拉机构清单」，取数与 refreshBoard 同一条路，范围初始化也在里面 */
async function initializeBoard() {
  pageError.value = ''
  try {
    await institutionStore.loadInstitutions()
  } catch (error) {
    pageError.value = error instanceof Error && error.message ? error.message : '工作台初始化失败'
    return
  }
  await refreshBoard()
}

onMounted(() => {
  initializeBoard()
})

watch(
  () => institutionStore.currentInstitutionId,
  async () => {
    // 换机构时 store 的 reset() 已清空范围，refreshBoard 里的 initScope 会在新机构上重新落一次
    await refreshBoard()
  }
)
</script>

<style scoped>
.workbench-section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
}

.mini-list,
.lesson-rhythm-list {
  display: grid;
  gap: 12px;
}

.mini-list-item {
  display: grid;
  gap: 6px;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: 16px;
  color: var(--tint-ink);
  background: var(--subtle-bg);
  text-align: left;
  cursor: pointer;
}

.mini-list-item strong {
  color: var(--text-primary);
}

.mini-list-item span {
  color: var(--text-secondary);
  font-size: 13px;
}

.mini-list-item.is-warning {
  border-color: rgba(249, 199, 79, 0.46);
  background: var(--soft-warn-bg);
}

.lesson-rhythm-item {
  display: grid;
  grid-template-columns: 108px minmax(0, 1fr) 160px;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 18px;
  background: var(--subtle-bg);
}

.lesson-rhythm-time {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.lesson-rhythm-main {
  display: grid;
  gap: 6px;
}

.lesson-rhythm-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-weight: 700;
  color: var(--text-primary);
}

.lesson-rhythm-text,
.lesson-rhythm-progress {
  color: var(--text-secondary);
  font-size: 13px;
}

.lesson-rhythm-progress {
  align-self: center;
  justify-self: end;
  font-weight: 700;
  color: var(--tint-ink);
}

@media (max-width: 900px) {
  .workbench-section-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .lesson-rhythm-item {
    grid-template-columns: 1fr;
  }

  .lesson-rhythm-progress {
    justify-self: start;
  }
}
</style>
