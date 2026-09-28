<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <!-- 主操作行:按钮靠左、当前范围回显靠右,筛选行与排课日期导航在它下面 -->
    <div class="table-toolbar">
      <el-button v-if="canManage" type="primary" @click="openCreateDialog">新增排课</el-button>
      <el-button @click="planListVisible = true">周期计划</el-button>
      <el-button @click="ruleListVisible = true">排课规则</el-button>
      <el-button
        :icon="Refresh"
        circle
        :loading="lookupLoading || lessonsLoading"
        @click="refreshLessons"
      />
      <el-button @click="goTimetable">查看课表</el-button>
      <div class="education-context-text">
        校区：{{ formatCampus(campusId) }}，班级：{{ formatClass(classId) }}，日期：{{
          lessonDate || '全部'
        }}。点名、结课、停课、补课等执行操作请前往「执行与课表」。
      </div>
    </div>

    <!-- 统一筛选栏:校区/学期/班级点「查询」才生效;排课日期是看哪一天的导航,保持选中即切换 -->
    <div class="page-toolbar">
      <FilterBar :loading="lessonsLoading" @query="applyFilters" @reset="resetFilters">
        <el-select v-model="campusId" placeholder="筛选校区" clearable filterable>
          <el-option
            v-for="item in campuses"
            :key="item.id"
            :label="item.campusName || item.campusCode || `校区 #${item.id}`"
            :value="item.id"
          />
        </el-select>
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
        <span class="datebar-label">排课日期</span>
        <el-date-picker
          v-model="lessonDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="全部日期"
          clearable
          @change="refreshLessons"
        />
        <span class="datebar-hint">清空日期可查看全部排课</span>
      </div>
    </div>

    <article class="panel-card education-panel">
      <TableFillBox v-slot="{ height }">
        <el-table
          :data="lessons"
          border
          row-key="id"
          highlight-current-row
          v-loading="lessonsLoading"
          :height="height"
          :cell-class-name="lessonSort.cellClass"
          @sort-change="lessonSort.onSortChange"
        >
          <el-table-column prop="lessonDate" label="上课日期" width="120" sortable="custom" />
          <el-table-column label="星期" width="100" prop="lessonWeekday" sortable="custom">
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
          <el-table-column label="时段" min-width="170" prop="startTime" sortable="custom">
            <template #default="{ row }">
              {{ formatTimeSlot(row.timeSlotId, row.startTime, row.endTime) }}
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
            sortable="custom"
          />
          <el-table-column label="状态" width="120" prop="lessonStatus" sortable="custom">
            <template #default="{ row }">
              <div class="lesson-status-cell">
                <el-tag :type="lessonStatusTagType(row.lessonStatus)">
                  {{ lessonStatusLabel(row.lessonStatus) }}
                </el-tag>
                <el-tag v-if="row.pendingMakeup === true" size="small" type="danger" effect="plain"
                  >待补课</el-tag
                >
                <!-- 禁排说明只有服务端判得准（规则可晚于课次建立，且按课次自己的班级与教师收窄），这里只转述 -->
                <el-tag
                  v-if="(row.ruleConflictMessages || []).length > 0"
                  size="small"
                  type="danger"
                  effect="plain"
                  :title="(row.ruleConflictMessages || []).join('；')"
                  >禁排</el-tag
                >
              </div>
            </template>
          </el-table-column>
          <el-table-column
            prop="remark"
            label="备注"
            min-width="180"
            show-overflow-tooltip
            sortable="custom"
          />
          <el-table-column label="停补关联" min-width="200">
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
          <el-table-column v-if="canManage" label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <div class="system-operation-group">
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
      </TableFillBox>

      <div v-if="!lessonsLoading && lessons.length === 0" class="education-empty">
        当前筛选范围下暂无排课记录。
      </div>

      <el-pagination
        v-model:current-page="lessonPage"
        v-model:page-size="lessonPageSize"
        class="panel-pagination"
        :total="lessonTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="refreshLessons"
        @size-change="handleLessonSizeChange"
      />
    </article>

    <ScheduleLessonFormDialog
      v-model="dialogVisible"
      :mode="dialogMode"
      :lesson="dialogLesson"
      :prefill="dialogPrefill"
      :institution-id="String(institutionId || '')"
      :scope-campus-id="campusId"
      :scope-term-id="termId"
      :scope-class-id="classId"
      :institutions="institutions"
      :campuses="campuses"
      :terms="terms"
      :time-slots="timeSlots"
      :default-date="lessonDate"
      :format-class="formatClass"
      @saved="handleLessonSaved"
    />

    <SchedulePlanWizardDialog
      v-model="planWizardVisible"
      :institution-id="String(institutionId || '')"
      :edit-plan="planEditing"
      :terms="terms"
      :teachers="teachers"
      :classrooms="classrooms"
      :time-slots="timeSlots"
      :scope-campus-id="campusId"
      :scope-class-id="classId"
      :lookup-loading="lookupLoading"
      :format-campus="formatCampus"
      @saved="handlePlanSaved"
    />

    <SchedulePlanListDialog
      ref="planListRef"
      v-model="planListVisible"
      :institution-id="String(institutionId || '')"
      :teachers="teachers"
      :classrooms="classrooms"
      :time-slots="timeSlots"
      :scope-campus-id="campusId"
      :scope-class-id="classId"
      :format-class="formatClass"
      @create="openPlanWizard(null)"
      @edit="openPlanWizard"
      @changed="refreshLessons"
    />

    <ScheduleRuleListDialog
      ref="ruleListRef"
      v-model="ruleListVisible"
      :institution-id="String(institutionId || '')"
      :teachers="teachers"
      :classrooms="classrooms"
      :campuses="campuses"
      :time-slots="timeSlots"
      :scope-campus-id="campusId"
      @create="openRuleEditor(null)"
      @edit="openRuleEditor"
      @changed="refreshLessons"
    />

    <ScheduleRuleEditorDialog
      v-model="ruleEditorVisible"
      :institution-id="String(institutionId || '')"
      :edit-rule="ruleEditing"
      :teachers="teachers"
      :classrooms="classrooms"
      :time-slots="timeSlots"
      :scope-campus-id="campusId"
      :lookup-loading="lookupLoading"
      :format-campus="formatCampus"
      @saved="handleRuleSaved"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import {
  deleteScheduleLesson,
  fetchScheduleLesson,
  fetchScheduleLessonPage,
  fetchTeachers,
  type ScheduleLessonRecord,
  type SchedulePlanRecord,
  type ScheduleRuleRecord,
  type TeacherSummary,
} from '@cornerstone/module-education/api/education'
import {
  fetchClassrooms,
  fetchTimeSlots,
  type ClassroomSummary,
  type TimeSlotSummary,
} from '@cornerstone/module-organization/api/organization'
import { useEducationScope } from '@cornerstone/module-education/composables/useEducationScope'
import {
  classLabelOf,
  ensureClassLabels,
} from '@cornerstone/module-education/composables/classLabels'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import { useTableSort } from '@cornerstone/core/composables/use-table-sort'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import FilterBar from '@cornerstone/core/components/FilterBar.vue'
import ScheduleLessonFormDialog from './components/ScheduleLessonFormDialog.vue'
import SchedulePlanWizardDialog from './components/SchedulePlanWizardDialog.vue'
import SchedulePlanListDialog from './components/SchedulePlanListDialog.vue'
import ScheduleRuleListDialog from './components/ScheduleRuleListDialog.vue'
import ScheduleRuleEditorDialog from './components/ScheduleRuleEditorDialog.vue'
import ClassSelect from './components/ClassSelect.vue'
import {
  compactTimeRange,
  formatTeacherIdsAs,
  lessonStatusLabel,
  lessonStatusTagType,
  sortedLessons,
  timeSlotOptionLabel,
  toMessage,
  weekdayLabel,
} from './components/schedule-utils'

