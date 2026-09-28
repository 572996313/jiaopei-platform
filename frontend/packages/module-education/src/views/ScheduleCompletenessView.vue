<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <!-- 主操作行:按钮靠左、补排说明靠右,整段统计口径收成问号 -->
    <div class="table-toolbar">
      <el-button v-if="canManage" type="primary" @click="autoFillVisible = true"
        >一键补排</el-button
      >
      <el-button :icon="Refresh" circle :loading="reportLoading" @click="loadReport" />
      <el-button @click="goScheduling">回到排课管理</el-button>
      <span class="education-context-text">
        「一键补排」按这里的缺口生成课次：只补还没上的节，教师和教室沿用该班启用中的计划。
      </span>
      <NoteHint label="统计口径" :width="520">
        分母来源按行区分：班级主档填了「每周应上节数」的，应排=<b>每周应上节数 × 周数</b
        >（行内标「按课时目标」）；
        没填的<b>仍回落到该班启用中的周期排课计划展开</b>（行内标「按计划展开」，跳过日期不计，没有计划的班级不出现在此表）——
        同一张表里两种口径并存，比数前先看行标签。
        手工加排、补课、调课后的课次按<b>日期与节次</b>比对：正好落在应排空格子上的，就算把那个缺口补上了；
        落在之外的（改期调课、额外加课、补课另排的一格）计入「计划外」，不抵扣缺口。
        已停课、已结课的课次仍算已生成；它们会让出排课占用，所以同一格可能被再排一节课进去——
        完成度按格子只认一格，<b>重复排课要到「执行与课表」看撞课标记</b>。区间对齐到整周，最长 26
        周。 行标签下的「已上课
        N/总」是<b>整班账</b>：日期已过、没停课的课次全算，计划外加的与改期补的也消耗总课时，
        所以它会<b>大于</b>本区间格子里的「已完成」，两个数不是一回事。
        预计结课日按<b>近四周</b>的节奏推，只到"几周以后"；比计划的结束日晚会用红字提示，
        近四周没排过课则推算不出，不显示日期。
      </NoteHint>
    </div>

    <FilterBar :loading="reportLoading" @query="applyFilters" @reset="resetFilters">
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
      <span class="filter-bar-group">
        <span class="filter-bar-group-label">统计区间</span>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="截止日期"
          clearable
        />
      </span>
    </FilterBar>

    <article class="panel-card education-panel">
      <div v-if="report" class="completeness-summary">
        <span>班级 {{ report.summary.classCount }}</span>
        <span>应排 {{ report.summary.expected }}</span>
        <span>已生成 {{ report.summary.generated }}（{{ report.summary.generatedPercent }}%）</span>
        <span>已完成 {{ report.summary.completed }}（{{ report.summary.completedPercent }}%）</span>
        <span>缺口 {{ report.summary.missing }}</span>
        <span>计划外 {{ report.summary.extra }}</span>
      </div>

      <TableFillBox v-slot="{ height }">
        <el-table
          :data="report?.classes ?? []"
          border
          row-key="classId"
          highlight-current-row
          v-loading="reportLoading"
          :height="height"
        >
          <el-table-column label="班级" min-width="230" fixed="left">
            <template #default="{ row }">
              <div class="class-cell">
                <span>{{ row.className || `班级 #${row.classId}` }}</span>
                <span class="class-cell-meta"
                  >{{ formatCampus(row.campusId) }} · {{ row.planCount }} 个计划 ·
                  {{ denominatorSourceLabel(row) }}</span
                >
                <!-- 整班课时进度只在设了总课时的班级出现；没设的班级不占行高 -->
                <span
                  v-if="lessonProgress(row).text"
                  class="class-cell-meta"
                  :class="{ 'class-cell-meta--late': lessonProgress(row).late }"
                  >{{ lessonProgress(row).text }}</span
                >
              </div>
            </template>
          </el-table-column>
          <el-table-column label="合计" width="150" fixed="left">
            <template #default="{ row }">
              <div class="summary-cell">
                <span>{{ row.summary.generated }}/{{ row.summary.expected }}</span>
                <el-tag v-if="row.summary.missing" size="small" type="danger" effect="plain">
                  缺 {{ row.summary.missing }}
                </el-tag>
                <el-tag v-if="row.summary.extra" size="small" type="warning" effect="plain">
                  外 {{ row.summary.extra }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            v-for="(week, index) in report?.weeks ?? []"
            :key="week.index"
            :label="`第${week.index}周`"
            width="118"
            align="center"
          >
            <template #header>
              <div class="week-head">
                <span>第{{ week.index }}周</span>
                <span class="week-head-date">{{ week.weekStart.slice(5) }}</span>
              </div>
            </template>
            <template #default="{ row }">
              <button
                v-if="row.cells[index]?.covered"
                class="week-cell"
                :class="weekCellClass(row.cells[index])"
                @click="openGapDetail(row, week, row.cells[index])"
              >
                <span class="week-cell-ratio"
                  >{{ row.cells[index].generated }}/{{ row.cells[index].expected }}</span
                >
                <span class="week-cell-extra">
                  <template v-if="row.cells[index].missing"
                    >缺 {{ row.cells[index].missing }}</template
                  >
                  <template v-else-if="row.cells[index].extra"
                    >外 {{ row.cells[index].extra }}</template
                  >
                  <template v-else-if="row.cells[index].completed === row.cells[index].expected"
                    >已上完</template
                  >
                </span>
              </button>
              <span v-else class="week-cell-empty">—</span>
            </template>
          </el-table-column>
        </el-table>
      </TableFillBox>

      <div v-if="!reportLoading && (report?.classes.length ?? 0) === 0" class="education-empty">
        当前筛选范围内没有启用中的周期排课计划，先到「排课管理 -
        周期计划」新建计划，再回来看完成度。
      </div>
    </article>

    <el-dialog v-model="gapDetailVisible" :title="gapDetailTitle" width="620px">
      <el-table :data="gapDetailRows" border row-key="key" max-height="360">
        <el-table-column prop="lessonDate" label="上课日期" width="120" />
        <el-table-column label="星期" width="90">
          <template #default="{ row }">{{ weekdayLabel(row.weekday) }}</template>
        </el-table-column>
        <el-table-column prop="slotName" label="节次" min-width="140" />
        <el-table-column v-if="canManage" label="操作" width="110">
          <template #default="{ row }">
            <el-button link type="primary" @click="goMakeup(row)">去补排</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="gapTargetOnly > 0" class="education-context-text">
        还有
        {{ gapTargetOnly }} 节没排上，但这一周的计划格子里没有对应空位（班级按每周课时目标统计）。
        挑这一周的任意空闲时段加排即可。
      </div>
      <div v-if="gapDetailRows.length === 0" class="education-empty">
        {{ gapTargetOnly > 0 ? '这一周没有计划内的空格子。' : '这一周没有缺口。' }}
      </div>
    </el-dialog>

    <ScheduleAutoFillDialog
      v-model="autoFillVisible"
      :institution-id="institutionId"
      :campuses="campuses"
      :terms="terms"
      :initial-campus-id="campusId"
      :initial-term-id="termId"
      @changed="loadReport"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import {
  fetchScheduleCompleteness,
  type ScheduleCompletenessCell,
  type ScheduleCompletenessGap,
  type ScheduleCompletenessResult,
  type ScheduleCompletenessClassRow,
  type ScheduleCompletenessWeekColumn,
} from '@cornerstone/module-education/api/education'
import { useEducationScope } from '@cornerstone/module-education/composables/useEducationScope'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import FilterBar from '@cornerstone/core/components/FilterBar.vue'
import NoteHint from '@cornerstone/core/components/NoteHint.vue'
import ScheduleAutoFillDialog from './components/ScheduleAutoFillDialog.vue'
import ClassSelect from './components/ClassSelect.vue'
import { toMessage, weekdayLabel } from './components/schedule-utils'

const institutionStore = useInstitutionStore()
const auth = useAuthStore()
const scopeStore = useEducationScopeStore()
const { termId } = storeToRefs(scopeStore)
/** 校区范围在横幅那颗芯片上选：null=全部校区，铺矩阵时不按校区收窄 */
const { campuses, campusId, terms, institutionId, loadScope } = useEducationScope()
const router = useRouter()

/** 「去补排」是写流程入口，按排课维护码收口；报表本身只要 education:read */
const canManage = computed(() => auth.hasPermission('education:schedule:write'))

const classId = ref<string | null>(null)
const dateRange = ref<[string, string] | null>(null)
const autoFillVisible = ref(false)

const report = ref<ScheduleCompletenessResult | null>(null)
const reportLoading = ref(false)
const lookupLoading = ref(false)
const pageError = ref('')

const gapDetailVisible = ref(false)
const gapDetailRows = ref<Array<ScheduleCompletenessGap & { key: string }>>([])
/** 按课时目标统计时，缺口可能比计划空格子多：多出来的那些没有具体日期节次，只能提示数量 */
const gapTargetOnly = ref(0)
const gapDetailTitle = ref('')
const gapDetailClassId = ref('')
// 「去补排」要把缺口所在的班级/校区/学期一起带过去：排课管理的新增弹窗按 scope 取校区与学期，
// 跳转过来时那两个选择器是空的，不带就会串行报「缺少校区 → 缺少学期」
const gapDetailCampusId = ref('')
const gapDetailTermId = ref('')

const campusMap = computed(
  () =>
    new Map(
      campuses.value.map((item) => [
        item.id,
        item.campusName || item.campusCode || `校区 #${item.id}`,
      ])
    )
)

function formatCampus(id?: string | null) {
  if (!id) {
    return '全校区'
  }
  return campusMap.value.get(id) || `校区 #${id}`
}

/** 分母来源回显：weekly_target=班级课时目标，其余（含后端未带字段的存量响应）按计划展开 */
function denominatorSourceLabel(row: ScheduleCompletenessClassRow) {
  if (row.denominatorSource === 'weekly_target') {
    return `按课时目标 ${row.targetLessons ?? ''} 节/周`
  }
  return '按计划展开'
}

/**
 * 整班课时进度。没设总课时的班级返回空文案（不显示这一行），免得满屏「—」。
 *
 * <p>分子是「日期已过且没停课」的全部课次，比格子里的「已完成」宽：计划外加的课、
 * 改期补出来的课同样消耗总课时。所以这里单独说"已上课"，不与格子口径混用。</p>
 */
function lessonProgress(row: ScheduleCompletenessClassRow) {
  if (!row.totalLessons) {
    return { text: '', late: false }
  }
  const held = row.heldLessons ?? 0
  const text = `已上课 ${held}/${row.totalLessons}`
  if (held >= row.totalLessons) {
    return { text: `${text} · 已上满`, late: false }
  }
  if (!row.estimatedFinishDate) {
    return { text: `${text} · 近四周没上课，算不出结课日`, late: false }
  }
  const late = Boolean(row.planEndDate && row.estimatedFinishDate > row.planEndDate)
  return {
    text: late
      ? `${text} · 约 ${row.estimatedFinishDate} 上完，晚于计划`
      : `${text} · 约 ${row.estimatedFinishDate} 上完`,
    late,
  }
}

function weekCellClass(cell?: ScheduleCompletenessCell) {
  if (!cell) {
    return ''
  }
  if (cell.missing > 0) {
    return 'week-cell--missing'
  }
  if (cell.extra > 0) {
    return 'week-cell--extra'
  }
  return 'week-cell--done'
}

function openGapDetail(
  row: ScheduleCompletenessClassRow,
  week: ScheduleCompletenessWeekColumn,
  cell?: ScheduleCompletenessCell
) {
  if (!cell) {
    return
  }
  gapDetailClassId.value = row.classId
  gapDetailCampusId.value = row.campusId || ''
  gapDetailTermId.value = row.termId || ''
  gapDetailTitle.value = `${row.className || `班级 #${row.classId}`} · 第${week.index}周（${week.weekStart} ~ ${week.weekEnd}）`
  gapDetailRows.value = cell.gaps.map((gap) => ({
    ...gap,
    key: `${gap.lessonDate}#${gap.timeSlotId}`,
  }))
  gapTargetOnly.value = Math.max(0, cell.missing - gapDetailRows.value.length)
  gapDetailVisible.value = true
}

/** 带上缺口所在的班级/校区/学期/日期/节次，直达排课管理的新增弹窗 */
function goMakeup(gap: ScheduleCompletenessGap) {
  router.push({
    path: '/scheduling',
    query: {
      create: '1',
      classId: gapDetailClassId.value,
      campusId: gapDetailCampusId.value,
      termId: gapDetailTermId.value,
      date: gap.lessonDate,
      timeSlotId: String(gap.timeSlotId),
    },
  })
}

function goScheduling() {
  router.push('/scheduling')
}

async function loadReport() {
  if (!institutionId.value) {
    report.value = null
    return
  }
  reportLoading.value = true
  pageError.value = ''
  try {
    report.value = await fetchScheduleCompleteness({
      institutionId: institutionId.value,
      campusId: campusId.value,
      termId: termId.value,
      classId: classId.value,
      dateFrom: dateRange.value?.[0] ?? null,
      dateTo: dateRange.value?.[1] ?? null,
    })
  } catch (error) {
    report.value = null
    pageError.value = toMessage(error, '排课完成度加载失败')
  } finally {
    reportLoading.value = false
  }
}

async function refreshScopeData() {
  if (!institutionId.value) {
    terms.value = []
    classId.value = null
    return
  }
  lookupLoading.value = true
  pageError.value = ''
  try {
    await loadScope()
  } catch (error) {
    pageError.value = toMessage(error, '范围数据加载失败')
  } finally {
    lookupLoading.value = false
  }
}

async function applyFilters() {
  await refreshScopeData()
  await loadReport()
}

/** 重置 = 取消学期/班级/统计区间筛选并生效（校区是全站范围，不由本页重置） */
async function resetFilters() {
  termId.value = null
  classId.value = null
  dateRange.value = null
  await applyFilters()
}

watch(
  () => institutionStore.currentInstitutionId,
  async () => {
    // 换机构时 store 的 reset() 已清空校区范围，本页只清自己选的筛选
    termId.value = null
    classId.value = null
    await refreshScopeData()
    await loadReport()
  }
)

onMounted(async () => {
  lookupLoading.value = true
  try {
    await institutionStore.loadInstitutions()
    if (institutionId.value) {
      await refreshScopeData()
    }
  } catch (error) {
    pageError.value = toMessage(error, '页面初始化失败')
  } finally {
    lookupLoading.value = false
  }
  await loadReport()
})
</script>

<style scoped>
.completeness-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  padding: 8px 0 12px;
  color: var(--text-strong);
  font-size: 13px;
}

.class-cell {
  display: grid;
  gap: 2px;
}

.class-cell-meta {
  color: var(--text-secondary);
  font-size: 12px;
}

/* 按现在的节奏赶不完计划：只标红这一句，格子里的缺口数已经够醒目了 */
.class-cell-meta--late {
  color: var(--soft-danger-ink);
}

.summary-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.week-head {
  display: grid;
  gap: 2px;
  line-height: 1.3;
}

.week-head-date {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 400;
}

.week-cell {
  display: grid;
  gap: 2px;
  width: 100%;
  padding: 4px 6px;
  border: none;
  background: transparent;
  font: inherit;
  color: var(--text-strong);
  cursor: pointer;
  text-align: center;
}

.week-cell-ratio {
  font-weight: 600;
}

.week-cell-extra {
  font-size: 12px;
  color: var(--text-secondary);
  min-height: 16px;
}

.week-cell--done .week-cell-ratio {
  color: var(--soft-success-ink);
}

.week-cell--extra .week-cell-ratio {
  color: var(--soft-warn-ink);
}

.week-cell--missing .week-cell-ratio,
.week-cell--missing .week-cell-extra {
  color: var(--soft-danger-ink);
}

.week-cell-empty {
  color: var(--text-faint);
}
</style>
