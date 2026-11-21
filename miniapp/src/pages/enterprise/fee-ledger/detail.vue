<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import {
  billingModeLabel,
  deleteFeeLedger,
  deleteFeePayment,
  fetchDictOptions,
  fetchFeeLedger,
  ledgerStatusLabel,
  moneyText,
  monthText,
  overrideFeeAttendance,
  overrideFeeMonthly,
  recalculateFees,
  resolveFeeLedgerPrices,
  revertFeeAttendance,
  revertFeeMonthly,
  syncFeeAttendance,
  updateFeeLedger,
  type DictOptionItem,
  type FeeAttendanceView,
  type FeeLedgerDetailView,
  type FeeMonthlyView,
  type FeePaymentView,
} from '@/api/enterprise'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const ledgerId = ref('')
const detail = ref<FeeLedgerDetailView | null>(null)
const loading = ref(false)
const tab = ref<'items' | 'payments' | 'attendance' | 'monthly' | 'settings'>('items')
const paymentMethods = ref<DictOptionItem[]>([])

const statusOptions = [
  { value: 'active', label: '在收' },
  { value: 'settled', label: '已结清' },
]
const statusIndex = ref(0)
const settingsForm = ref({ status: 'active', remark: '' })

// ---------------------------------------------------------------- 派生数据

const items = computed(() => detail.value?.items ?? [])
const payments = computed(() => detail.value?.payments ?? [])
const attendance = computed(() => detail.value?.attendance ?? [])
const monthly = computed(() => detail.value?.monthly ?? [])

function itemNameOf(ledgerItemId: string) {
  return items.value.find((item) => item.id === ledgerItemId)?.itemName || `项目 #${ledgerItemId}`
}

function paymentMethodText(value?: string | null) {
  if (!value) {
    return '-'
  }
  return paymentMethods.value.find((item) => item.itemValue === value)?.itemName || value
}

function priceSourceText(value?: string | null) {
  if (value === 'PRICE') {
    return '价目'
  }
  if (value === 'STUDENT') {
    return '学员价'
  }
  if (value === 'MANUAL') {
    return '手工'
  }
  return value || '-'
}

function numberText(value?: number | null) {
  if (value === null || value === undefined) {
    return '-'
  }
  return String(value)
}

// ---------------------------------------------------------------- 加载

onLoad((options) => {
  ledgerId.value = options?.id || ''
  fetchDictOptions('fee_payment_method')
    .then((list) => {
      paymentMethods.value = list
    })
    .catch(() => {
      paymentMethods.value = []
    })
})

onShow(() => {
  if (!authStore.isLoggedIn()) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  // 从编辑/收款页返回时数据已变，一律重拉详情
  loadDetail()
})