const institutionStore = useInstitutionStore()
const auth = useAuthStore()
const scopeStore = useEducationScopeStore()
const { campusId, termId } = storeToRefs(scopeStore)
const { institutions, institutionId, campuses, terms, loadScope } = useEducationScope()
const route = useRoute()
const router = useRouter()

/** 写操作按钮按排课维护码收口（菜单按钮节点 112「排课维护」），后端仍用 @RequiresPermission 硬拦 */
const canManage = computed(() => auth.hasPermission('education:schedule:write'))

const teachers = ref<TeacherSummary[]>([])
const classrooms = ref<ClassroomSummary[]>([])
const timeSlots = ref<TimeSlotSummary[]>([])

const classId = ref<string | null>(null)
const lessonDate = ref<string | null>(null)

const lessons = ref<ScheduleLessonRecord[]>([])
const lessonPage = ref(1)
const lessonPageSize = ref(10)
const lessonTotal = ref(0)

/** 表头排序(服务端):无序时回到后端的日期+开始时间默认排序 */
const lessonSort = useTableSort({
  onSorted: () => {
    lessonPage.value = 1
    void refreshLessons()
  },
})

const pageError = ref('')
const lookupLoading = ref(false)
const lessonsLoading = ref(false)
const deletingLessonId = ref<string | null>(null)

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const dialogLesson = ref<ScheduleLessonRecord | null>(null)
const dialogPrefill = ref<Record<string, string> | null>(null)

