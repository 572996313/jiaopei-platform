<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <div class="page-toolbar">
      <div class="page-toolbar-filters" v-loading="lookupLoading">
        <el-select v-model="campusId" placeholder="筛选校区" clearable filterable @change="handleFilterChange">
          <el-option
            v-for="item in campuses"
            :key="item.id"
            :label="item.campusName || item.campusCode || `校区 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <el-select v-model="termId" placeholder="筛选学期" clearable filterable @change="handleFilterChange">
          <el-option
            v-for="item in terms"
            :key="item.id"
            :label="item.termName || item.termCode || `学期 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <el-select v-model="classId" placeholder="筛选班级" clearable filterable @change="handleFilterChange">
          <el-option
            v-for="item in classes"
            :key="item.id"
            :label="item.className || item.classCode || `班级 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <span class="datebar-label">统计区间</span>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="截止日期"
          clearable
          @change="handleFilterChange"
        />
        <el-button type="primary" :loading="reportLoading" @click="loadReport">查询</el-button>
      </div>
    </div>

    <article class="panel-card education-panel">
      <div class="education-context-text">
        应排取自各班级<b>启用中的周期排课计划</b>展开（跳过日期不计），没有计划的班级不出现在此表。
        计划只描述「哪个班、每周固定哪几个节次」，不是「每班每科每周应上几节」的课时目标，所以本表只回答计划有没有落地，不回答课时够不够。
        手工加排、补课、调课后的课次落在计划之外，计入「计划外」而不抵扣缺口——缺口需在「排课管理 - 周期计划 - 新建计划」补排才算补上。
        已停课的课次仍算已生成。区间对齐到整周，最长 26 周。
      </div>

      <div class="table-toolbar">
        <el-button :icon="Refresh" circle :loading="reportLoading" @click="loadReport" />
        <el-button @click="goScheduling">回到排课管理</el-button>
      </div>

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
          stripe
          row-key="classId"
          highlight-current-row
          v-loading="reportLoading"
          :height="height"
        >
          <el-table-column label="班级" min-width="180" fixed="left">
            <template #default="{ row }">
              <div class="class-cell">
                <span>{{ row.className || `班级 #${row.classId}` }}</span>
                <span class="class-cell-meta">{{ formatCampus(row.campusId) }} · {{ row.planCount }} 个计划</span>
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
                <span class="week-cell-ratio">{{ row.cells[index].generated }}/{{ row.cells[index].expected }}</span>
                <span class="week-cell-extra">
                  <template v-if="row.cells[index].gaps.length"
                    >缺 {{ row.cells[index].gaps.length }}</template
                  >
                  <template v-else-if="row.cells[index].extra">外 {{ row.cells[index].extra }}</template>
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
        当前筛选范围内没有启用中的周期排课计划，先到「排课管理 - 周期计划」新建计划，再回来看完成度。
      </div>
    </article>

    <el-dialog v-model="gapDetailVisible" :title="gapDetailTitle" width="620px">
      <el-table :data="gapDetailRows" border row-key="key" max-height="360">
        <el-table-column prop="lessonDate" label="上课日期" width="120" />
        <el-table-column label="星期" width="90">
          <template #default="{ row }">{{ weekdayLabel(row.weekday) }}</template>
        </el-table-column>
        <el-table-column prop="slotName" label="节次" min-width="140" />
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button link type="primary" @click="goMakeup(row)">去补排</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="gapDetailRows.length === 0" class="education-empty">这一周没有缺口。</div>
    </el-dialog>
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
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import { toMessage, weekdayLabel } from './components/schedule-utils'

const institutionStore = useInstitutionStore()
const scopeStore = useEducationScopeStore()
const { campusId, termId } = storeToRefs(scopeStore)
const { campuses, terms, classes, institutionId, loadScope } = useEducationScope()
const router = useRouter()

const classId = ref<string | null>(null)
const dateRange = ref<[string, string] | null>(null)

const report = ref<ScheduleCompletenessResult | null>(null)
const reportLoading = ref(false)
const lookupLoading = ref(false)
const pageError = ref('')

const gapDetailVisible = ref(false)
const gapDetailRows = ref<Array<ScheduleCompletenessGap & { key: string }>>([])
const gapDetailTitle = ref('')
const gapDetailClassId = ref('')

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

function weekCellClass(cell?: ScheduleCompletenessCell) {
  if (!cell) {
    return ''
  }
  if (cell.gaps.length > 0) {
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
  gapDetailTitle.value = `${row.className || `班级 #${row.classId}`} · 第${week.index}周（${week.weekStart} ~ ${week.weekEnd}）`
  gapDetailRows.value = cell.gaps.map((gap) => ({
    ...gap,
    key: `${gap.lessonDate}#${gap.timeSlotId}`,
  }))
  gapDetailVisible.value = true
}

/** 带上班级与日期跳到排课管理，由操作员在那里用周期计划新建/补排缺口 */
function goMakeup(gap: ScheduleCompletenessGap) {
  router.push({
    path: '/scheduling',
    query: { classId: gapDetailClassId.value, date: gap.lessonDate },
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
    campuses.value = []
    terms.value = []
    classes.value = []
    classId.value = null
    return
  }
  lookupLoading.value = true
  pageError.value = ''
  try {
    await loadScope(campusId.value, termId.value)
    if (classId.value && !classes.value.some((item) => item.id === classId.value)) {
      classId.value = null
    }
  } catch (error) {
    pageError.value = toMessage(error, '范围数据加载失败')
  } finally {
    lookupLoading.value = false
  }
}

async function handleFilterChange() {
  await refreshScopeData()
  await loadReport()
}

watch(
  () => institutionStore.currentInstitutionId,
  async () => {
    campusId.value = null
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
.datebar-label {
  font-weight: 600;
  color: #14213d;
  white-space: nowrap;
}

.completeness-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  padding: 8px 0 12px;
  color: #334155;
  font-size: 13px;
}

.class-cell {
  display: grid;
  gap: 2px;
}

.class-cell-meta {
  color: #64748b;
  font-size: 12px;
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
  color: #64748b;
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
  color: #334155;
  cursor: pointer;
  text-align: center;
}

.week-cell-ratio {
  font-weight: 600;
}

.week-cell-extra {
  font-size: 12px;
  color: #64748b;
  min-height: 16px;
}

.week-cell--done .week-cell-ratio {
  color: #15803d;
}

.week-cell--extra .week-cell-ratio {
  color: #b45309;
}

.week-cell--missing .week-cell-ratio,
.week-cell--missing .week-cell-extra {
  color: #b91c1c;
}

.week-cell-empty {
  color: #cbd5e1;
}
</style>
