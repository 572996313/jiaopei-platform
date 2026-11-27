<template>
  <el-dialog v-model="visible" title="收款登记" width="980px" @open="handleOpen">
    <el-alert
      v-if="formError"
      :title="formError"
      type="error"
      show-icon
      :closable="false"
      class="education-alert"
    />

    <div v-if="ledger" class="payment-summary">
      <span>{{ ledger.studentName || ledger.studentNo }} · {{ ledger.className }}</span>
      <span>应收 <b>{{ moneyText(ledger.receivable) }}</b></span>
      <span>实收 <b>{{ moneyText(ledger.paid) }}</b></span>
      <span>
        未收费
        <b :class="{ 'payment-negative': Number(ledger.unpaid ?? 0) < 0 }">
          {{ moneyText(ledger.unpaid) }}
        </b>
      </span>
      <span>结余 <b>{{ moneyText(ledger.balance) }}</b></span>
    </div>

    <div class="table-toolbar">
      <el-button v-if="canWrite && !formVisible" type="primary" @click="openForm()">
        登记收款
      </el-button>
      <el-button :icon="Refresh" circle :loading="loading" @click="loadPayments" />
      <span class="education-context-text">
        实收是流水的合计，正数收款、负数冲正；未收费为负表示预收，不是错误。
      </span>
    </div>

    <el-table
      :data="payments"
      border
      row-key="id"
      highlight-current-row
      v-loading="loading"
      height="320"
      empty-text="还没有收款流水，点击「登记收款」录入。"
      class="payment-table"
    >
      <el-table-column prop="paymentNo" label="收款单号" width="170" fixed="left" />
      <el-table-column label="收费项目" min-width="130">
        <template #default="{ row }">{{ itemName(row.ledgerItemId) }}</template>
      </el-table-column>
      <el-table-column label="金额" width="120" align="right">
        <template #default="{ row }">{{ moneyText(row.amount) }}</template>
      </el-table-column>
      <el-table-column prop="paidOn" label="收款日期" width="120" />
      <el-table-column label="收款方式" width="110">
        <template #default="{ row }">{{ dictStore.displayLabel('fee_payment_method', row.paymentMethod) }}</template>
      </el-table-column>
      <el-table-column label="归属账期" width="120">
        <template #default="{ row }">{{ row.billingMonth ? monthText(row.billingMonth) : '-' }}</template>
      </el-table-column>
      <el-table-column prop="voucherNo" label="凭证号" width="140" show-overflow-tooltip />
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column v-if="canWrite" label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button link type="primary" @click="openForm(row)">编辑</el-button>
            <el-button link type="danger" @click="confirmDelete(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="formVisible" class="payment-form">
      <div class="education-table-head">
        <span class="panel-title">{{ editingId ? '编辑收款' : '登记收款' }}</span>
      </div>
      <el-form
        ref="paymentFormRef"
        :model="form"
        :rules="paymentRules"
        label-position="top"
        class="education-form payment-form-grid"
        scroll-to-error
        @submit.prevent
      >
        <el-form-item label="收费项目" prop="ledgerItemId">
          <el-select
            v-model="form.ledgerItemId"
            :disabled="Boolean(editingId)"
            placeholder="收到的钱归到哪个项目"
            style="width: 100%"
          >
            <el-option
              v-for="item in ledger?.items ?? []"
              :key="item.id"
              :label="item.itemName || item.itemCode || `项目 #${item.feeItemId}`"
              :value="String(item.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="收款金额" prop="amount">
          <el-input-number
            v-model="form.amount"
            :precision="4"
            :step="100"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="收款日期" prop="paidOn">
          <el-date-picker
            v-model="form.paidOn"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择收款日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="收款方式">
          <DictSelect v-model="form.paymentMethod" type-code="fee_payment_method" />
        </el-form-item>
        <el-form-item label="归属账期">
          <el-date-picker
            v-model="form.billingMonth"
            type="month"
            value-format="YYYY-MM"
            placeholder="可空"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="凭证号 / 银行流水号">
          <el-input v-model="form.voucherNo" />
        </el-form-item>
        <el-form-item label="收款单号">
          <el-input v-model="form.paymentNo" placeholder="留空自动生成 SK-日期-序号" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="如：按月收" />
        </el-form-item>
      </el-form>
      <div class="payment-form-actions">
        <div class="payment-form-spacer" />
        <el-button @click="closeForm">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存收款</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { computed, nextTick, reactive, ref } from 'vue'
