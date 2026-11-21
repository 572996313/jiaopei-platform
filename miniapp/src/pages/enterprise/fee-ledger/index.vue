<script setup lang="ts">
import { computed, ref } from 'vue'
import { onReachBottom, onShow } from '@dcloudio/uni-app'
import {
  fetchClassOptions,
  fetchFeeLedgerPage,
  fetchTerms,
  ledgerStatusLabel,
  moneyText,
  type FeeLedgerRowView,
  type TermRecord,
} from '@/api/enterprise'
import type { PickerOption } from '@/components/picker-types'
import PickerField from '@/components/PickerField.vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const filters = ref({
  termId: '',
  classId: '',
  status: '',
  keyword: '',
})

const terms = ref<TermRecord[]>([])
const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'active', label: '在收' },
  { value: 'settled', label: '已结清' },
]
const statusIndex = ref(0)
const statusLabel = computed(() => statusOptions[statusIndex.value]?.label ?? '全部状态')

const records = ref<FeeLedgerRowView[]>([])
const page = ref(1)
const pageSize = 20
const total = ref(0)
const loading = ref(false)
const finished = ref(false)

async function loadTermOptions(keyword: string): Promise<PickerOption[]> {
  if (terms.value.length === 0) {
    terms.value = await fetchTerms()
  }
  const kw = keyword.trim()
  return terms.value
    .filter((term) => !kw || term.termName.includes(kw))
    .map((term) => ({
      value: term.id,
      label: term.termName,
      sub:
        term.startDate && term.endDate
          ? `${term.startDate} ~ ${term.endDate}`
          : '未维护起止日期，建账前需到管理后台补齐',
    }))
}

async function loadClassOptions(keyword: string): Promise<PickerOption[]> {
  const classes = await fetchClassOptions({
    keyword: keyword.trim() || undefined,
    termId: filters.value.termId || undefined,
  })
  return classes.map((clazz) => ({
    value: clazz.id,
    label: clazz.className,
    sub: clazz.classCode || undefined,
  }))
}

function onStatusChange(event: { detail: { value: string | number } }) {
  statusIndex.value = Number(event.detail.value)
  filters.value.status = statusOptions[statusIndex.value]?.value ?? ''
}

