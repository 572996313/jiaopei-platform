<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <div class="table-toolbar">
      <el-button v-if="canWrite" type="primary" @click="openGenerateDialog">生成台账</el-button>
      <el-button v-if="canWrite" @click="openLedgerDialog('create')">新增台账</el-button>
      <el-button :loading="exportingMatrix" @click="exportMatrix">导出矩阵</el-button>
      <el-button :loading="exportingDetail" @click="exportDetail">导出明细</el-button>
      <el-button :icon="Refresh" circle :loading="loading" @click="refreshAll" />
      <span class="education-context-text">
        结余 = 实收 − 合计发生，即可退金额；未收费为负表示预收。矩阵导出需先选学期与班级。
      </span>
    </div>

    <FilterBar :loading="tableLoading" @query="reloadFirstPage" @reset="resetFilters">
      <el-select v-model="termId" placeholder="学期" clearable filterable style="width: 180px">
        <el-option
          v-for="term in terms"
          :key="term.id"
          :label="term.termName || term.termCode || `学期 #${term.id}`"
          :value="String(term.id)"
        />
      </el-select>
      <ClassSelect v-model="classId" :institution-id="institutionId" placeholder="筛选班级" />
      <el-input
        v-model="keyword"
        placeholder="学号 / 姓名 / 班级名"
        clearable
        @keyup.enter="reloadFirstPage"
      />
    </FilterBar>

    <article class="panel-card education-panel">
      <TableFillBox v-slot="{ height }">
        <el-table
          :data="rows"
          border
          row-key="id"
          highlight-current-row
          v-loading="tableLoading"
          :height="height"
          :empty-text="tableLoading ? '' : '当前筛选范围下没有收费台账，点击「生成台账」按在班名册批量建账。'"
        >
          <el-table-column prop="studentNo" label="学号" width="130" fixed="left" />
          <el-table-column prop="studentName" label="姓名" width="110" fixed="left" show-overflow-tooltip />
          <el-table-column prop="className" label="班级" min-width="150" show-overflow-tooltip />
          <el-table-column prop="termName" label="学期" width="140" show-overflow-tooltip />
          <el-table-column label="应收" width="110" align="right">
            <template #default="{ row }">{{ moneyText(row.receivable) }}</template>
          </el-table-column>
          <el-table-column label="实收" width="110" align="right">
            <template #default="{ row }">{{ moneyText(row.paid) }}</template>
          </el-table-column>
          <el-table-column label="未收费" width="110" align="right">
            <template #default="{ row }">
              <span :class="{ 'fee-prepaid': Number(row.unpaid ?? 0) < 0 }">
                {{ moneyText(row.unpaid) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="合计发生" width="110" align="right">
            <template #default="{ row }">{{ moneyText(row.totalOccurrence) }}</template>
          </el-table-column>
          <el-table-column label="结余（可退）" width="120" align="right">
            <template #default="{ row }">
              <b>{{ moneyText(row.balance) }}</b>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 'settled' ? 'info' : 'success'">
                {{ row.status === 'settled' ? '已结清' : '在收' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
          <el-table-column v-if="canWrite" label="操作" width="250" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button link type="primary" @click="openLedgerDialog('edit', row)">编辑</el-button>
                <el-button link type="primary" @click="openPayment(row)">收款</el-button>
                <el-button link type="primary" @click="openMonthly(row)">逐月/出勤</el-button>
                <el-button link type="danger" @click="confirmDelete(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </TableFillBox>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        class="panel-pagination"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadRows"
        @size-change="reloadFirstPage"
      />
    </article>

    <el-dialog v-model="generateVisible" title="按在班名册生成台账" width="560px">
      <el-alert
        v-if="generateError"
        :title="generateError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />
      <el-form label-position="top" class="education-form">
        <el-form-item label="学期" required>
          <el-select v-model="generateForm.termId" placeholder="选择学期" style="width: 100%">
            <el-option
              v-for="term in terms"
              :key="term.id"
              :label="term.termName || term.termCode || `学期 #${term.id}`"
              :value="String(term.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="班级" required>
          <ClassSelect
            v-model="generateForm.classId"
            :institution-id="institutionId"
            :term-id="generateForm.termId || null"
          />
        </el-form-item>
      </el-form>
      <el-alert
        title="只为名册里还没有该学期台账的学员建账；已存在的学员会跳过，不会覆盖已录入的收费数据。"
        type="info"
        show-icon
        :closable="false"
      />
      <template #footer>
        <el-button @click="generateVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="submitGenerate">开始生成</el-button>
      </template>
    </el-dialog>

    <FeeLedgerDialog
      v-model="ledgerDialogVisible"
      :mode="ledgerDialogMode"
      :institution-id="institutionId || ''"
      :fee-items="feeItems"
      :terms="terms"
      :ledger="editingLedger"
      :default-term-id="termId"
      :default-class-id="classId"
      @saved="refreshAfterChange"
    />

    <FeePaymentDialog
      v-model="paymentVisible"
      :ledger="activeLedger"
      @changed="refreshAfterChange"
    />

    <FeeMonthlyDialog
      v-model="monthlyVisible"
      :ledger="activeLedger"
      @changed="refreshAfterChange"
    />
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import FilterBar from '@cornerstone/core/components/FilterBar.vue'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import { useEducationScope } from '@cornerstone/module-education/composables/useEducationScope'
import { classLabelOf } from '@cornerstone/module-education/composables/classLabels'
import ClassSelect from './components/ClassSelect.vue'
import FeeLedgerDialog from './components/FeeLedgerDialog.vue'
import FeePaymentDialog from './components/FeePaymentDialog.vue'
import FeeMonthlyDialog from './components/FeeMonthlyDialog.vue'
import {
  deleteFeeLedger,
  exportFeeDetail,
  exportFeeMatrix,
  fetchFeeItems,
  fetchFeeLedger,
  fetchFeeLedgerPage,
  generateFeeLedgers,
  moneyText,
  type FeeItemRecord,
  type FeeLedgerDetailView,
  type FeeLedgerRowView,
} from '@cornerstone/module-education/api/fee'

const institutionStore = useInstitutionStore()
const auth = useAuthStore()
const { institutionId, campusId, terms, loadScope } = useEducationScope()
const canWrite = computed(() => auth.hasPermission('education:fee:write'))

const rows = ref<FeeLedgerRowView[]>([])
const feeItems = ref<FeeItemRecord[]>([])
const termId = ref<string | null>(null)
const classId = ref<string | null>(null)
const keyword = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const loading = ref(false)
const tableLoading = ref(false)
const pageError = ref('')

const generateVisible = ref(false)
const generateError = ref('')
const generating = ref(false)
const generateForm = reactive({ termId: '', classId: '' })

const ledgerDialogVisible = ref(false)
const ledgerDialogMode = ref<'create' | 'edit'>('create')
const editingLedger = ref<FeeLedgerDetailView | null>(null)

const paymentVisible = ref(false)
const monthlyVisible = ref(false)
const activeLedger = ref<FeeLedgerDetailView | null>(null)

const exportingMatrix = ref(false)
const exportingDetail = ref(false)

const termNameMap = computed(
  () =>
    new Map(
      terms.value.map((term) => [
        String(term.id),
        term.termName || term.termCode || `学期 #${term.id}`,
      ])
    )
)

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function trimToUndefined(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

async function loadLookups() {
  if (!institutionId.value) {
    feeItems.value = []
    return
  }
  try {
    await loadScope()
    feeItems.value = await fetchFeeItems({ institutionId: institutionId.value })
  } catch (error) {
    pageError.value = toMessage(error, '范围数据加载失败')
  }
}

async function loadRows() {
  if (!institutionId.value) {
    rows.value = []
    total.value = 0
    return
  }
  tableLoading.value = true
  pageError.value = ''
  try {
    const result = await fetchFeeLedgerPage({
      institutionId: institutionId.value,
      campusId: campusId.value || undefined,
      termId: termId.value,
      classId: classId.value,
      keyword: trimToUndefined(keyword.value),
      page: page.value,
      pageSize: pageSize.value,
    })
    rows.value = result.records
    total.value = result.total
  } catch (error) {
    rows.value = []
    total.value = 0
    pageError.value = toMessage(error, '收费台账加载失败')
  } finally {
    tableLoading.value = false
  }
}

function reloadFirstPage() {
  page.value = 1
  void loadRows()
}

async function refreshAll() {
  loading.value = true
  try {
    await loadLookups()
    await loadRows()
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  termId.value = null
  classId.value = null
  keyword.value = ''
  reloadFirstPage()
}

async function refreshAfterChange() {
  const pageTotal = total.value
  // 收款/逐月弹窗改完后顶部汇总还读着打开时的快照，先把详情重新拉一遍再刷列表
  if (activeLedger.value) {
    try {
      activeLedger.value = await fetchFeeLedger(activeLedger.value.id)
    } catch {
      // 列表刷新才是主反馈，详情这一下失败不额外打断操作
    }
  }
  await loadRows()
  // 删掉当页最后一条后页码可能落到空页，夹回实有最后一页
  if (rows.value.length === 0 && pageTotal > 0 && page.value > 1) {
    page.value = Math.max(1, Math.ceil((pageTotal - 1) / pageSize.value))
    await loadRows()
  }
}

function openGenerateDialog() {
  generateError.value = ''
  generateForm.termId = termId.value || ''
  generateForm.classId = classId.value || ''
  generateVisible.value = true
}

async function submitGenerate() {
  if (!institutionId.value) {
    generateError.value = '请先选择机构范围'
    return
  }
  if (!generateForm.termId || !generateForm.classId) {
    generateError.value = '请选择学期与班级'
    return
  }
  generating.value = true
  generateError.value = ''
  try {
    const result = await generateFeeLedgers({
      institutionId: institutionId.value,
      termId: generateForm.termId,
      classId: generateForm.classId,
    })
    ElMessage.success(
      `新建 ${result.createdCount} 份台账，跳过 ${result.skippedCount} 名已有台账的学员，` +
        `每份自动建 ${result.itemCount} 个收费项目行`
    )
    generateVisible.value = false
    reloadFirstPage()
  } catch (error) {
    generateError.value = toMessage(error, '批量建账失败')
  } finally {
    generating.value = false
  }
}

async function loadLedgerDetail(row: FeeLedgerRowView) {
  try {
    return await fetchFeeLedger(row.id)
  } catch (error) {
    ElMessage.error(toMessage(error, '收费台账详情加载失败'))
    return null
  }
}

async function openLedgerDialog(mode: 'create' | 'edit', row?: FeeLedgerRowView) {
  ledgerDialogMode.value = mode
  if (mode === 'edit' && row) {
    const detail = await loadLedgerDetail(row)
    if (!detail) {
      return
    }
    editingLedger.value = detail
  } else {
    editingLedger.value = null
  }
  ledgerDialogVisible.value = true
}

async function openPayment(row: FeeLedgerRowView) {
  const detail = await loadLedgerDetail(row)
  if (!detail) {
    return
  }
  activeLedger.value = detail
  paymentVisible.value = true
}

async function openMonthly(row: FeeLedgerRowView) {
  const detail = await loadLedgerDetail(row)
  if (!detail) {
    return
  }
  activeLedger.value = detail
  monthlyVisible.value = true
}

async function confirmDelete(row: FeeLedgerRowView) {
  try {
    await ElMessageBox.confirm(
      `确定删除 ${row.studentName || row.studentNo} 在「${row.className} / ${row.termName}」的收费台账吗？` +
        '已有收款流水时会被后端拒绝，请先删除收款。',
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteFeeLedger(row.id)
    ElMessage.success('收费台账已删除')
    await refreshAfterChange()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除收费台账失败'))
  }
}

function exportParams() {
  return {
    institutionId: institutionId.value || undefined,
    campusId: campusId.value || undefined,
    termId: termId.value || undefined,
    classId: classId.value || undefined,
    keyword: trimToUndefined(keyword.value),
  }
}

/** 文件名里的机构/班级名可能带路径字符，去掉再给浏览器 */
function safePart(value?: string | null) {
  return (value ?? '').replace(/[\\/:*?"<>|]/g, '').trim()
}

function exportPrefix() {
  const term = termId.value ? termNameMap.value.get(termId.value) : ''
  const clazz = classId.value ? classLabelOf(classId.value) : ''
  return [safePart(term), safePart(clazz)].filter(Boolean).join('-')
}

async function exportMatrix() {
  if (!termId.value || !classId.value) {
    ElMessage.warning('导出矩阵请先选择学期与班级，矩阵表按同一班同一学期成表')
    return
  }
  exportingMatrix.value = true
  try {
    const prefix = exportPrefix()
    await exportFeeMatrix(exportParams(), `${prefix ? `${prefix}-` : ''}收费出勤表.xlsx`)
  } catch (error) {
    ElMessage.error(toMessage(error, '导出矩阵失败'))
  } finally {
    exportingMatrix.value = false
  }
}

async function exportDetail() {
  exportingDetail.value = true
  try {
    const prefix = exportPrefix()
    await exportFeeDetail(exportParams(), `${prefix ? `${prefix}-` : ''}收费台账明细.xlsx`)
  } catch (error) {
    ElMessage.error(toMessage(error, '导出明细失败'))
  } finally {
    exportingDetail.value = false
  }
}

watch(institutionId, () => {
  termId.value = null
  classId.value = null
  void refreshAll()
})

watch(campusId, () => {
  // 校区芯片是全站范围，换了就把本页选中的班级一起清掉（它可能属于旧校区）
  classId.value = null
  reloadFirstPage()
})

onMounted(async () => {
  await institutionStore.loadInstitutions()
  await refreshAll()
})
</script>

<style scoped>
.fee-prepaid {
  color: var(--el-color-warning);
}
</style>
