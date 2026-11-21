<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  createFeePayment,
  deleteFeePayment,
  fetchDictOptions,
  fetchFeeLedger,
  monthText,
  optionalText,
  todayText,
  updateFeePayment,
  type DictOptionItem,
  type FeeLedgerDetailView,
  type FeePaymentView,
} from '@/api/enterprise'
import type { PickerOption } from '@/components/picker-types'
import PickerField from '@/components/PickerField.vue'

const ledgerId = ref('')
const paymentId = ref('')
const detail = ref<FeeLedgerDetailView | null>(null)
const payment = ref<FeePaymentView | null>(null)
const submitting = ref(false)

const form = ref({
  ledgerItemId: '',
  itemLabel: '',
  amount: '',
  paidOn: todayText(),
  paymentMethod: '',
  billingMonth: '', // YYYY-MM，空=不指定
  voucherNo: '',
  remark: '',
})

const methodOptions = ref<Array<{ value: string; label: string }>>([{ value: '', label: '不指定' }])
const methodIndex = ref(0)

const isEditing = ref(false)

onLoad((options) => {
  ledgerId.value = options?.ledgerId || ''
  paymentId.value = options?.paymentId || ''
  isEditing.value = Boolean(paymentId.value)
  load()
})

async function load() {
  if (!ledgerId.value) {
    uni.showToast({ title: '缺少台账参数', icon: 'none' })
    return
  }
  try {
    const [data, dictItems] = await Promise.all([
      fetchFeeLedger(ledgerId.value),
      fetchDictOptions('fee_payment_method').catch(() => [] as DictOptionItem[]),
    ])
    detail.value = data
    methodOptions.value = [
      { value: '', label: '不指定' },
      ...dictItems.map((item) => ({ value: item.itemValue, label: item.itemName })),
    ]

    if (paymentId.value) {
      const found = data.payments.find((item) => item.id === paymentId.value)
      if (!found) {
        uni.showToast({ title: '收款流水不存在', icon: 'none' })
        return
      }
      payment.value = found
      form.value = {
        ledgerItemId: found.ledgerItemId,
        itemLabel: itemLabelOf(found.ledgerItemId),
        amount: found.amount === null || found.amount === undefined ? '' : String(found.amount),
        paidOn: found.paidOn || todayText(),
        paymentMethod: found.paymentMethod || '',
        billingMonth: found.billingMonth ? found.billingMonth.slice(0, 7) : '',
        voucherNo: found.voucherNo || '',
        remark: found.remark || '',
      }
      syncMethodIndex()
    }
  } catch (error) {
    uni.showToast({ title: (error as Error)?.message || '加载失败', icon: 'none' })
  }
}

function itemLabelOf(ledgerItemId: string) {
  const item = detail.value?.items.find((row) => row.id === ledgerItemId)
  return item ? `${item.itemName || '项目'}（${item.itemCode || '-'}）` : `项目 #${ledgerItemId}`
}

async function loadItemOptions(keyword: string): Promise<PickerOption[]> {
  const kw = keyword.trim()
  return (detail.value?.items ?? [])
    .filter((item) => !kw || (item.itemName || '').includes(kw) || (item.itemCode || '').includes(kw))
    .map((item) => ({
      value: item.id,
      label: `${item.itemName || '未命名项目'}（${item.itemCode || '-'}）`,
    }))
}

function onItemChange(option: PickerOption | null) {
  form.value.itemLabel = option?.label || ''
  if (!option) {
    form.value.ledgerItemId = ''
  }
}

function syncMethodIndex() {
  const index = methodOptions.value.findIndex((item) => item.value === form.value.paymentMethod)
  methodIndex.value = index < 0 ? 0 : index
  if (index < 0) {
    form.value.paymentMethod = ''
  }
}

function onMethodChange(event: { detail: { value: string | number } }) {
  methodIndex.value = Number(event.detail.value)
  form.value.paymentMethod = methodOptions.value[methodIndex.value]?.value ?? ''
}

function onPaidOnChange(event: { detail: { value: string } }) {
  form.value.paidOn = event.detail.value
}

function onBillingMonthChange(event: { detail: { value: string } }) {
  form.value.billingMonth = event.detail.value
}

function clearBillingMonth() {
  form.value.billingMonth = ''
}

const billingMonthText = () => {
  return form.value.billingMonth ? monthText(`${form.value.billingMonth}-01`) : '不指定'
}