const planWizardVisible = ref(false)
const planListVisible = ref(false)
const planEditing = ref<SchedulePlanRecord | null>(null)
const planListRef = ref<InstanceType<typeof SchedulePlanListDialog> | null>(null)

const ruleListVisible = ref(false)
const ruleEditorVisible = ref(false)
const ruleEditing = ref<ScheduleRuleRecord | null>(null)
const ruleListRef = ref<InstanceType<typeof ScheduleRuleListDialog> | null>(null)

// 补课课次的停课原课详情缓存，用于「停补关联」列展示与跳转
const relatedLessonCache = ref<Record<string, ScheduleLessonRecord>>({})

const campusMap = computed(() => {
  return new Map(
    campuses.value.map((item) => [
      item.id,
      item.campusName || item.campusCode || `校区 #${item.id}`,
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

function formatCampus(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return campusMap.value.get(id) || `校区 #${id}`
}

function formatClass(id?: string | null) {
  return classLabelOf(id)
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

function lessonRelationSummary(lesson: ScheduleLessonRecord) {
  if (lesson.lessonStatus === 'cancelled') {
    if (lesson.pendingMakeup === true) {
      return '未关联补课'
    }
    return lesson.pendingMakeup === false ? '已安排补课' : '—'
  }
  if (lesson.lessonStatus === 'makeup' && lesson.changeType === 'cancelled_makeup') {
    const related = lesson.relatedLessonId
      ? relatedLessonCache.value[String(lesson.relatedLessonId)]
      : null
    if (!related) {
      return '补课原课待加载'
    }
    return `补 ${related.lessonDate || '未设置日期'} ${formatClass(related.classId)}`
  }
  return '—'
}

function relationJumpLabel(lesson: ScheduleLessonRecord) {
  if (
    lesson.lessonStatus === 'makeup' &&
    lesson.changeType === 'cancelled_makeup' &&
    lesson.relatedLessonId &&
    relatedLessonCache.value[String(lesson.relatedLessonId)]
  ) {
    return '查看原停课'
  }
  return ''
}

function jumpToRelatedLesson(lesson: ScheduleLessonRecord) {
  const related = lesson.relatedLessonId
    ? relatedLessonCache.value[String(lesson.relatedLessonId)]
    : null
  if (!related) {
    ElMessage.warning('未找到关联课次')
    return
  }
  lessonDate.value = related.lessonDate || null
  if (related.classId) {
    classId.value = related.classId
  }
  lessonPage.value = 1
  void refreshLessons()
}

async function loadRelatedLessons(values: ScheduleLessonRecord[]) {
  const missing = Array.from(
    new Set(
      values
        .filter(
          (lesson) =>
            lesson.lessonStatus === 'makeup' &&
            lesson.changeType === 'cancelled_makeup' &&
            lesson.relatedLessonId &&
            !relatedLessonCache.value[String(lesson.relatedLessonId)]
        )
        .map((lesson) => String(lesson.relatedLessonId))
    )
  )
  if (missing.length === 0) {
    return
  }
  const results = await Promise.allSettled(missing.map((id) => fetchScheduleLesson(id)))
  const next = { ...relatedLessonCache.value }
  results.forEach((result, index) => {
    if (result.status === 'fulfilled') {
      next[missing[index]] = result.value
    }
  })
  relatedLessonCache.value = next
}

async function refreshScopeData() {
  if (!institutionId.value) {
    campuses.value = []
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

    if (campusId.value && !campuses.value.some((item) => item.id === campusId.value)) {
      campusId.value = null
      await loadSupportingOptions()
    }
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

async function loadSupportingOptions() {
  if (!institutionId.value) {
    teachers.value = []
    classrooms.value = []
    timeSlots.value = []
    return
  }

  const [teacherList, classroomList, timeSlotList] = await Promise.all([
    fetchTeachers({ institutionId: institutionId.value, campusId: campusId.value }),
    // 不传校区时后端返回全部教室，保证未选校区时列表仍能显示教室名称
    fetchClassrooms(campusId.value),
    fetchTimeSlots(institutionId.value),
  ])

  teachers.value = teacherList
  classrooms.value = classroomList
  timeSlots.value = timeSlotList
}

async function refreshLessons() {
  if (!institutionId.value) {
    lessons.value = []
    lessonTotal.value = 0
    return
  }

  lessonsLoading.value = true
  pageError.value = ''
  try {
    const result = await fetchScheduleLessonPage({
      institutionId: institutionId.value,
      campusId: campusId.value,
      classId: classId.value,
      lessonDate: lessonDate.value,
      page: lessonPage.value,
      pageSize: lessonPageSize.value,
      ...lessonSort.sortParams(),
    })
    // 表头排序由后端完成;无排序时才在前端补一次日期+时间顺序(接口默认序已如此,此处只是稳定展示)
    lessons.value = lessonSort.sortField.value ? result.records : sortedLessons(result.records)
    lessonTotal.value = result.total
    void loadRelatedLessons(lessons.value)
    // 课次视图只带 classId，班级名要按 id 回查（下拉不再持有全量班级当字典）
    void ensureClassLabels(
      institutionId.value,
      lessons.value.map((item) => item.classId)
    )
  } catch (error) {
    lessons.value = []
    lessonTotal.value = 0
    pageError.value = toMessage(error, '排课列表加载失败')
  } finally {
    lessonsLoading.value = false
  }
}

function handleLessonSizeChange() {
  lessonPage.value = 1
  return refreshLessons()
}

/** 上一次「查询」实际生效的校区:只有校区真变了才清掉已选班级草稿 */
const lastAppliedCampus = ref<string | null>(null)

async function applyFilters() {
  if (campusId.value !== lastAppliedCampus.value) {
    classId.value = null
  }
  lessonPage.value = 1
  await refreshScopeData()
  lastAppliedCampus.value = campusId.value
  await refreshLessons()
}

/** 重置 = 取消校区/学期/班级筛选并生效 */
async function resetFilters() {
  campusId.value = null
  termId.value = null
  classId.value = null
  await applyFilters()
}

function openCreateDialog() {
  dialogMode.value = 'create'
  dialogLesson.value = null
  dialogPrefill.value = null
  dialogVisible.value = true
}

function openEditDialog(lesson: ScheduleLessonRecord) {
  dialogMode.value = 'edit'
  dialogLesson.value = lesson
  dialogPrefill.value = null
  dialogVisible.value = true
}

function handleLessonSaved(message: string) {
  ElMessage.success(message)
  void refreshLessons()
}

function openPlanWizard(plan: SchedulePlanRecord | null) {
  planEditing.value = plan
  planWizardVisible.value = true
}

function handlePlanSaved(message: string) {
  ElMessage.success(message)
  planListRef.value?.reload()
  void refreshLessons()
}

function openRuleEditor(rule: ScheduleRuleRecord | null) {
  ruleEditing.value = rule
  ruleEditorVisible.value = true
}

function handleRuleSaved(message: string) {
  ElMessage.success(message)
  ruleListRef.value?.reload()
}

function goTimetable() {
  router.push({
    path: '/schedule',
    query: {
      ...(lessonDate.value ? { date: lessonDate.value } : {}),
      ...(classId.value ? { classId: classId.value } : {}),
    },
  })
}

async function removeLesson(lesson: ScheduleLessonRecord) {
  try {
    await ElMessageBox.confirm(
      `确认删除 ${lesson.lessonDate || ''} ${formatClass(lesson.classId)} 的排课吗？`,
      '删除排课',
      { type: 'warning' }
    )
  } catch {
    return
  }

  deletingLessonId.value = lesson.id
  try {
    await deleteScheduleLesson(lesson.id)
    ElMessage.success('排课已删除')
    await refreshLessons()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除排课失败'))
  } finally {
    deletingLessonId.value = null
  }
}

/** 执行页跳转带参：create=1 直接打开新增弹窗（周课表空格点击），editId 直接打开编辑 */
async function applyRouteQuery() {
  const query = route.query
  const date = typeof query.date === 'string' && query.date ? query.date : ''
  const queryClassId = typeof query.classId === 'string' && query.classId ? query.classId : ''
  const queryCampusId = typeof query.campusId === 'string' && query.campusId ? query.campusId : ''
  const queryTermId = typeof query.termId === 'string' && query.termId ? query.termId : ''
  if (date) {
    lessonDate.value = date
  }
  // 校区/学期是共享范围，先落范围再刷候选：班级下拉、教室与教师选项都按校区过滤
  let scopeChanged = false
  if (queryCampusId && campusId.value !== queryCampusId) {
    campusId.value = queryCampusId
    scopeChanged = true
  }
  if (queryTermId && termId.value !== queryTermId) {
    termId.value = queryTermId
    scopeChanged = true
  }
  if (scopeChanged) {
    await refreshScopeData()
  }
  if (queryClassId) {
    classId.value = queryClassId
  }
  if (query.create === '1') {
    dialogMode.value = 'create'
    dialogLesson.value = null
    // 校区/学期取刷完候选之后的值：范围不存在时 refreshScopeData 已把它清回 null
    dialogPrefill.value = {
      ...(date ? { lessonDate: date } : {}),
      ...(campusId.value ? { campusId: campusId.value } : {}),
      ...(termId.value ? { termId: termId.value } : {}),
      ...(typeof query.timeSlotId === 'string' && query.timeSlotId
        ? { timeSlotId: query.timeSlotId }
        : {}),
      ...(typeof query.classroomId === 'string' && query.classroomId
        ? { classroomId: query.classroomId }
        : {}),
      ...(queryClassId ? { classId: queryClassId } : {}),
    }
    dialogVisible.value = true
    // create=1 是一次性动作标记：留在地址里会让页签「刷新页面」把弹窗重新弹出来，消费掉即抹掉
    void router.replace({
      path: route.path,
      query: { ...route.query, create: undefined },
    })
    return
  }
  if (typeof query.editId === 'string' && query.editId) {
    const target = lessons.value.find((item) => String(item.id) === query.editId)
    if (target) {
      openEditDialog(target)
    }
  }
}

async function initializePage() {
  lookupLoading.value = true
  pageError.value = ''
  try {
    await institutionStore.loadInstitutions()
    if (institutionId.value) {
      await refreshScopeData()
    }
    await applyRouteQuery()
    await refreshLessons()
    // editId 依赖列表加载完成后才能定位目标行
    if (typeof route.query.editId === 'string' && route.query.editId) {
      await applyRouteQuery()
    }
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
    lessonPage.value = 1
    await refreshScopeData()
    await refreshLessons()
  }
)

// 页面停留时从执行页等入口带参跳转也要生效（初始化时由 initializePage 处理）
watch(
  () => route.query,
  async () => {
    if (route.name !== 'scheduling') {
      return
    }
    lessonPage.value = 1
    await applyRouteQuery()
    await refreshLessons()
    if (typeof route.query.editId === 'string' && route.query.editId) {
      await applyRouteQuery()
    }
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

.lesson-status-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
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