async function loadDetail() {
  if (!ledgerId.value) {
    return
  }
  loading.value = true
  try {
    const data = await fetchFeeLedger(ledgerId.value)
    detail.value = data
    settingsForm.value = { status: data.status || 'active', remark: data.remark || '' }
    statusIndex.value = statusOptions.findIndex((item) => item.value === settingsForm.value.status)
    if (statusIndex.value < 0) {
      statusIndex.value = 0
    }
  } catch (error) {
    uni.showToast({ title: (error as Error)?.message || '台账加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function refresh() {
  loadDetail()
}

// ---------------------------------------------------------------- 导航

function goEdit() {
  uni.navigateTo({ url: `/pages/enterprise/fee-ledger/edit?id=${ledgerId.value}` })
}

function goCreatePayment() {
  uni.navigateTo({ url: `/pages/enterprise/fee-ledger/payment?ledgerId=${ledgerId.value}` })
}

function goEditPayment(payment: FeePaymentView) {
  uni.navigateTo({
    url: `/pages/enterprise/fee-ledger/payment?ledgerId=${ledgerId.value}&paymentId=${payment.id}`,
  })
}

// ---------------------------------------------------------------- 收款

function confirmDeletePayment(payment: FeePaymentView) {
  uni.showModal({
    title: '删除收款',
    content: `确定删除这笔 ${moneyText(payment.amount)} 元的收款吗？删除后实收与结余会立即变化。`,
    confirmColor: '#dc2626',
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      try {
        await deleteFeePayment(payment.id)
        uni.showToast({ title: '已删除', icon: 'success' })
        loadDetail()
      } catch (error) {
        uni.showToast({ title: (error as Error)?.message || '删除失败', icon: 'none' })
      }
    },
  })
}

// ---------------------------------------------------------------- 出勤

const attendancePopup = ref<any>(null)
const attendanceForm = ref({ billingMonth: '', displayMonth: '', attendanceDays: '', reason: '' })

function openAttendanceOverride(row: FeeAttendanceView) {
  attendanceForm.value = {
    billingMonth: row.billingMonth,
    displayMonth: monthText(row.billingMonth),
    attendanceDays: row.attendanceDays === null || row.attendanceDays === undefined ? '' : String(row.attendanceDays),
    reason: '',
  }
  attendancePopup.value?.open()
}

async function submitAttendanceOverride() {
  const raw = attendanceForm.value.attendanceDays.trim()
  if (raw === '') {
    uni.showToast({ title: '请填写出勤天数，0 也要显式填', icon: 'none' })
    return
  }
  const days = Number(raw)
  if (Number.isNaN(days) || days < 0) {
    uni.showToast({ title: '请填写正确的出勤天数', icon: 'none' })
    return
  }
  uni.showLoading({ title: '保存中…' })
  try {
    await overrideFeeAttendance(ledgerId.value, attendanceForm.value.billingMonth, {
      attendanceDays: days,
      overrideReason: attendanceForm.value.reason.trim() || undefined,
    })
    uni.hideLoading()
    attendancePopup.value?.close()
    uni.showToast({ title: '出勤已覆盖', icon: 'success' })
    loadDetail()
  } catch (error) {
    uni.hideLoading()
    uni.showToast({ title: (error as Error)?.message || '保存失败', icon: 'none' })
  }
}

function confirmRevertAttendance(row: FeeAttendanceView) {
  uni.showModal({
    title: '撤销出勤覆盖',
    content: `把 ${monthText(row.billingMonth)} 的出勤天数恢复为考勤汇总值？`,
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      try {
        await revertFeeAttendance(ledgerId.value, row.billingMonth)
        uni.showToast({ title: '已撤销', icon: 'success' })
        loadDetail()
      } catch (error) {
        uni.showToast({ title: (error as Error)?.message || '撤销失败', icon: 'none' })
      }
    },
  })
}

function syncAttendance() {
  uni.showModal({
    title: '从考勤同步',
    content: '按点名记录汇总本月出勤天数（出勤+迟到，同一天只算一次）；手工覆盖过的月份保留手工值。',
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      uni.showLoading({ title: '同步中…' })
      try {
        const syncResult = await syncFeeAttendance({ ledgerId: ledgerId.value })
        uni.hideLoading()
        uni.showModal({
          title: '同步完成',
          content: `共 ${syncResult.ledgerCount} 份台账，同步 ${syncResult.syncedCount} 个月份，保留手工覆盖 ${syncResult.keptOverrideCount} 个月份。`,
          showCancel: false,
        })
        loadDetail()
      } catch (error) {
        uni.hideLoading()
        uni.showToast({ title: (error as Error)?.message || '同步失败', icon: 'none' })
      }
    },
  })
}

// ---------------------------------------------------------------- 逐月发生额

const monthlyPopup = ref<any>(null)
const monthlyForm = ref({
  ledgerItemId: '',
  billingMonth: '',
  displayMonth: '',
  itemName: '',
  occurrenceAmount: '',
  monthFactor: '',
  reason: '',
})

function openMonthlyOverride(row: FeeMonthlyView) {
  monthlyForm.value = {
    ledgerItemId: row.ledgerItemId,
    billingMonth: row.billingMonth,
    displayMonth: `${row.itemName || '项目'} · ${monthText(row.billingMonth)}`,
    itemName: row.itemName || '',
    occurrenceAmount:
      row.occurrenceAmount === null || row.occurrenceAmount === undefined
        ? ''
        : String(row.occurrenceAmount),
    monthFactor: row.monthFactor === null || row.monthFactor === undefined ? '' : String(row.monthFactor),
    reason: '',
  }
  monthlyPopup.value?.open()
}