async function submit() {
  if (submitting.value) {
    return
  }
  if (!isEditing.value && !form.value.ledgerItemId) {
    uni.showToast({ title: '请选择收费项目', icon: 'none' })
    return
  }
  const amount = Number(form.value.amount)
  if (form.value.amount.trim() === '' || Number.isNaN(amount)) {
    uni.showToast({ title: '请填写收款金额', icon: 'none' })
    return
  }
  if (!form.value.paidOn) {
    uni.showToast({ title: '请选择收款日期', icon: 'none' })
    return
  }

  const billingMonth = form.value.billingMonth ? `${form.value.billingMonth}-01` : null
  const common = {
    amount,
    paidOn: form.value.paidOn,
    paymentMethod: form.value.paymentMethod || undefined,
    billingMonth,
    voucherNo: optionalText(form.value.voucherNo),
    remark: optionalText(form.value.remark),
  }

  submitting.value = true
  uni.showLoading({ title: '保存中…' })
  try {
    if (isEditing.value && payment.value) {
      // 后端更新接口只改非空字段，归属账期/收款方式清空不会生效，这里只提交有值的变化
      await updateFeePayment(payment.value.id, {
        amount,
        paidOn: form.value.paidOn,
        paymentMethod: form.value.paymentMethod || undefined,
        billingMonth: billingMonth || undefined,
        // 更新接口 null/缺省=不改，留空传空串才能清掉凭证号与备注
        voucherNo: form.value.voucherNo.trim(),
        remark: form.value.remark.trim(),
      })
    } else {
      await createFeePayment({
        ledgerId: ledgerId.value,
        ledgerItemId: form.value.ledgerItemId,
        ...common,
      })
    }
    uni.hideLoading()
    uni.showToast({ title: '已保存', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 500)
  } catch (error) {
    uni.hideLoading()
    uni.showToast({ title: (error as Error)?.message || '保存失败', icon: 'none', duration: 2600 })
  } finally {
    submitting.value = false
  }
}

function confirmDelete() {
  if (!payment.value) {
    return
  }
  uni.showModal({
    title: '删除收款',
    content: '删除后实收与结余会立即变化，确定继续吗？',
    confirmColor: '#dc2626',
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      try {
        await deleteFeePayment(payment.value!.id)
        uni.showToast({ title: '已删除', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 500)
      } catch (error) {
        uni.showToast({ title: (error as Error)?.message || '删除失败', icon: 'none' })
      }
    },
  })
}
</script>

<template>
  <view class="ent-page">
    <view v-if="detail" class="ent-card">
      <view class="ent-card-title">
        <text>{{ detail.studentName }} · {{ detail.className }}</text>
        <text class="ent-tag blue">{{ detail.termName }}</text>
      </view>
      <view class="ent-tip">收款金额填正数是正常收款，负数用于冲正/退回。收款项目决定这笔钱冲减哪个项目的未收费。</view>
    </view>

    <view class="ent-card">
      <view class="ent-form-item">
        <text class="ent-form-label"><text class="ent-required">*</text> 收费项目</text>
        <view v-if="isEditing" class="ent-picker">{{ form.itemLabel }}</view>
        <PickerField
          v-else
          v-model="form.ledgerItemId"
          :label="form.itemLabel"
          title="选择收费项目"
          placeholder="这笔钱算到哪个项目"
          :loader="loadItemOptions"
          @change="onItemChange"
        />
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label"><text class="ent-required">*</text> 收款金额</text>
        <input
          v-model="form.amount"
          class="ent-input"
          type="text"
          placeholder="如 1500.00；负数=冲正/退回"
          placeholder-class="input-placeholder"
        />
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label"><text class="ent-required">*</text> 收款日期</text>
        <picker mode="date" :value="form.paidOn" @change="onPaidOnChange">
          <view class="ent-picker">{{ form.paidOn }}</view>
        </picker>
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label">收款方式</text>
        <picker
          mode="selector"
          :range="methodOptions"
          range-key="label"
          :value="methodIndex"
          @change="onMethodChange"
        >
          <view class="ent-picker" :class="{ 'is-placeholder': !form.paymentMethod }">
            {{ methodOptions[methodIndex]?.label || '不指定' }}
          </view>
        </picker>
      </view>

      <view class="ent-form-item">
        <view class="label-row">
          <text class="ent-form-label">归属账期（选填，仅备注）</text>
          <text v-if="form.billingMonth" class="clear-link" @click="clearBillingMonth">清空</text>
        </view>
        <picker mode="date" fields="month" :value="form.billingMonth" @change="onBillingMonthChange">
          <view class="ent-picker" :class="{ 'is-placeholder': !form.billingMonth }">
            {{ billingMonthText() }}
          </view>
        </picker>
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label">凭证号 / 银行流水号</text>
        <input
          v-model="form.voucherNo"
          class="ent-input"
          type="text"
          maxlength="64"
          placeholder="选填"
          placeholder-class="input-placeholder"
        />
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label">备注</text>
        <input
          v-model="form.remark"
          class="ent-input"
          type="text"
          maxlength="500"
          placeholder="如：按月收"
          placeholder-class="input-placeholder"
        />
      </view>
    </view>

    <view class="ent-footer">
      <view class="ent-btn primary" :class="{ disabled: submitting }" @click="submit">
        {{ submitting ? '保存中…' : (isEditing ? '保存修改' : '提交收款') }}
      </view>
      <view v-if="isEditing" class="ent-btn danger delete-btn" @click="confirmDelete">删除这笔收款</view>
    </view>
  </view>
</template>

<style scoped>
.input-placeholder {
  color: #9ca3af;
}

.label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.clear-link {
  font-size: 24rpx;
  color: #6b7280;
}

.ent-footer {
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
}

.delete-btn {
  width: 100%;
  margin-top: 16rpx;
}
</style>
