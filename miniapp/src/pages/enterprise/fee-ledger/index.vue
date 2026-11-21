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

const filterOpen = ref(false)
const filters = ref({
  termId: '',
  classId: '',
  status: '',
  keyword: '',
})
// 筛选面板先改草稿，点「查询」才生效，避免边填边查
const draft = ref({ termId: '', classId: '', status: '' })
const termLabel = ref('')
const classLabel = ref('')
const draftTermLabel = ref('')
const draftClassLabel = ref('')

const terms = ref<TermRecord[]>([])
const statusOptions = [
  { value: '', label: '全部' },
  { value: 'active', label: '在收' },
  { value: 'settled', label: '已结清' },
]
const statusIndex = ref(0)
const draftStatusIndex = ref(0)
const statusText = computed(() => statusOptions[statusIndex.value]?.label ?? '全部')
const activeFilterCount = computed(
  () =>
    (filters.value.termId ? 1 : 0)
    + (filters.value.classId ? 1 : 0)
    + (filters.value.status ? 1 : 0),
)

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
  // 班级下拉跟着筛选面板里已选的学期走；面板没开时按已生效的学期收窄
  const classes = await fetchClassOptions({
    keyword: keyword.trim() || undefined,
    termId: (filterOpen.value ? draft.value.termId : filters.value.termId) || undefined,
  })
  return classes.map((clazz) => ({
    value: clazz.id,
    label: clazz.className,
    sub: clazz.classCode || undefined,
  }))
}

function onDraftTermChange(option: PickerOption | null) {
  draft.value.termId = option?.value || ''
  draftTermLabel.value = option?.label || ''
  // 换学期后原班级可能不属于新学期，顺手清掉，避免两个条件互相打架
  draft.value.classId = ''
  draftClassLabel.value = ''
}

function onDraftClassChange(option: PickerOption | null) {
  draft.value.classId = option?.value || ''
  draftClassLabel.value = option?.label || ''
}

function toggleFilterPanel() {
  if (filterOpen.value) {
    filterOpen.value = false
    return
  }
  draft.value = {
    termId: filters.value.termId,
    classId: filters.value.classId,
    status: filters.value.status,
  }
  draftTermLabel.value = termLabel.value
  draftClassLabel.value = classLabel.value
  const index = statusOptions.findIndex((item) => item.value === filters.value.status)
  draftStatusIndex.value = index >= 0 ? index : 0
  filterOpen.value = true
}

function applyFilters() {
  filters.value = {
    ...filters.value,
    termId: draft.value.termId,
    classId: draft.value.classId,
    status: statusOptions[draftStatusIndex.value]?.value ?? '',
  }
  termLabel.value = draftTermLabel.value
  classLabel.value = draftClassLabel.value
  statusIndex.value = draftStatusIndex.value
  filterOpen.value = false
  reload()
}

function resetFilters() {
  filters.value = { termId: '', classId: '', status: '', keyword: '' }
  draft.value = { termId: '', classId: '', status: '' }
  termLabel.value = ''
  classLabel.value = ''
  draftTermLabel.value = ''
  draftClassLabel.value = ''
  statusIndex.value = 0
  draftStatusIndex.value = 0
  filterOpen.value = false
  reload()
}

function clearKeyword() {
  filters.value.keyword = ''
  reload()
}

function clearTerm() {
  filters.value.termId = ''
  termLabel.value = ''
  draft.value.termId = ''
  draftTermLabel.value = ''
  reload()
}

function clearClass() {
  filters.value.classId = ''
  classLabel.value = ''
  draft.value.classId = ''
  draftClassLabel.value = ''
  reload()
}

function clearStatus() {
  filters.value.status = ''
  statusIndex.value = 0
  draft.value.status = ''
  draftStatusIndex.value = 0
  reload()
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

function goDetail(id: string) {
  uni.navigateTo({ url: `/pages/enterprise/fee-ledger/detail?id=${id}` })
}

function goCreate(mode: 'single' | 'generate') {
  uni.navigateTo({ url: `/pages/enterprise/fee-ledger/create?mode=${mode}` })
}

/** 新增入口收进右下角悬浮按钮，两种建账方式用系统菜单分开 */
function openCreateSheet() {
  uni.showActionSheet({
    itemList: ['新增单个台账', '按班批量建账'],
    success: (result) => {
      if (result.tapIndex === 1) {
        goCreate('generate')
      } else {
        goCreate('single')
      }
    },
  })
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
  <view class="ent-page list-page">
    <view class="list-toolbar">
      <view class="toolbar-search">
        <uni-icons type="search" size="18" color="#9ca3af" />
        <input
          v-model="filters.keyword"
          class="toolbar-search-input"
          type="text"
          confirm-type="search"
          placeholder="搜索学号 / 姓名 / 班级"
          placeholder-class="input-placeholder"
          @confirm="reload"
        />
        <uni-icons
          v-if="filters.keyword"
          type="clear"
          size="18"
          color="#c4c9d1"
          class="toolbar-clear"
          @click="clearKeyword"
        />
      </view>
      <view class="toolbar-btn" :class="{ active: activeFilterCount > 0 }" @click="toggleFilterPanel">
        <uni-icons type="tune" size="18" :color="activeFilterCount > 0 ? '#2563eb' : '#4b5563'" />
        <text>筛选</text>
        <text v-if="activeFilterCount > 0" class="toolbar-badge">{{ activeFilterCount }}</text>
      </view>
    </view>

    <view v-if="activeFilterCount > 0" class="chip-row">
      <view v-if="filters.termId" class="chip" @click="clearTerm">学期：{{ termLabel || '-' }} ×</view>
      <view v-if="filters.classId" class="chip" @click="clearClass">班级：{{ classLabel || '-' }} ×</view>
      <view v-if="filters.status" class="chip" @click="clearStatus">状态：{{ statusText }} ×</view>
      <text class="chip-clear" @click="resetFilters">清空</text>
    </view>

    <view v-if="filterOpen" class="filter-panel">
      <view class="filter-grid">
        <view class="filter-field">
          <text class="filter-label">学期</text>
          <PickerField
            v-model="draft.termId"
            :label="draftTermLabel"
            title="选择学期"
            placeholder="全部学期"
            :clearable="true"
            :loader="loadTermOptions"
            @change="onDraftTermChange"
          />
        </view>
        <view class="filter-field">
          <text class="filter-label">班级</text>
          <PickerField
            v-model="draft.classId"
            :label="draftClassLabel"
            title="选择班级"
            placeholder="全部班级"
            :clearable="true"
            :loader="loadClassOptions"
            @change="onDraftClassChange"
          />
        </view>
      </view>

      <view class="filter-field filter-field-block">
        <text class="filter-label">状态</text>
        <view class="status-chips">
          <view
            v-for="(option, index) in statusOptions"
            :key="option.value"
            class="status-chip"
            :class="{ active: draftStatusIndex === index }"
            @click="draftStatusIndex = index"
          >
            {{ option.label }}
          </view>
        </view>
      </view>

      <view class="filter-actions">
        <view class="ent-btn small" @click="resetFilters">重置</view>
        <view class="ent-btn primary small" @click="applyFilters">查询</view>
      </view>
    </view>

    <view class="list-head">
      <text class="list-title">收费台账</text>
      <text class="list-count">{{ total }} 条</text>
    </view>

    <view class="ent-card ledger-card">
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

    <view class="fab" @click="openCreateSheet">
      <uni-icons type="plusempty" size="30" color="#ffffff" />
    </view>
  </view>
</template>

<style scoped>
.input-placeholder {
  color: #9ca3af;
}

.ledger-card {
  padding-top: 8rpx;
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