import DictSelect from '@cornerstone/core/components/DictSelect.vue'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { useDictStore } from '@cornerstone/core/stores/dict'
import { toMessage } from '@cornerstone/core/utils/error'
import { trimToUndefined } from '@cornerstone/core/utils/text'
import {
  createFeePayment,
  deleteFeePayment,
  fetchFeePayments,
  moneyText,
  monthText,
  updateFeePayment,
  type FeeLedgerDetailView,
  type FeePaymentView,
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
const dictStore = useDictStore()
const canWrite = computed(() => auth.hasPermission('education:fee:write'))

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const payments = ref<FeePaymentView[]>([])
const loading = ref(false)
const formError = ref('')
const formVisible = ref(false)
const editingId = ref<string | null>(null)
const submitting = ref(false)
const paymentFormRef = ref<FormInstance | null>(null)

/** 必填校验统一交给 el-form：标签红星 + 输入框红边 + 框下行内提示，不靠顶部一条总文案 */
const paymentRules: FormRules = {
  ledgerItemId: [{ required: true, message: '请选择收费项目', trigger: 'change' }],
  amount: [
    {
      // 金额允许 0（如冲正），只拦空值
      required: true,
      validator: (_rule, value: number | null, callback) => {
        if (value === null || value === undefined) {
          callback(new Error('请填写收款金额'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
  paidOn: [{ required: true, message: '请选择收款日期', trigger: 'change' }],
}

const form = reactive({
  ledgerItemId: '' as string | null,
  paymentNo: '',
  amount: null as number | null,
  paidOn: todayValue(),
  paymentMethod: '' as string | null,
  billingMonth: '',
  voucherNo: '',
  remark: '',
})

const itemNameMap = computed(
  () =>
    new Map(
      (props.ledger?.items ?? []).map((item) => [
        String(item.id),
        item.itemName || item.itemCode || `项目 #${item.feeItemId}`,
      ])
    )
)

function todayValue() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}

function itemName(ledgerItemId: string) {
  return itemNameMap.value.get(String(ledgerItemId)) ?? `项目 #${ledgerItemId}`
}

async function loadPayments() {
  if (!props.ledger) {
    payments.value = []
    return
  }
  loading.value = true
  formError.value = ''
  try {
    payments.value = await fetchFeePayments({ ledgerId: props.ledger.id })
  } catch (error) {
    payments.value = []
    formError.value = toMessage(error, '收款流水加载失败')
  } finally {
    loading.value = false
  }
}

function handleOpen() {
  closeForm()
  // 表格里的收款方式列不会触发 DictSelect 自己去拉字典，进弹窗时先备好
  void dictStore.ensureLoaded(['fee_payment_method']).catch(() => undefined)
  void loadPayments()
}

function resetForm() {
  form.ledgerItemId = props.ledger?.items?.[0] ? String(props.ledger.items[0].id) : ''
  form.paymentNo = ''
  form.amount = null
  form.paidOn = todayValue()
  form.paymentMethod = ''
  form.billingMonth = ''
  form.voucherNo = ''
  form.remark = ''
  formError.value = ''
}

function openForm(row?: FeePaymentView) {
  resetForm()
  if (row) {
    editingId.value = row.id
    form.ledgerItemId = String(row.ledgerItemId)
    form.paymentNo = row.paymentNo ?? ''
    form.amount = row.amount ?? null
    form.paidOn = row.paidOn ?? todayValue()
    form.paymentMethod = row.paymentMethod ?? ''
    form.billingMonth = row.billingMonth ? row.billingMonth.slice(0, 7) : ''
    form.voucherNo = row.voucherNo ?? ''
    form.remark = row.remark ?? ''
  } else {
    editingId.value = null
  }
  formVisible.value = true
  // 上一次填写留下的红框不能带给新一次填写
  void nextTick(() => paymentFormRef.value?.clearValidate())
}

function closeForm() {
  formVisible.value = false
  editingId.value = null
  formError.value = ''
}

async function submitForm() {
  if (!props.ledger) {
    formError.value = '请先选择收费台账'
    return
  }
  // 校验失败时 el-form 已经在各字段上标红并给出提示，这里不再补一条顶部总文案
  const valid = await paymentFormRef.value?.validate().catch(() => false)
  if (valid === false) {
    return
  }
  // required 规则已保证这两项非空，这里只为类型收窄（金额 0 是合法值，不能当成空）
  if (!form.ledgerItemId || form.amount === null) {
    return
  }
  submitting.value = true
  formError.value = ''
  const billingMonth = form.billingMonth ? `${form.billingMonth}-01` : null
  try {
    if (editingId.value) {
      await updateFeePayment(editingId.value, {
        amount: form.amount,
        paidOn: form.paidOn,
        paymentMethod: trimToUndefined(form.paymentMethod ?? ''),
        billingMonth,
        voucherNo: trimToUndefined(form.voucherNo),
        remark: trimToUndefined(form.remark),
      })
      ElMessage.success('收款流水已更新')
    } else {
      await createFeePayment({
        ledgerId: props.ledger.id,
        ledgerItemId: form.ledgerItemId,
        paymentNo: trimToUndefined(form.paymentNo),
        amount: form.amount,
        paidOn: form.paidOn,
        paymentMethod: trimToUndefined(form.paymentMethod ?? ''),
        billingMonth,
        voucherNo: trimToUndefined(form.voucherNo),
        remark: trimToUndefined(form.remark),
      })
      ElMessage.success('收款已登记')
    }
    closeForm()
    await loadPayments()
    emit('changed')
  } catch (error) {
    formError.value = toMessage(error, editingId.value ? '更新收款失败' : '登记收款失败')
  } finally {
    submitting.value = false
  }
}

async function confirmDelete(row: FeePaymentView) {
  try {
    await ElMessageBox.confirm(
      `确定删除收款流水「${row.paymentNo || row.id}」（${moneyText(row.amount)}）吗？删除后实收合计立即减少。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteFeePayment(row.id)
    ElMessage.success('收款流水已删除')
    await loadPayments()
    emit('changed')
  } catch (error) {
    ElMessage.error(toMessage(error, '删除收款失败'))
  }
}
</script>

<style scoped>
.payment-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  margin-bottom: 12px;
  color: var(--text-secondary);
}

.payment-summary b {
  color: var(--text-primary);
  font-weight: 600;
}

.payment-negative {
  color: var(--el-color-warning);
}

.payment-form {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.payment-form-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0 12px;
}

.payment-form-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.payment-form-spacer {
  flex: 1 1 auto;
}

.payment-table :deep(.el-table__empty-block) {
  min-height: calc(320px - 38px);
}
</style>