async function submitMonthlyOverride() {
  const raw = monthlyForm.value.occurrenceAmount.trim()
  if (raw === '') {
    uni.showToast({ title: '请填写发生额，0 也要显式填', icon: 'none' })
    return
  }
  const amount = Number(raw)
  if (Number.isNaN(amount)) {
    uni.showToast({ title: '请填写正确的发生额', icon: 'none' })
    return
  }
  const factor = monthlyForm.value.monthFactor.trim()
  const factorValue = factor === '' ? null : Number(factor)
  if (factorValue !== null && Number.isNaN(factorValue)) {
    uni.showToast({ title: '月份系数格式不正确', icon: 'none' })
    return
  }
  uni.showLoading({ title: '保存中…' })
  try {
    await overrideFeeMonthly(monthlyForm.value.ledgerItemId, monthlyForm.value.billingMonth, {
      occurrenceAmount: amount,
      monthFactor: factorValue,
      overrideReason: monthlyForm.value.reason.trim() || undefined,
    })
    uni.hideLoading()
    monthlyPopup.value?.close()
    uni.showToast({ title: '发生额已覆盖', icon: 'success' })
    loadDetail()
  } catch (error) {
    uni.hideLoading()
    uni.showToast({ title: (error as Error)?.message || '保存失败', icon: 'none' })
  }
}

function confirmRevertMonthly(row: FeeMonthlyView) {
  uni.showModal({
    title: '撤销发生额覆盖',
    content: `把 ${row.itemName || '该项目'} ${monthText(row.billingMonth)} 的发生额恢复为引擎自动值？`,
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      try {
        await revertFeeMonthly(row.ledgerItemId, row.billingMonth)
        uni.showToast({ title: '已撤销', icon: 'success' })
        loadDetail()
      } catch (error) {
        uni.showToast({ title: (error as Error)?.message || '撤销失败', icon: 'none' })
      }
    },
  })
}

function recalculate() {
  uni.showModal({
    title: '按当前出勤重算',
    content: '刷新未手工覆盖的月份发生额；手工覆盖过的行不会变。',
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      uni.showLoading({ title: '重算中…' })
      try {
        await recalculateFees({ ledgerId: ledgerId.value })
        uni.hideLoading()
        uni.showToast({ title: '重算完成', icon: 'success' })
        loadDetail()
      } catch (error) {
        uni.hideLoading()
        uni.showToast({ title: (error as Error)?.message || '重算失败', icon: 'none' })
      }
    },
  })
}

/** 后端只提供单行撤销，批量恢复在客户端逐行调用（与 PC「全部恢复自动」同口径） */
function revertAllMonthly() {
  const rows = monthly.value.filter((row) => row.manualOverride)
  if (rows.length === 0) {
    uni.showToast({ title: '没有手工覆盖的月份', icon: 'none' })
    return
  }
  uni.showModal({
    title: '全部恢复自动',
    content: `将撤销 ${rows.length} 行手工覆盖，恢复为引擎自动值。确定继续吗？`,
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      uni.showLoading({ title: '恢复中…' })
      let failed = 0
      for (const row of rows) {
        try {
          await revertFeeMonthly(row.ledgerItemId, row.billingMonth)
        } catch {
          failed += 1
        }
      }
      uni.hideLoading()
      if (failed > 0) {
        uni.showToast({ title: `有 ${failed} 行恢复失败，请重试`, icon: 'none', duration: 2600 })
      } else {
        uni.showToast({ title: '已全部恢复自动', icon: 'success' })
      }
      loadDetail()
    },
  })
}

function resolvePrices() {
  uni.showModal({
    title: '重新取价',
    content: '按当前价目表覆盖明细行的标准并重算；手工覆盖过的发生额不受影响。',
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      uni.showLoading({ title: '取价中…' })
      try {
        await resolveFeeLedgerPrices(ledgerId.value)
        uni.hideLoading()
        uni.showToast({ title: '已按价目表重新取价', icon: 'success' })
        loadDetail()
      } catch (error) {
        uni.hideLoading()
        uni.showToast({ title: (error as Error)?.message || '取价失败', icon: 'none' })
      }
    },
  })
}

// ---------------------------------------------------------------- 台账设置

function onStatusChange(event: { detail: { value: string | number } }) {
  statusIndex.value = Number(event.detail.value)
  settingsForm.value.status = statusOptions[statusIndex.value]?.value ?? 'active'
}

async function saveSettings() {
  uni.showLoading({ title: '保存中…' })
  try {
    await updateFeeLedger(ledgerId.value, {
      status: settingsForm.value.status,
      remark: settingsForm.value.remark,
    })
    uni.hideLoading()
    uni.showToast({ title: '已保存', icon: 'success' })
    loadDetail()
  } catch (error) {
    uni.hideLoading()
    uni.showToast({ title: (error as Error)?.message || '保存失败', icon: 'none' })
  }
}

