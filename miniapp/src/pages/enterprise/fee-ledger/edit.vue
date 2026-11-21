<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  billingModeLabel,
  fetchFeeItems,
  fetchFeeLedger,
  optionalNumber,
  optionalText,
  updateFeeLedger,
  type FeeItemRecord,
  type FeeLedgerDetailView,
} from '@/api/enterprise'
import type { PickerOption } from '@/components/picker-types'
import PickerField from '@/components/PickerField.vue'

interface EditableItem {
  id: string
  feeItemId: string
  itemName: string
  billingMode: string
  billableMonths: string
  monthlyStandard: string
  dailyRate: string
  openingBalance: string
  otherDeduction: string
  remark: string
}

const ledgerId = ref('')
const detail = ref<FeeLedgerDetailView | null>(null)
const items = ref<EditableItem[]>([])
const activeFeeItems = ref<FeeItemRecord[]>([])
const loading = ref(false)
const saving = ref(false)

const addPickerValue = ref('')
const addPickerLabel = ref('')

const existingFeeItemIds = computed(() => new Set(items.value.map((item) => item.feeItemId)))

function toText(value?: number | null) {
  return value === null || value === undefined ? '' : String(value)
}

onLoad((options) => {
  ledgerId.value = options?.id || ''
  load()
})

