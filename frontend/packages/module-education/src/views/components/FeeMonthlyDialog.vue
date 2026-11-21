<template>
  <el-dialog
    v-model="visible"
    title="出勤与逐月发生额"
    class="fee-monthly-dialog"
    width="min(1500px, 96vw)"
    top="4vh"
    :close-on-click-modal="false"
    :before-close="beforeClose"
    @open="handleOpen"
  >
    <el-alert
      v-if="formError"
      :title="formError"
      type="error"
      show-icon
      :closable="false"
      class="education-alert"
    />

    <div v-if="ledger" class="monthly-summary">
      <span class="monthly-student">
        {{ ledger.studentName || ledger.studentNo }} · {{ ledger.className }} · {{ ledger.termName }}
      </span>
      <span>合计发生 <b>{{ moneyText(ledger.totalOccurrence) }}</b></span>
      <span>结余 <b>{{ moneyText(ledger.balance) }}</b></span>
      <span v-if="manualCount > 0" class="monthly-manual-count">
        手工覆盖 <b>{{ manualCount }}</b> 项
      </span>
    </div>

    <div class="table-toolbar">
      <el-button
        v-if="canWrite"
        :disabled="busy"
        :loading="syncing"
        @click="syncAttendance"
        >从考勤同步</el-button
      >
      <el-button
        v-if="canWrite"
        :disabled="busy"
        :loading="recalculating"
        @click="recalculate"
        >按当前出勤重算</el-button
      >
      <el-button
        v-if="canWrite && manualCount > 0"
        type="warning"
        plain
        :disabled="busy"
        :loading="revertingAll"
        @click="revertAll"
        >全部恢复自动</el-button
      >
      <el-button :icon="Refresh" circle :disabled="busy" :loading="loading" @click="loadAll" />
      <span class="education-context-text">
        直接改单元格，最后点右下角「保存全部修改」一次提交；「撤销覆盖」只把生效发生额恢复为引擎自动值，月份系数保留。
      </span>
    </div>

    <div class="education-table-head monthly-section-head">
      <span class="panel-title">月度出勤</span>
      <el-tag v-if="dirtyAttendance.length" type="warning" effect="plain" size="small">
        待保存 {{ dirtyAttendance.length }} 项
      </el-tag>
      <span class="education-context-text">出勤天数影响该台账全部按天项目。</span>
    </div>
    <el-table
      :data="attendance"
      border
      row-key="billingMonth"
      v-loading="loading"
      height="clamp(220px, 24vh, 280px)"
      empty-text="还没有出勤记录，点击「从考勤同步」按点名汇总。"
      :row-class-name="attendanceRowClass"
    >
      <el-table-column label="账期" min-width="150">
        <template #default="{ row }">{{ monthText(row.billingMonth) }}</template>
      </el-table-column>
      <el-table-column label="考勤汇总天数" min-width="150" align="right">
        <template #default="{ row }">{{ moneyText(row.autoDays) }}</template>
      </el-table-column>
      <el-table-column label="生效出勤天数" min-width="190">
        <template #default="{ row }">
          <el-input-number
            v-model="attendanceDrafts[row.billingMonth]"
            :min="0"
            :precision="2"
            :step="0.5"
            controls-position="right"
            size="small"
            :disabled="!canWrite || busy"
            style="width: 100%"
          />
        </template>
      </el-table-column>
      <el-table-column label="覆盖状态" min-width="120">
        <template #default="{ row }">
          <el-tag :type="row.manualOverride ? 'warning' : 'info'">
            {{ row.manualOverride ? '已覆盖' : '自动' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="来源" min-width="110">
        <template #default="{ row }">{{ row.source || '-' }}</template>
      </el-table-column>
      <el-table-column v-if="canWrite" label="操作" min-width="130" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.manualOverride"
            link
            type="warning"
            :disabled="busy"
            :loading="revertingKey === `attendance-${row.billingMonth}`"
            @click="revertAttendance(row)"
            >撤销覆盖</el-button
          >
          <span v-else class="monthly-auto-text">自动值</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="education-table-head monthly-section-head monthly-second-head">
      <span class="panel-title">逐月发生额</span>
      <el-tag v-if="dirtyMonthly.length" type="warning" effect="plain" size="small">
        待保存 {{ dirtyMonthly.length }} 项
      </el-tag>
      <span class="education-context-text">发生额覆盖只动当前项目当前月，重算不会改手工覆盖值。</span>
    </div>
    <el-table
      :data="monthly"
      border
      row-key="id"
      v-loading="loading"
      height="clamp(320px, calc(100vh - 720px), 520px)"
      empty-text="还没有逐月发生额，点击「按当前出勤重算」生成。"
      :row-class-name="monthlyRowClass"
    >
      <el-table-column label="账期" min-width="140">
        <template #default="{ row }">{{ monthText(row.billingMonth) }}</template>
      </el-table-column>
      <el-table-column label="收费项目" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">{{ row.itemName || `项目 #${row.feeItemId}` }}</template>
      </el-table-column>
      <el-table-column label="计费方式" min-width="110">
        <template #default="{ row }">{{ billingModeLabel(row.billingMode) }}</template>
      </el-table-column>
      <el-table-column label="月份系数" min-width="150">
        <template #default="{ row }">
          <el-input-number
            v-model="monthlyDrafts[row.id].monthFactor"
            :min="0"
            :max="1"
            :precision="2"
            :step="0.5"
            controls-position="right"
            size="small"
            :disabled="!canWrite || busy || row.billingMode !== 'monthly'"
            style="width: 100%"
          />
        </template>
      </el-table-column>
      <el-table-column label="引擎自动金额" min-width="160" align="right">
        <template #default="{ row }">{{ moneyText(row.autoAmount) }}</template>
      </el-table-column>
      <el-table-column label="生效发生额" min-width="180">
        <template #default="{ row }">
          <el-input-number
            v-model="monthlyDrafts[row.id].occurrenceAmount"
            :min="0"
            :precision="2"
            :step="100"
            controls-position="right"
            size="small"
            :disabled="!canWrite || busy"
            style="width: 100%"
          />
        </template>
      </el-table-column>
      <el-table-column label="覆盖状态" min-width="120">
        <template #default="{ row }">
          <el-tag :type="row.manualOverride ? 'warning' : 'info'">
            {{ row.manualOverride ? '已覆盖' : '自动' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canWrite" label="操作" min-width="130" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.manualOverride"
            link
            type="warning"
            :disabled="busy"
            :loading="revertingKey === `monthly-${row.id}`"
            @click="revertMonthly(row)"
            >撤销覆盖</el-button
          >
          <span v-else class="monthly-auto-text">自动值</span>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <div class="fee-dialog-footer">
        <span class="fee-footer-hint">
          <template v-if="dirtyCount > 0">共 {{ dirtyCount }} 项修改未保存</template>
          <template v-else-if="manualCount > 0">
            当前 {{ manualCount }} 项为手工覆盖，保存后仍保留覆盖状态
          </template>
          <template v-else>改完单元格后，点右侧按钮统一保存</template>
        </span>
        <div class="fee-footer-actions">
          <el-button :disabled="savingAll" @click="requestClose">关闭</el-button>
          <el-button
            type="primary"
            :loading="savingAll"
            :disabled="dirtyCount === 0 || busy"
            @click="saveAll"
          >
            保存全部修改{{ dirtyCount ? `（${dirtyCount}）` : '' }}
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { computed, reactive, ref } from 'vue'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import {
  billingModeLabel,
  fetchFeeAttendance,
  fetchFeeMonthly,
  moneyText,
  monthText,
  overrideFeeAttendance,
  overrideFeeMonthly,
  recalculateFees,
  revertFeeAttendance,
  revertFeeMonthly,
  syncFeeAttendance,
  type FeeAttendanceView,
  type FeeLedgerDetailView,
  type FeeMonthlyView,
} from '@cornerstone/module-education/api/fee'

const props = defineProps<{
  modelValue: boolean
  ledger: FeeLedgerDetailView | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: []
}>()

const auth = useAuthStore()
const canWrite = computed(() => auth.hasPermission('education:fee:write'))

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const attendance = ref<FeeAttendanceView[]>([])
const monthly = ref<FeeMonthlyView[]>([])
const attendanceDrafts = reactive<Record<string, number | null>>({})
const monthlyDrafts = reactive<
  Record<string, { occurrenceAmount: number | null; monthFactor: number | null }>
>({})
const loading = ref(false)
const syncing = ref(false)
const recalculating = ref(false)
const savingAll = ref(false)
const revertingKey = ref('')
const revertingAll = ref(false)
const formError = ref('')

const busy = computed(
  () =>
    loading.value ||
    syncing.value ||
    recalculating.value ||
    savingAll.value ||
    revertingAll.value ||
    revertingKey.value !== ''
)

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function sameNumber(left?: number | null, right?: number | null) {
  if ((left === null || left === undefined) && (right === null || right === undefined)) {
    return true
  }
  if (left === null || left === undefined || right === null || right === undefined) {
    return false
  }
  return Math.abs(Number(left) - Number(right)) < 0.0001
}

const dirtyAttendance = computed(() =>
  attendance.value.filter(
    (row) => !sameNumber(attendanceDrafts[row.billingMonth], row.attendanceDays)
  )
)

const dirtyMonthly = computed(() =>
  monthly.value.filter((row) => {
    const draft = monthlyDrafts[row.id]
    if (!draft) {
      return false
    }
    if (!sameNumber(draft.occurrenceAmount, row.occurrenceAmount)) {
      return true
    }
    return row.billingMode === 'monthly' && !sameNumber(draft.monthFactor, row.monthFactor)
  })
)

const dirtyCount = computed(() => dirtyAttendance.value.length + dirtyMonthly.value.length)

const manualCount = computed(
  () =>
    attendance.value.filter((row) => row.manualOverride).length +
    monthly.value.filter((row) => row.manualOverride).length
)

function attendanceRowClass({ row }: { row: FeeAttendanceView }) {
  return sameNumber(attendanceDrafts[row.billingMonth], row.attendanceDays) ? '' : 'fee-dirty-row'
}

function monthlyRowClass({ row }: { row: FeeMonthlyView }) {
  return dirtyMonthly.value.some((dirty) => dirty.id === row.id) ? 'fee-dirty-row' : ''
}

function fillDrafts() {
  Object.keys(attendanceDrafts).forEach((key) => delete attendanceDrafts[key])
  attendance.value.forEach((row) => {
    attendanceDrafts[row.billingMonth] = row.attendanceDays ?? null
  })
  Object.keys(monthlyDrafts).forEach((key) => delete monthlyDrafts[key])
  monthly.value.forEach((row) => {
    monthlyDrafts[row.id] = {
      occurrenceAmount: row.occurrenceAmount ?? null,
      monthFactor: row.monthFactor ?? null,
    }
  })
}

async function loadAll() {
  if (!props.ledger) {
    attendance.value = []
    monthly.value = []
    return
  }
  loading.value = true
  formError.value = ''
  try {
    const [attendanceRows, monthlyRows] = await Promise.all([
      fetchFeeAttendance(props.ledger.id),
      fetchFeeMonthly(props.ledger.id),
    ])
    attendance.value = attendanceRows
    monthly.value = monthlyRows
    fillDrafts()
  } catch (error) {
    attendance.value = []
    monthly.value = []
    formError.value = toMessage(error, '出勤与逐月发生额加载失败')
  } finally {
    loading.value = false
  }
}

function handleOpen() {
  formError.value = ''
  void loadAll()
}

function requestClose() {
  if (savingAll.value || revertingAll.value) {
    ElMessage.warning('正在处理，请稍候再关闭')
    return
  }
  if (dirtyCount.value === 0) {
    visible.value = false
    return
  }
  ElMessageBox.confirm(
    `还有 ${dirtyCount.value} 项修改未保存，关闭后这些修改会丢失。`,
    '放弃修改？',
    { type: 'warning', confirmButtonText: '放弃并关闭', cancelButtonText: '继续编辑' }
  )
    .then(() => {
      visible.value = false
    })
    .catch(() => undefined)
}

function beforeClose(done: () => void) {
  if (savingAll.value || revertingAll.value) {
    ElMessage.warning('正在处理，请稍候再关闭')
    return
  }
  if (dirtyCount.value === 0) {
    done()
    return
  }
  ElMessageBox.confirm(
    `还有 ${dirtyCount.value} 项修改未保存，关闭后这些修改会丢失。`,
    '放弃修改？',
    { type: 'warning', confirmButtonText: '放弃并关闭', cancelButtonText: '继续编辑' }
  )
    .then(() => done())
    .catch(() => undefined)
}

function validateDrafts(attendanceRows: FeeAttendanceView[], monthlyRows: FeeMonthlyView[]) {
  const emptyAttendance = attendanceRows.find(
    (row) => attendanceDrafts[row.billingMonth] === null || attendanceDrafts[row.billingMonth] === undefined
  )
  if (emptyAttendance) {
    return `${monthText(emptyAttendance.billingMonth)}的出勤天数不能为空`
  }
  const emptyMonthly = monthlyRows.find((row) => {
    const draft = monthlyDrafts[row.id]
    return !draft || draft.occurrenceAmount === null || draft.occurrenceAmount === undefined
  })
  if (emptyMonthly) {
    return `${monthText(emptyMonthly.billingMonth)} ${emptyMonthly.itemName || ''} 的生效发生额不能为空`
  }
  const emptyFactor = monthlyRows.find(
    (row) =>
      row.billingMode === 'monthly' &&
      (monthlyDrafts[row.id]?.monthFactor === null || monthlyDrafts[row.id]?.monthFactor === undefined)
  )
  if (emptyFactor) {
    return `${monthText(emptyFactor.billingMonth)} ${emptyFactor.itemName || ''} 的月份系数不能为空`
  }
  return ''
}

/** 出勤先存（会触发按天重算），再存发生额，最后统一重算一次并只刷新一遍列表 */
async function saveAll() {
  if (!props.ledger || dirtyCount.value === 0 || busy.value) {
    return
  }
  const attendanceRows = dirtyAttendance.value.slice()
  const monthlyRows = dirtyMonthly.value.slice()
  const invalid = validateDrafts(attendanceRows, monthlyRows)
  if (invalid) {
    formError.value = invalid
    return
  }

  savingAll.value = true
  formError.value = ''
  const failures: string[] = []
  const failedAttendance: Record<string, number | null> = {}
  const failedMonthly: Record<
    string,
    { occurrenceAmount: number | null; monthFactor: number | null }
  > = {}
  let saved = 0
  let factorChanged = false

  for (const row of attendanceRows) {
    try {
      await overrideFeeAttendance(props.ledger.id, row.billingMonth, {
        attendanceDays: Number(attendanceDrafts[row.billingMonth]),
      })
      saved++
    } catch (error) {
      failedAttendance[row.billingMonth] = attendanceDrafts[row.billingMonth]
      failures.push(`${monthText(row.billingMonth)}出勤：${toMessage(error, '保存失败')}`)
    }
  }

  for (const row of monthlyRows) {
    const draft = monthlyDrafts[row.id]
    const amountChanged = !sameNumber(draft.occurrenceAmount, row.occurrenceAmount)
    // 自动行只改系数时不提交发生额：后端按新系数重算生效值，别把旧值钉成手工覆盖
    const keepAmountOverride = Boolean(row.manualOverride) && !amountChanged
    try {
      await overrideFeeMonthly(row.ledgerItemId, row.billingMonth, {
        occurrenceAmount:
          amountChanged || keepAmountOverride ? Number(draft.occurrenceAmount) : undefined,
        monthFactor: row.billingMode === 'monthly' ? draft.monthFactor : undefined,
      })
      if (row.billingMode === 'monthly' && !sameNumber(draft.monthFactor, row.monthFactor)) {
        factorChanged = true
      }
      saved++
    } catch (error) {
      failedMonthly[row.id] = { ...draft }
      failures.push(
        `${monthText(row.billingMonth)} ${row.itemName || ''}：${toMessage(error, '保存失败')}`
      )
    }
  }

  if (factorChanged) {
    try {
      await recalculateFees({ ledgerId: props.ledger.id })
    } catch (error) {
      failures.push(`重算自动金额失败：${toMessage(error, '请手动点「按当前出勤重算」')}`)
    }
  }

  await loadAll()
  // loadAll 会把草稿重置为服务端值，失败的草稿要放回去，避免用户重录
  Object.entries(failedAttendance).forEach(([key, value]) => {
    attendanceDrafts[key] = value
  })
  Object.entries(failedMonthly).forEach(([key, value]) => {
    monthlyDrafts[key] = value
  })

  const failureCount = failures.length
  if (failureCount > 0) {
    formError.value = `已保存 ${saved} 项，${failureCount} 项失败：${failures.join('；')}`
    ElMessage.warning(`有 ${failureCount} 项未保存，已保留在表格里`)
  } else {
    ElMessage.success(`已保存 ${saved} 项修改`)
  }
  savingAll.value = false
  if (saved > 0 || factorChanged) {
    emit('changed')
  }
}

async function confirmDiscardDrafts(target: string) {
  if (dirtyCount.value === 0) {
    return true
  }
  try {
    await ElMessageBox.confirm(
      `还有 ${dirtyCount.value} 项修改未保存，${target}会重新拉取数据并丢弃这些修改。`,
      '丢弃未保存的修改？',
      { type: 'warning', confirmButtonText: '继续', cancelButtonText: '取消' }
    )
    return true
  } catch {
    return false
  }
}

async function revertAttendance(row: FeeAttendanceView) {
  if (!props.ledger || revertingKey.value || savingAll.value) {
    return
  }
  if (!(await confirmDiscardDrafts(`${monthText(row.billingMonth)}的出勤覆盖撤销`))) {
    return
  }
  revertingKey.value = `attendance-${row.billingMonth}`
  formError.value = ''
  try {
    await revertFeeAttendance(props.ledger.id, row.billingMonth)
    ElMessage.success(`${monthText(row.billingMonth)}的出勤覆盖已撤销`)
    await loadAll()
    emit('changed')
  } catch (error) {
    formError.value = toMessage(error, '撤销出勤覆盖失败')
  } finally {
    revertingKey.value = ''
  }
}

async function revertMonthly(row: FeeMonthlyView) {
  if (revertingKey.value || savingAll.value) {
    return
  }
  if (
    !(await confirmDiscardDrafts(
      `${monthText(row.billingMonth)} ${row.itemName || ''} 的发生额覆盖撤销`
    ))
  ) {
    return
  }
  revertingKey.value = `monthly-${row.id}`
  formError.value = ''
  try {
    await revertFeeMonthly(row.ledgerItemId, row.billingMonth)
    ElMessage.success(
      `${monthText(row.billingMonth)} ${row.itemName || ''} 的发生额覆盖已撤销`
    )
    await loadAll()
    emit('changed')
  } catch (error) {
    formError.value = toMessage(error, '撤销发生额覆盖失败')
  } finally {
    revertingKey.value = ''
  }
}

async function revertAll() {
  if (!props.ledger || manualCount.value === 0 || busy.value) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `将把 ${manualCount.value} 项手工覆盖全部恢复为自动值（出勤回到考勤汇总，发生额回到引擎计算，已调整的月份系数保留）。` +
        (dirtyCount.value > 0 ? `另有 ${dirtyCount.value} 项未保存修改会被丢弃。` : ''),
      '全部恢复自动',
      { type: 'warning', confirmButtonText: '恢复', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  revertingAll.value = true
  formError.value = ''
  const failures: string[] = []
  let reverted = 0
  for (const row of attendance.value.filter((item) => item.manualOverride)) {
    try {
      await revertFeeAttendance(props.ledger.id, row.billingMonth)
      reverted++
    } catch (error) {
      failures.push(`${monthText(row.billingMonth)}出勤：${toMessage(error, '恢复失败')}`)
    }
  }
  for (const row of monthly.value.filter((item) => item.manualOverride)) {
    try {
      await revertFeeMonthly(row.ledgerItemId, row.billingMonth)
      reverted++
    } catch (error) {
      failures.push(
        `${monthText(row.billingMonth)} ${row.itemName || ''}：${toMessage(error, '恢复失败')}`
      )
    }
  }

  await loadAll()
  if (failures.length > 0) {
    formError.value = `已恢复 ${reverted} 项，${failures.length} 项失败：${failures.join('；')}`
  } else {
    ElMessage.success(`已恢复 ${reverted} 项手工覆盖`)
  }
  revertingAll.value = false
  if (reverted > 0) {
    emit('changed')
  }
}

async function syncAttendance() {
  if (!props.ledger) {
    return
  }
  if (!(await confirmDiscardDrafts('从考勤同步'))) {
    return
  }
  syncing.value = true
  formError.value = ''
  try {
    const result = await syncFeeAttendance({ ledgerId: props.ledger.id })
    if (result.syncedCount === 0) {
      // 学期没有起止日期或考勤还没点名时,后端会静默跳过;这里必须说清楚,否则看起来像同步成功了
      ElMessage.warning('没有可同步的月份：请先确认学期起止日期已维护，且该班已有「出勤/迟到」的点名记录')
    } else {
      ElMessage.success(
        `已同步 ${result.ledgerCount} 份台账的出勤，刷新 ${result.syncedCount} 个月份行` +
          (result.keptOverrideCount > 0 ? `；保留 ${result.keptOverrideCount} 条手工覆盖` : '')
      )
    }
    await loadAll()
    emit('changed')
  } catch (error) {
    formError.value = toMessage(error, '从考勤同步失败')
  } finally {
    syncing.value = false
  }
}

async function recalculate() {
  if (!props.ledger) {
    return
  }
  if (!(await confirmDiscardDrafts('按当前出勤重算'))) {
    return
  }
  recalculating.value = true
  formError.value = ''
  try {
    await recalculateFees({ ledgerId: props.ledger.id })
    ElMessage.success('已按当前出勤与标准重算，手工覆盖的发生额保持不变')
    await loadAll()
    emit('changed')
  } catch (error) {
    formError.value = toMessage(error, '重算失败')
  } finally {
    recalculating.value = false
  }
}
</script>

<style scoped>
.monthly-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 24px;
  margin-bottom: 12px;
  padding: 10px 14px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  color: var(--text-secondary);
}

.monthly-student {
  color: var(--text-primary);
  font-weight: 600;
}

.monthly-summary b {
  color: var(--text-primary);
  font-weight: 600;
}

.monthly-manual-count b {
  color: var(--el-color-warning);
}

.monthly-section-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.monthly-second-head {
  margin-top: 18px;
}

.monthly-auto-text {
  color: var(--text-secondary);
  font-size: 12px;
}

.fee-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.fee-footer-hint {
  flex: 1 1 auto;
  min-width: 0;
  color: var(--text-secondary);
  font-size: 13px;
  text-align: left;
}

.fee-footer-actions {
  display: flex;
  gap: 8px;
}

:deep(.el-table__row.fee-dirty-row) {
  --el-table-tr-bg-color: var(--el-color-warning-light-9);
}

:global(.fee-monthly-dialog .el-dialog__body) {
  max-height: calc(100vh - 180px);
  overflow-y: auto;
}
</style>