function confirmDeleteLedger() {
  uni.showModal({
    title: '删除台账',
    content: '已有收款流水的台账不能删除；删除后明细、出勤与逐月发生额一并删除。确定继续吗？',
    confirmColor: '#dc2626',
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      uni.showLoading({ title: '删除中…' })
      try {
        await deleteFeeLedger(ledgerId.value)
        uni.hideLoading()
        uni.showToast({ title: '台账已删除', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 500)
      } catch (error) {
        uni.hideLoading()
        uni.showToast({ title: (error as Error)?.message || '删除失败', icon: 'none', duration: 2600 })
      }
    },
  })
}
</script>

<template>
  <view class="ent-page">
    <view v-if="loading && !detail" class="ent-empty">加载中…</view>

    <template v-if="detail">
      <!-- 台账头 -->
      <view class="ent-card">
        <view class="head-top">
          <view class="head-main">
            <text class="head-name">{{ detail.studentName || '未命名学员' }}</text>
            <text class="head-sub">
              {{ detail.studentNo || '-' }} · {{ detail.className || '-' }} · {{ detail.termName || '-' }}
            </text>
            <text class="head-sub">{{ detail.campusName || '未关联校区' }}</text>
          </view>
          <text class="ent-tag" :class="detail.status === 'settled' ? 'green' : 'blue'">
            {{ ledgerStatusLabel(detail.status) }}
          </text>
        </view>

        <view class="ent-divider"></view>
        <view class="ent-summary-grid">
          <view class="ent-summary-item">
            <view class="ent-summary-box">
              <text class="ent-summary-label">应收</text>
              <text class="ent-summary-value">{{ moneyText(detail.receivable) }}</text>
            </view>
          </view>
          <view class="ent-summary-item">
            <view class="ent-summary-box">
              <text class="ent-summary-label">实收</text>
              <text class="ent-summary-value">{{ moneyText(detail.paid) }}</text>
            </view>
          </view>
          <view class="ent-summary-item">
            <view class="ent-summary-box">
              <text class="ent-summary-label">未收费</text>
              <text class="ent-summary-value">{{ moneyText(detail.unpaid) }}</text>
            </view>
          </view>
          <view class="ent-summary-item">
            <view class="ent-summary-box">
              <text class="ent-summary-label">合计发生</text>
              <text class="ent-summary-value">{{ moneyText(detail.totalOccurrence) }}</text>
            </view>
          </view>
          <view class="ent-summary-item">
            <view class="ent-summary-box">
              <text class="ent-summary-label">结余(可退)</text>
              <text class="ent-summary-value">{{ moneyText(detail.balance) }}</text>
            </view>
          </view>
          <view class="ent-summary-item">
            <view class="ent-summary-box">
              <text class="ent-summary-label">项目数</text>
              <text class="ent-summary-value">{{ items.length }}</text>
            </view>
          </view>
        </view>
        <view v-if="detail.remark" class="ent-tip">台账备注：{{ detail.remark }}</view>
        <view class="ent-btn-row">
          <view class="ent-btn small" @click="refresh">刷新</view>
          <view class="ent-btn ghost small" @click="goEdit">编辑明细与参数</view>
        </view>
      </view>

      <view class="ent-tabs">
        <view class="ent-tab" :class="{ active: tab === 'items' }" @click="tab = 'items'">明细</view>
        <view class="ent-tab" :class="{ active: tab === 'payments' }" @click="tab = 'payments'">收款</view>
        <view class="ent-tab" :class="{ active: tab === 'attendance' }" @click="tab = 'attendance'">出勤</view>
        <view class="ent-tab" :class="{ active: tab === 'monthly' }" @click="tab = 'monthly'">逐月</view>
        <view class="ent-tab" :class="{ active: tab === 'settings' }" @click="tab = 'settings'">设置</view>
      </view>

      <!-- 明细 -->
      <template v-if="tab === 'items'">
        <view v-if="items.length === 0" class="ent-card ent-empty">该台账还没有收费项目行，去「编辑明细与参数」添加。</view>
        <view v-for="item in items" :key="item.id" class="ent-card">
          <view class="ent-item-title">
            <text class="ent-item-name">{{ item.itemName || '未命名项目' }}</text>
            <text class="ent-tag blue">{{ billingModeLabel(item.billingMode) }}</text>
          </view>
          <view class="ent-row">
            <text class="ent-label">收费月数 × 月标准</text>
            <text class="ent-value ent-amount">{{ numberText(item.billableMonths) }} × {{ moneyText(item.monthlyStandard) }}</text>
          </view>
          <view class="ent-row">
            <text class="ent-label">日标准</text>
            <text class="ent-value ent-amount">{{ moneyText(item.dailyRate) }}</text>
          </view>
          <view class="ent-row">
            <text class="ent-label">上期结余 / 其他抵扣</text>
            <text class="ent-value ent-amount">{{ moneyText(item.openingBalance) }} / {{ moneyText(item.otherDeduction) }}</text>
          </view>
          <view class="ent-divider"></view>
          <view class="ent-row">
            <text class="ent-label">应收</text>
            <text class="ent-value ent-amount">{{ moneyText(item.receivable) }}</text>
          </view>
          <view class="ent-row">
            <text class="ent-label">实收</text>
            <text class="ent-value ent-amount">{{ moneyText(item.paid) }}</text>
          </view>
          <view class="ent-row">
            <text class="ent-label">未收费</text>
            <text class="ent-value ent-amount" :class="{ warn: Number(item.unpaid ?? 0) > 0 }">{{ moneyText(item.unpaid) }}</text>
          </view>
          <view class="ent-row">
            <text class="ent-label">合计发生</text>
            <text class="ent-value ent-amount">{{ moneyText(item.totalOccurrence) }}</text>
          </view>
          <view class="ent-row">
            <text class="ent-label">结余</text>
            <text class="ent-value ent-amount">{{ moneyText(item.balance) }}</text>
          </view>
          <view class="ent-row">
            <text class="ent-label">取价来源</text>
            <text class="ent-value">{{ priceSourceText(item.priceSource) }}</text>
          </view>
          <view v-if="item.remark" class="ent-row">
            <text class="ent-label">备注</text>
            <text class="ent-value">{{ item.remark }}</text>
          </view>
        </view>
      </template>

      <!-- 收款 -->
      <template v-else-if="tab === 'payments'">
        <view class="ent-btn-row">
          <view class="ent-btn primary small" @click="goCreatePayment">登记收款</view>
          <view class="ent-btn small" @click="refresh">刷新</view>
        </view>
        <view v-if="payments.length === 0" class="ent-card ent-empty">还没有收款流水</view>
        <view v-for="payment in payments" :key="payment.id" class="ent-card">
          <view class="ent-item-title">
            <text class="ent-item-name">{{ itemNameOf(payment.ledgerItemId) }}</text>
            <text class="payment-amount">{{ moneyText(payment.amount) }}</text>
          </view>
          <view class="ent-row">
            <text class="ent-label">收款日期</text>
            <text class="ent-value">{{ payment.paidOn || '-' }}</text>
          </view>
          <view class="ent-row">
            <text class="ent-label">收款方式</text>
            <text class="ent-value">{{ paymentMethodText(payment.paymentMethod) }}</text>
          </view>
          <view class="ent-row">
            <text class="ent-label">归属账期</text>
            <text class="ent-value">{{ payment.billingMonth ? monthText(payment.billingMonth) : '-' }}</text>
          </view>
          <view class="ent-row">
            <text class="ent-label">收款单号</text>
            <text class="ent-value">{{ payment.paymentNo || '-' }}</text>
          </view>
          <view v-if="payment.voucherNo" class="ent-row">
            <text class="ent-label">凭证号</text>
            <text class="ent-value">{{ payment.voucherNo }}</text>
          </view>
          <view v-if="payment.remark" class="ent-row">
            <text class="ent-label">备注</text>
            <text class="ent-value">{{ payment.remark }}</text>
          </view>
          <view class="ent-btn-row right">
            <view class="ent-btn small" @click="goEditPayment(payment)">编辑</view>
            <view class="ent-btn danger small" @click="confirmDeletePayment(payment)">删除</view>
          </view>
        </view>
      </template>

      <!-- 出勤 -->
      <template v-else-if="tab === 'attendance'">
        <view class="ent-btn-row">
          <view class="ent-btn primary small" @click="syncAttendance">从考勤同步</view>
          <view class="ent-btn small" @click="refresh">刷新</view>
        </view>
        <view class="ent-tip">
          出勤天数是按天收费项目发生额的唯一来源；按月项目也要出勤大于 0 才有发生额。请假、缺勤不计，同一天多节课只算一天。
        </view>
        <view v-if="attendance.length === 0" class="ent-card ent-empty">该台账没有月度出勤记录</view>
        <view v-for="row in attendance" :key="row.id" class="ent-card">
          <view class="ent-item-title">
            <text class="ent-item-name">{{ monthText(row.billingMonth) }}</text>
            <text class="ent-tag" :class="row.manualOverride ? 'orange' : 'blue'">
              {{ row.manualOverride ? '手工覆盖' : '考勤汇总' }}
            </text>
          </view>
          <view class="ent-row">
            <text class="ent-label">生效出勤天数</text>
            <text class="ent-value ent-amount">{{ numberText(row.attendanceDays) }}</text>
          </view>
          <view class="ent-row">
            <text class="ent-label">考勤汇总天数</text>
            <text class="ent-value ent-amount">{{ numberText(row.autoDays) }}</text>
          </view>
          <view v-if="row.remark" class="ent-row">
            <text class="ent-label">备注</text>
            <text class="ent-value">{{ row.remark }}</text>
          </view>
          <view class="ent-btn-row right">
            <view class="ent-btn small" @click="openAttendanceOverride(row)">改天数</view>
            <view v-if="row.manualOverride" class="ent-btn danger small" @click="confirmRevertAttendance(row)">撤销覆盖</view>
          </view>
        </view>
      </template>

      <!-- 逐月发生额 -->
      <template v-else-if="tab === 'monthly'">
        <view class="ent-btn-row">
          <view class="ent-btn primary small" @click="recalculate">按当前出勤重算</view>
          <view class="ent-btn ghost small" @click="resolvePrices">重新取价</view>
          <view class="ent-btn small" @click="revertAllMonthly">全部恢复自动</view>
          <view class="ent-btn small" @click="refresh">刷新</view>
        </view>
        <view class="ent-tip">
          按月自动金额 = 出勤大于 0 时「月标准 × 月份系数」；按天 = 出勤天数 × 日标准。手工覆盖后重算不会再改这一行。
        </view>
        <view v-if="monthly.length === 0" class="ent-card ent-empty">该台账没有逐月发生额</view>
        <view v-for="row in monthly" :key="row.id" class="ent-card">
          <view class="ent-item-title">
            <text class="ent-item-name">{{ row.itemName || '项目' }} · {{ monthText(row.billingMonth) }}</text>
            <text class="ent-tag" :class="row.manualOverride ? 'orange' : 'blue'">
              {{ row.manualOverride ? '已覆盖' : billingModeLabel(row.billingMode) }}
            </text>
          </view>
          <view class="ent-row">
            <text class="ent-label">月份系数</text>
            <text class="ent-value ent-amount">{{ numberText(row.monthFactor) }}</text>
          </view>
          <view class="ent-row">
            <text class="ent-label">自动金额</text>
            <text class="ent-value ent-amount">{{ moneyText(row.autoAmount) }}</text>
          </view>
          <view class="ent-row">
            <text class="ent-label">生效发生额</text>
            <text class="ent-value ent-amount">{{ moneyText(row.occurrenceAmount) }}</text>
          </view>
          <view v-if="row.remark" class="ent-row">
            <text class="ent-label">备注</text>
            <text class="ent-value">{{ row.remark }}</text>
          </view>
          <view class="ent-btn-row right">
            <view class="ent-btn small" @click="openMonthlyOverride(row)">覆盖</view>
            <view v-if="row.manualOverride" class="ent-btn danger small" @click="confirmRevertMonthly(row)">撤销覆盖</view>
          </view>
        </view>
      </template>

      <!-- 设置 -->
      <template v-else>
        <view class="ent-card">
          <view class="ent-form-item">
            <text class="ent-form-label">台账状态</text>
            <picker mode="selector" :range="statusOptions" range-key="label" :value="statusIndex" @change="onStatusChange">
              <view class="ent-picker">{{ statusOptions[statusIndex]?.label || '在收' }}</view>
            </picker>
          </view>
          <view class="ent-form-item">
            <text class="ent-form-label">台账备注</text>
            <textarea
              v-model="settingsForm.remark"
              class="ent-textarea"
              maxlength="500"
              placeholder="如：按月收、转入说明等"
              placeholder-class="input-placeholder"
            />
          </view>
          <view class="ent-btn-row">
            <view class="ent-btn primary small" @click="saveSettings">保存</view>
          </view>
        </view>

        <view class="ent-card">
          <view class="ent-card-title"><text>危险操作</text></view>
          <view class="ent-tip">已有收款流水的台账不能删除；要停止收费请把状态改为「已结清」。</view>
          <view class="ent-btn-row">
            <view class="ent-btn danger small" @click="confirmDeleteLedger">删除台账</view>
          </view>
        </view>
      </template>
    </template>

    <!-- 出勤覆盖弹窗 -->
    <uni-popup ref="attendancePopup" type="bottom" background-color="#ffffff">
      <view class="dialog">
        <view class="dialog-head">
          <text class="dialog-title">覆盖出勤天数 · {{ attendanceForm.displayMonth }}</text>
          <text class="dialog-close" @click="attendancePopup?.close()">关闭</text>
        </view>
        <view class="dialog-body">
          <view class="ent-form-item">
            <text class="ent-form-label">生效出勤天数</text>
            <input
              v-model="attendanceForm.attendanceDays"
              class="ent-input"
              type="digit"
              placeholder="如 18"
              placeholder-class="input-placeholder"
            />
          </view>
          <view class="ent-form-item">
            <text class="ent-form-label">覆盖原因（选填）</text>
            <input
              v-model="attendanceForm.reason"
              class="ent-input"
              type="text"
              maxlength="200"
              placeholder="如：考勤漏记，按纸质签到补录"
              placeholder-class="input-placeholder"
            />
          </view>
        </view>
        <view class="dialog-footer">
          <view class="ent-btn primary" @click="submitAttendanceOverride">保存覆盖</view>
        </view>
      </view>
    </uni-popup>

    <!-- 发生额覆盖弹窗 -->
    <uni-popup ref="monthlyPopup" type="bottom" background-color="#ffffff">
      <view class="dialog">
        <view class="dialog-head">
          <text class="dialog-title">覆盖发生额 · {{ monthlyForm.displayMonth }}</text>
          <text class="dialog-close" @click="monthlyPopup?.close()">关闭</text>
        </view>
        <view class="dialog-body">
          <view class="ent-form-item">
            <text class="ent-form-label">生效发生额</text>
            <input
              v-model="monthlyForm.occurrenceAmount"
              class="ent-input"
              type="digit"
              placeholder="如 1500.00"
              placeholder-class="input-placeholder"
            />
          </view>
          <view class="ent-form-item">
            <text class="ent-form-label">月份系数（选填，留空保持现值）</text>
            <input
              v-model="monthlyForm.monthFactor"
              class="ent-input"
              type="digit"
              placeholder="如 1.00 / 0.50"
              placeholder-class="input-placeholder"
            />
          </view>
          <view class="ent-form-item">
            <text class="ent-form-label">覆盖原因（选填）</text>
            <input
              v-model="monthlyForm.reason"
              class="ent-input"
              type="text"
              maxlength="200"
              placeholder="如：按协议减免"
              placeholder-class="input-placeholder"
            />
          </view>
        </view>
        <view class="dialog-footer">
          <view class="ent-btn primary" @click="submitMonthlyOverride">保存覆盖</view>
        </view>
      </view>
    </uni-popup>
  </view>
</template>

<style scoped>
.input-placeholder {
  color: #9ca3af;
}

.head-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.head-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.head-name {
  font-size: 34rpx;
  font-weight: 600;
  color: #111827;
}

.head-sub {
  margin-top: 6rpx;
  font-size: 23rpx;
  color: #9ca3af;
}

.payment-amount {
  font-size: 32rpx;
  font-weight: 600;
  color: #2563eb;
  font-variant-numeric: tabular-nums;
}

.dialog {
  display: flex;
  flex-direction: column;
  max-height: 78vh;
  padding-bottom: env(safe-area-inset-bottom);
}

.dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 32rpx 12rpx;
}

.dialog-title {
  flex: 1;
  font-size: 30rpx;
  font-weight: 600;
  color: #111827;
}

.dialog-close {
  margin-left: 20rpx;
  font-size: 26rpx;
  color: #6b7280;
}

.dialog-body {
  padding: 12rpx 32rpx 4rpx;
}

.dialog-footer {
  padding: 16rpx 32rpx 24rpx;
}

.dialog-footer .ent-btn {
  width: 100%;
}
</style>