async function load() {
  if (!ledgerId.value) {
    return
  }
  loading.value = true
  try {
    const [data, feeItems] = await Promise.all([
      fetchFeeLedger(ledgerId.value),
      fetchFeeItems({ status: 'active' }),
    ])
    detail.value = data
    activeFeeItems.value = feeItems
    items.value = data.items.map((item) => ({
      id: item.id,
      feeItemId: item.feeItemId,
      itemName: item.itemName || item.itemCode || '未命名项目',
      billingMode: item.billingMode || 'monthly',
      billableMonths: toText(item.billableMonths),
      monthlyStandard: toText(item.monthlyStandard),
      dailyRate: toText(item.dailyRate),
      openingBalance: toText(item.openingBalance),
      otherDeduction: toText(item.otherDeduction),
      remark: item.remark || '',
    }))
  } catch (error) {
    uni.showToast({ title: (error as Error)?.message || '台账加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function loadAddOptions(keyword: string): Promise<PickerOption[]> {
  if (activeFeeItems.value.length === 0) {
    activeFeeItems.value = await fetchFeeItems({ status: 'active' })
  }
  const kw = keyword.trim()
  return activeFeeItems.value
    .filter((item) => !existingFeeItemIds.value.has(item.id))
    .filter((item) => !kw || (item.itemName || '').includes(kw) || (item.itemCode || '').includes(kw))
    .map((item) => ({
      value: item.id,
      label: `${item.itemName || '未命名项目'}（${billingModeLabel(item.billingMode)}）`,
      sub: item.itemCode || undefined,
      meta: { itemName: item.itemName || '', billingMode: item.billingMode || 'monthly' },
    }))
}

function onAddChange(option: PickerOption | null) {
  addPickerLabel.value = option?.label || ''
  if (!option) {
    return
  }
  items.value.push({
    id: '',
    feeItemId: option.value,
    itemName: String(option.meta?.itemName || option.label),
    billingMode: String(option.meta?.billingMode || 'monthly'),
    billableMonths: '',
    monthlyStandard: '',
    dailyRate: '',
    openingBalance: '',
    otherDeduction: '',
    remark: '',
  })
  // 选完清空,同一个选择器可以连续添加
  addPickerValue.value = ''
  addPickerLabel.value = ''
}

function removeItem(index: number) {
  const item = items.value[index]
  const message = item.id
    ? `移除「${item.itemName}」后保存时，如果它没有收款或手工覆盖，会被真正删除。确定吗？`
    : `确定移除「${item.itemName}」吗？`
  uni.showModal({
    title: '移除收费项目',
    content: message,
    confirmColor: '#dc2626',
    success: (result) => {
      if (result.confirm) {
        items.value.splice(index, 1)
      }
    },
  })
}

async function save() {
  if (saving.value) {
    return
  }
  if (items.value.length === 0) {
    uni.showToast({ title: '至少要保留一个收费项目', icon: 'none' })
    return
  }
  saving.value = true
  uni.showLoading({ title: '保存中…' })
  try {
    await updateFeeLedger(ledgerId.value, {
      items: items.value.map((item) => ({
        feeItemId: item.feeItemId,
        billableMonths: optionalNumber(item.billableMonths),
        monthlyStandard: optionalNumber(item.monthlyStandard),
        dailyRate: optionalNumber(item.dailyRate),
        openingBalance: optionalNumber(item.openingBalance),
        otherDeduction: optionalNumber(item.otherDeduction),
        remark: optionalText(item.remark),
      })),
    })
    uni.hideLoading()
    uni.showToast({ title: '已保存', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 500)
  } catch (error) {
    uni.hideLoading()
    uni.showToast({ title: (error as Error)?.message || '保存失败', icon: 'none', duration: 2600 })
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <view class="ent-page">
    <view v-if="loading" class="ent-empty">加载中…</view>

    <template v-if="!loading && detail">
      <view class="ent-card">
        <view class="ent-card-title">
          <text>{{ detail.studentName }} · {{ detail.className }}</text>
          <text class="ent-tag blue">{{ detail.termName }}</text>
        </view>
        <view class="ent-tip">
          整表覆盖提交：未列出的收费项目在「没有收款且没有手工覆盖」时会被删除。
          月标准 / 日标准留空 = 按价目表重新取价，填 0 = 免费。
        </view>
      </view>

      <view class="ent-card">
        <view class="ent-form-item">
          <text class="ent-form-label">添加收费项目</text>
          <PickerField
            v-model="addPickerValue"
            :label="addPickerLabel"
            title="选择收费项目"
            placeholder="从启用中的收费项目里选择"
            :loader="loadAddOptions"
            @change="onAddChange"
          />
        </view>
      </view>

      <view v-for="(item, index) in items" :key="item.feeItemId" class="ent-card">
        <view class="ent-item-title">
          <text class="ent-item-name">{{ item.itemName }}</text>
          <view class="item-head-right">
            <text class="ent-tag blue">{{ billingModeLabel(item.billingMode) }}</text>
            <text class="remove-link" @click="removeItem(index)">移除</text>
          </view>
        </view>

        <view class="field-grid">
          <view class="field-cell">
            <text class="ent-form-label">收费月数</text>
            <input
              v-model="item.billableMonths"
              class="ent-input"
              type="digit"
              placeholder="留空按学期默认"
              placeholder-class="input-placeholder"
            />
          </view>
          <view class="field-cell">
            <text class="ent-form-label">月标准</text>
            <input
              v-model="item.monthlyStandard"
              class="ent-input"
              type="digit"
              placeholder="留空按价目表"
              placeholder-class="input-placeholder"
            />
          </view>
          <view class="field-cell">
            <text class="ent-form-label">日标准</text>
            <input
              v-model="item.dailyRate"
              class="ent-input"
              type="digit"
              placeholder="留空按价目表"
              placeholder-class="input-placeholder"
            />
          </view>
          <view class="field-cell">
            <text class="ent-form-label">上期结余</text>
            <input
              v-model="item.openingBalance"
              class="ent-input"
              type="text"
              placeholder="正数预收，负数欠费"
              placeholder-class="input-placeholder"
            />
          </view>
          <view class="field-cell">
            <text class="ent-form-label">其他抵扣</text>
            <input
              v-model="item.otherDeduction"
              class="ent-input"
              type="text"
              placeholder="冲减应收"
              placeholder-class="input-placeholder"
            />
          </view>
        </view>

        <view class="ent-form-item">
          <text class="ent-form-label">备注</text>
          <input
            v-model="item.remark"
            class="ent-input"
            type="text"
            maxlength="500"
            placeholder="选填"
            placeholder-class="input-placeholder"
          />
        </view>
      </view>

      <view class="ent-footer">
        <view class="ent-btn primary" :class="{ disabled: saving }" @click="save">
          {{ saving ? '保存中…' : '保存全部修改' }}
        </view>
      </view>
    </template>
  </view>
</template>

<style scoped>
.input-placeholder {
  color: #9ca3af;
}

.item-head-right {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.remove-link {
  font-size: 24rpx;
  color: #dc2626;
}

.field-grid {
  display: flex;
  flex-wrap: wrap;
  margin: 0 -8rpx;
}

.field-cell {
  width: 50%;
  padding: 0 8rpx;
  box-sizing: border-box;
  margin-bottom: 18rpx;
}

.ent-footer {
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
}
</style>
