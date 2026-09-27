<template>
  <el-dialog
    :model-value="modelValue"
    title="自动补排（按完成度缺口）"
    width="1080px"
    top="6vh"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @open="handleOpen"
  >
    <el-tabs v-model="activeTab">
      <el-tab-pane label="缺口补排" name="fill">
        <div class="auto-form">
          <span class="auto-form-label">校区</span>
          <el-select
            v-model="formCampusId"
            placeholder="选择校区"
            filterable
            class="auto-form-scope"
            @change="handleCampusChange"
          >
            <el-option
              v-for="item in campuses"
              :key="item.id"
              :label="item.campusName || item.campusCode || `校区 #${item.id}`"
              :value="item.id"
            />
          </el-select>
          <span class="auto-form-label">学期</span>
          <el-select
            v-model="formTermId"
            placeholder="选择学期"
            filterable
            class="auto-form-scope"
          >
            <el-option
              v-for="item in terms"
              :key="item.id"
              :label="item.termName || item.termCode || `学期 #${item.id}`"
              :value="item.id"
            />
          </el-select>
          <span class="auto-form-label">补排窗口</span>
          <el-date-picker
            v-model="dateWindow"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="截止日期"
            :disabled-date="disabledPast"
          />
          <span class="auto-form-label">班级</span>
          <ClassMultiSelect
            v-model="classIds"
            :institution-id="institutionId"
            :campus-id="formCampusId"
            :disabled="!formCampusId"
            placeholder="不选=本校区全部有缺口的班级"
            class="auto-form-class"
          />
          <el-button type="primary" :loading="previewLoading" @click="handlePreview">预览草案</el-button>
          <el-button
            :loading="previewLoading"
            :disabled="!draft"
            @click="resetDraft"
          >
            清空草案
          </el-button>
        </div>

        <div class="auto-form">
          <span class="auto-form-label">择优</span>
          <el-checkbox v-model="concentrateClassDays">尽量排在同一天（少来一趟）</el-checkbox>
          <el-checkbox v-model="preferAdjacentSlot">尽量贴已有课的相邻节次</el-checkbox>
          <span class="auto-form-label">单班单日上限</span>
          <el-input-number
            v-model="maxDailyLessonsPerClass"
            :min="1"
            :max="12"
            placeholder="不限"
            controls-position="right"
          />
          <span class="auto-form-label">备注</span>
          <el-input v-model="remark" placeholder="给批次清单看的说明" maxlength="200" />
        </div>

        <div class="education-context-text">
          引擎只补<b>完成度报表里还缺的节</b>：教师和教室沿用该班启用中的周期排课计划，不换人也不换房；
          先补计划里没展开出的格子，补不上再从本周空位里挑。教师周/日上限在这里是<b>硬约束</b>（手工排课只提示），
          停排日只作兜底——实在没位置才会用上，并在下面标出提示。补排不往今天之前排课。
        </div>

        <div v-if="draft" class="table-toolbar">
          <el-button type="success" :loading="runLoading" :disabled="!draft.placements.length" @click="handleRun">
            执行并生成课次
          </el-button>
          <span class="education-context-text">
            待补 {{ draft.requestedCount }} 节 · 可排 {{ draft.generatedCount }} 节 · 补不上
            {{ draft.unscheduledCount }} 节
            <template v-if="draft.unscheduledCount > draft.failures.length">
              （原因 {{ draft.failures.length }} 条：同一周排不下去会整周停止尝试，一条原因可能对应好几节）
            </template>
            <template v-if="!draft.placements.length">（这一周没有能落的位置，看下面「补不上」的原因）</template>
          </span>
        </div>

        <div v-if="draft" class="panel-title">落位明细</div>
        <el-table
          v-if="draft"
          :data="draft.placements"
          border
          row-key="classId"
          highlight-current-row
          :max-height="TABLE_VIEWPORT_MAX_HEIGHT"
        >
          <el-table-column label="班级" min-width="150" fixed="left">
            <template #default="{ row }">{{ row.className || `班级 #${row.classId}` }}</template>
          </el-table-column>
          <el-table-column label="周次" width="70" align="right">
            <template #default="{ row }">第{{ row.weekNo }}周</template>
          </el-table-column>
          <el-table-column label="上课日期" width="150">
            <template #default="{ row }">{{ row.lessonDate }} {{ weekdayLabel(row.weekday) }}</template>
          </el-table-column>
          <el-table-column label="节次" width="150">
            <template #default="{ row }">
              {{ timeSlotShortName(row.slotName) || row.slotName || `时段 #${row.timeSlotId}` }}
              <span class="auto-time">{{ row.startTime?.slice(0, 5) }}~{{ row.endTime?.slice(0, 5) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="教室" min-width="110">
            <template #default="{ row }">{{ row.classroomName || '—' }}</template>
          </el-table-column>
          <el-table-column label="教师" min-width="120">
            <template #default="{ row }">{{ row.teacherNames || '—' }}</template>
          </el-table-column>
          <el-table-column label="落点" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.source === 'plan_gap' ? 'success' : 'info'" effect="plain">
                {{ row.source === 'plan_gap' ? '计划缺口' : '计划外' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="提示" min-width="150">
            <template #default="{ row }">
              <span v-if="row.notice" class="auto-notice">{{ row.notice }}</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="draft?.failures.length" class="panel-title">补不上的</div>
        <el-table
          v-if="draft?.failures.length"
          :data="draft.failures"
          border
          :max-height="TABLE_VIEWPORT_MAX_HEIGHT"
        >
          <el-table-column label="班级" min-width="150">
            <template #default="{ row }">{{ row.className || `班级 #${row.classId}` }}</template>
          </el-table-column>
          <el-table-column label="周次" width="80" align="right">
            <template #default="{ row }">{{ row.weekNo ? `第${row.weekNo}周` : '整班' }}</template>
          </el-table-column>
          <el-table-column label="原因" min-width="420" prop="reason" />
        </el-table>
        <el-empty v-if="draft && !draft.placements.length && !draft.failures.length" description="窗口内没有缺口" />
      </el-tab-pane>

      <el-tab-pane label="批次清单" name="batches">
        <div class="table-toolbar">
          <el-button :icon="Refresh" circle :loading="batchesLoading" @click="loadBatches" />
          <span class="education-context-text">
            只列当前校区最近 20 个批次（换上方「校区」即换范围）。撤销只删这批里「还没上」的课次，已经点过名或过了日期的不动。
          </span>
        </div>
        <el-table
          :data="batches"
          border
          row-key="id"
          highlight-current-row
          v-loading="batchesLoading"
          :max-height="TABLE_STACK_MAX_HEIGHT"
        >
          <el-table-column label="执行时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="窗口" width="190">
            <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template>
          </el-table-column>
          <el-table-column label="班级" width="100" align="right">
            <template #default="{ row }">{{ row.classIds?.length ? `${row.classIds.length} 个班` : '全部' }}</template>
          </el-table-column>
          <el-table-column label="待补" width="80" align="right" prop="requestedCount" />
          <el-table-column label="生成" width="80" align="right" prop="generatedCount" />
          <el-table-column label="没补上" width="80" align="right" prop="unscheduledCount" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'completed' ? 'success' : 'info'" effect="plain">
                {{ row.status === 'completed' ? '已执行' : '已撤销' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="160" prop="remark" />
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="canManage && row.status === 'completed'"
                link
                type="danger"
                :loading="rollingBack === row.id"
                @click="handleRollback(row)"
              >
                撤销
              </el-button>
              <span v-else>—</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import type { CampusSummary, TermSummary } from '@cornerstone/module-organization/api/organization'
import {
  fetchScheduleAutoBatches,
  previewScheduleAutoBatch,
  rollbackScheduleAutoBatch,
  runScheduleAutoBatch,
  type ScheduleAutoBatchRecord,
  type ScheduleAutoPlanResult,
} from '@cornerstone/module-education/api/education'
import { formatDateTime } from '@cornerstone/core/components/ConfigurableList/renderers'
import { TABLE_STACK_MAX_HEIGHT, TABLE_VIEWPORT_MAX_HEIGHT } from '@cornerstone/core/utils/table-view'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { timeSlotShortName, toMessage, weekdayLabel } from './schedule-utils'
import type { ClassScopeValue } from '@cornerstone/module-education/composables/useClassOptions'
import ClassMultiSelect from './ClassMultiSelect.vue'

const props = defineProps<{
  modelValue: boolean
  /** 小号机构 id（种子数据里的 1）不到 16 位，大 ID 转字符串那步不会命中，运行期可能是数字 */
  institutionId: ClassScopeValue
  campuses: CampusSummary[]
  terms: TermSummary[]
  /** 范围栏筛到的校区/学期只当**初值**：页面上那是可选筛项，弹窗的必填上下文由本表单自己承担 */
  initialCampusId?: string | null
  initialTermId?: string | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'changed'): void
}>()

const auth = useAuthStore()
const canManage = computed(() => auth.hasPermission('education:schedule:write'))

const activeTab = ref('fill')
const formCampusId = ref<string | null>(null)
const formTermId = ref<string | null>(null)
const dateWindow = ref<[string, string]>(defaultWindow())
const classIds = ref<string[]>([])
const concentrateClassDays = ref(true)
const preferAdjacentSlot = ref(true)
const maxDailyLessonsPerClass = ref<number | undefined>(undefined)
const remark = ref('')

const draft = ref<ScheduleAutoPlanResult | null>(null)
const previewLoading = ref(false)
const runLoading = ref(false)

const batches = ref<ScheduleAutoBatchRecord[]>([])
const batchesLoading = ref(false)
const rollingBack = ref<string | null>(null)

/** 引擎不接受早于今天的起始日，默认给"今天起四个整周"，最长 26 周由后端拦。 */
function defaultWindow(): [string, string] {
  const start = new Date()
  const end = new Date()
  end.setDate(start.getDate() + 27)
  return [isoDate(start), isoDate(end)]
}

function isoDate(day: Date) {
  const month = `${day.getMonth() + 1}`.padStart(2, '0')
  const date = `${day.getDate()}`.padStart(2, '0')
  return `${day.getFullYear()}-${month}-${date}`
}

function disabledPast(day: Date) {
  return day < new Date(new Date().toDateString())
}

function handleOpen() {
  activeTab.value = 'fill'
  // 只有一个候选就直接定上，多数机构就一个校区、同时在读的学期就一个，不该逼操作员再点一次
  formCampusId.value = props.initialCampusId || singleId(props.campuses)
  formTermId.value = props.initialTermId || singleId(props.terms)
  classIds.value = []
  resetDraft()
  void loadBatches()
}

function singleId(items: Array<{ id: string }>) {
  return items.length === 1 ? items[0].id : null
}

/** 换校区后旧选择里的外班会被服务端拒成"不在所选校区"，直接清掉重选（草案由下面的 watch 作废）。 */
function handleCampusChange() {
  classIds.value = []
  void loadBatches()
}

function resetDraft() {
  draft.value = null
}

/**
 * 改动任何进过引擎的入参就作废草案：执行会按**当下**的表单重算一遍，留着旧草案只会让
 * 「按草案生成 N 节」的确认框说的是上一轮的范围。备注不参与落位，所以不在这里。
 */
watch(
  [
    formCampusId,
    formTermId,
    dateWindow,
    classIds,
    concentrateClassDays,
    preferAdjacentSlot,
    maxDailyLessonsPerClass,
  ],
  resetDraft
)

function payload() {
  if (!props.institutionId) {
    ElMessage.warning('当前账号没有可用机构，先到右上角切换机构')
    return null
  }
  if (!formCampusId.value || !formTermId.value) {
    ElMessage.warning('先在弹窗里选好校区与学期')
    return null
  }
  return {
    institutionId: String(props.institutionId),
    campusId: formCampusId.value,
    termId: formTermId.value,
    startDate: dateWindow.value?.[0],
    endDate: dateWindow.value?.[1],
    classIds: classIds.value,
    strategy: {
      concentrateClassDays: concentrateClassDays.value,
      preferAdjacentSlot: preferAdjacentSlot.value,
      maxDailyLessonsPerClass: maxDailyLessonsPerClass.value ?? null,
    },
    remark: remark.value || undefined,
  }
}

async function handlePreview() {
  const body = payload()
  if (!body) {
    return
  }
  previewLoading.value = true
  try {
    draft.value = await previewScheduleAutoBatch(body)
    if (!draft.value.placements.length) {
      ElMessage.warning('这一批没有能落位的位置，看下面「补不上的」原因')
    }
  } catch (error) {
    draft.value = null
    ElMessage.error(toMessage(error, '补排草案生成失败'))
  } finally {
    previewLoading.value = false
  }
}

async function handleRun() {
  const body = payload()
  if (!body || !draft.value) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `将按草案生成 ${draft.value.generatedCount} 节课次，生成后可在「批次清单」里整批撤销。`,
      '确认执行补排',
      { type: 'warning' }
    )
  } catch {
    return
  }
  runLoading.value = true
  try {
    const result = await runScheduleAutoBatch(body)
    ElMessage.success(`已生成 ${result.generatedCount} 节课次`)
    draft.value = null
    emit('changed')
    void loadBatches()
  } catch (error) {
    ElMessage.error(toMessage(error, '补排执行失败'))
  } finally {
    runLoading.value = false
  }
}

async function loadBatches() {
  if (!props.institutionId) {
    batches.value = []
    return
  }
  batchesLoading.value = true
  try {
    batches.value = await fetchScheduleAutoBatches({
      institutionId: String(props.institutionId),
      campusId: formCampusId.value,
    })
  } catch (error) {
    batches.value = []
    ElMessage.error(toMessage(error, '批次清单加载失败'))
  } finally {
    batchesLoading.value = false
  }
}

async function handleRollback(batch: ScheduleAutoBatchRecord) {
  try {
    await ElMessageBox.confirm(
      `撤销这批补排生成的 ${batch.generatedCount ?? 0} 节课次里还没上的部分，已点过名的会保留。`,
      '确认撤销批次',
      { type: 'warning' }
    )
  } catch {
    return
  }
  rollingBack.value = batch.id
  try {
    const result = await rollbackScheduleAutoBatch(batch.id)
    ElMessage.success(result.message)
    emit('changed')
    void loadBatches()
  } catch (error) {
    ElMessage.error(toMessage(error, '批次撤销失败'))
  } finally {
    rollingBack.value = null
  }
}
</script>

<style scoped>
.auto-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.auto-form-label {
  color: var(--text-secondary);
}

.auto-form-scope {
  width: 170px;
}

.auto-form-class {
  width: 280px;
}

.panel-title {
  margin: 14px 0 8px;
  font-weight: 600;
}

.auto-time {
  color: var(--text-muted);
  margin-left: 6px;
}

.auto-notice {
  color: var(--soft-warn-ink);
}
</style>