async function load(reset: boolean) {
  if (loading.value) {
    return
  }
  if (reset) {
    page.value = 1
    finished.value = false
  }
  loading.value = true
  try {
    const result = await fetchFeeLedgerPage({
      page: page.value,
      pageSize,
      termId: filters.value.termId || undefined,
      classId: filters.value.classId || undefined,
      status: filters.value.status || undefined,
      keyword: filters.value.keyword.trim() || undefined,
    })
    total.value = result.total
    records.value = reset ? result.records : records.value.concat(result.records)
    finished.value = records.value.length >= result.total
  } catch (error) {
    if (reset) {
      records.value = []
      total.value = 0
    }
    uni.showToast({ title: (error as Error)?.message || '台账加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function reload() {
  load(true)
}

function resetFilters() {
  filters.value = { termId: '', classId: '', status: '', keyword: '' }
  statusIndex.value = 0
  reload()
}

function goDetail(id: string) {
  uni.navigateTo({ url: `/pages/enterprise/fee-ledger/detail?id=${id}` })
}

function goCreate(mode: 'single' | 'generate') {
  uni.navigateTo({ url: `/pages/enterprise/fee-ledger/create?mode=${mode}` })
}

function unsettledClass(row: FeeLedgerRowView) {
  return Number(row.unpaid ?? 0) > 0 ? 'warn' : ''
}

onShow(() => {
  if (!authStore.isLoggedIn()) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  // 从详情/建账返回时金额可能已变，回到列表一律重拉第一页
  reload()
})

onReachBottom(() => {
  if (!loading.value && !finished.value) {
    page.value += 1
    load(false)
  }
})
</script>

<template>
  <view class="ent-page">
    <view class="ent-card">
      <view class="filter-item">
        <text class="ent-form-label">学期</text>
        <PickerField
          v-model="filters.termId"
          title="选择学期"
          placeholder="全部学期"
          :clearable="true"
          :loader="loadTermOptions"
        />
      </view>
      <view class="filter-item">
        <text class="ent-form-label">班级</text>
        <PickerField
          v-model="filters.classId"
          title="选择班级"
          placeholder="全部班级"
          :clearable="true"
          :loader="loadClassOptions"
        />
      </view>
      <view class="filter-item">
        <text class="ent-form-label">状态</text>
        <picker mode="selector" :range="statusOptions" range-key="label" :value="statusIndex" @change="onStatusChange">
          <view class="ent-picker" :class="{ 'is-placeholder': !filters.status }">{{ statusLabel }}</view>
        </picker>
      </view>
      <view class="filter-item">
        <text class="ent-form-label">关键字</text>
        <input
          v-model="filters.keyword"
          class="ent-input"
          type="text"
          confirm-type="search"
          placeholder="学号 / 姓名 / 班级"
          placeholder-class="input-placeholder"
          @confirm="reload"
        />
      </view>
      <view class="ent-btn-row right">
        <view class="ent-btn small" @click="resetFilters">重置</view>
        <view class="ent-btn primary small" @click="reload">查询</view>
      </view>
    </view>

    <view class="ent-card">
      <view class="ent-card-title">
        <text>收费台账</text>
        <text class="ent-link">{{ total }} 条</text>
      </view>
      <view class="ent-btn-row">
        <view class="ent-btn primary small" @click="goCreate('single')">新增台账</view>
        <view class="ent-btn ghost small" @click="goCreate('generate')">按班建账</view>
        <view class="ent-btn small" @click="reload">刷新</view>
      </view>

      <view
        v-for="row in records"
        :key="row.id"
        class="ledger-row"
        @click="goDetail(row.id)"
      >
        <view class="row-top">
          <text class="student-name">{{ row.studentName || '未命名学员' }}</text>
          <text class="ent-tag" :class="row.status === 'settled' ? 'green' : 'blue'">
            {{ ledgerStatusLabel(row.status) }}
          </text>
        </view>
        <view class="row-sub">
          {{ row.studentNo || '-' }} · {{ row.className || '-' }} · {{ row.termName || '-' }}
        </view>
        <view class="row-money">
          <view class="money-cell">
            <text class="money-label">应收</text>
            <text class="money-value">{{ moneyText(row.receivable) }}</text>
          </view>
          <view class="money-cell">
            <text class="money-label">实收</text>
            <text class="money-value">{{ moneyText(row.paid) }}</text>
          </view>
          <view class="money-cell">
            <text class="money-label">未收费</text>
            <text class="money-value" :class="unsettledClass(row)">{{ moneyText(row.unpaid) }}</text>
          </view>
          <view class="money-cell">
            <text class="money-label">结余</text>
            <text class="money-value">{{ moneyText(row.balance) }}</text>
          </view>
        </view>
      </view>

      <view v-if="loading" class="ent-empty">加载中…</view>
      <view v-else-if="records.length === 0" class="ent-empty">没有符合条件的台账</view>
      <view v-else-if="finished" class="ent-empty">没有更多了</view>
    </view>
  </view>
</template>

<style scoped>
.input-placeholder {
  color: #9ca3af;
}

.filter-item {
  margin-bottom: 18rpx;
}

.ledger-row {
  padding: 20rpx 0;
  border-top: 1rpx solid #f3f4f6;
}

.row-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.student-name {
  font-size: 30rpx;
  font-weight: 600;
  color: #111827;
}

.row-sub {
  margin-top: 8rpx;
  font-size: 23rpx;
  color: #9ca3af;
}

.row-money {
  display: flex;
  margin-top: 14rpx;
}

.money-cell {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.money-label {
  font-size: 21rpx;
  color: #9ca3af;
}

.money-value {
  margin-top: 4rpx;
  font-size: 26rpx;
  font-weight: 600;
  color: #111827;
  font-variant-numeric: tabular-nums;
}

.money-value.warn {
  color: #dc2626;
}
</style>
