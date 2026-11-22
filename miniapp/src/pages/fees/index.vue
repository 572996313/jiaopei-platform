<script setup lang="ts">
import { onLoad, onShow } from '@dcloudio/uni-app'
import { ref, watch } from 'vue'
import {
  fetchFeePaymentMethodOptions,
  fetchStudentFees,
  type StudentFeeOverview,
} from '@/api/miniapp'
import { useBoundStudent } from '@/composables/use-bound-student'
import StudentSwitcher from '@/components/StudentSwitcher.vue'

const { students, studentId, ready, showSwitcher, init, switchTo } = useBoundStudent()

const overview = ref<StudentFeeOverview | null>(null)
const loading = ref(false)
const error = ref('')
const paymentMethodLabels = ref<Record<string, string>>({})

// 收款方式是系统字典(fee_payment_method),与 PC 同一份;拉不到就回显原始值
fetchFeePaymentMethodOptions()
  .then((items) => {
    paymentMethodLabels.value = Object.fromEntries(
      items.map((item) => [item.itemValue, item.itemName]),
    )
  })
  .catch(() => undefined)

function paymentMethodText(value?: string | null) {
  if (!value) {
    return '-'
  }
  return paymentMethodLabels.value[value] || value
}

function moneyText(value?: number | null) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '-'
  }
  return Number(value).toFixed(2)
}

function loadFees() {
  if (!studentId.value) {
    overview.value = null
    return
  }
  loading.value = true
  error.value = ''
  fetchStudentFees(studentId.value)
    .then((data) => {
      overview.value = data
    })
    .catch((err) => {
      overview.value = null
      error.value = (err as Error)?.message || '费用信息加载失败,请稍后重试'
    })
    .finally(() => {
      loading.value = false
    })
}

onLoad((options) => {
  init(decodeURIComponent(options?.studentId || ''))
})

// 页签缓存着这一页,机构补交费/新建成账后回来要看到,所以每次显示都重拉
onShow(() => {
  if (ready.value && studentId.value) {
    loadFees()
  }
})

watch(studentId, (id) => {
  if (id) {
    loadFees()
  }
})
</script>

<template>
  <view class="fees-page">
    <StudentSwitcher
      v-if="showSwitcher"
      :students="students"
      :active-id="studentId"
      @select="switchTo"
    />

    <view v-if="!ready" class="page-tip">加载中…</view>
    <view v-else-if="!studentId" class="page-tip">
      <text>还没有可查看的学员,先在「我的」页绑定或创建学员档案</text>
    </view>

    <template v-else>
      <view class="summary-card">
        <view class="summary-item">
          <text class="summary-label">应收</text>
          <text class="summary-value">{{ moneyText(overview?.receivable) }}</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">已收</text>
          <text class="summary-value">{{ moneyText(overview?.paid) }}</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">未收</text>
          <text class="summary-value warn">{{ moneyText(overview?.unpaid) }}</text>
        </view>
      </view>

      <view v-if="loading" class="page-tip">加载中…</view>
      <view v-else-if="error" class="page-tip">{{ error }}</view>

      <template v-else-if="overview">
        <view v-if="overview.ledgers.length === 0" class="empty-card">
          <text>机构还没有为这名学员建立收费台账</text>
        </view>

        <view v-for="ledger in overview.ledgers" :key="ledger.id" class="ledger-card">
          <view class="ledger-head">
            <text class="ledger-title">{{ ledger.className || '未分班' }}</text>
            <text class="ledger-tag" :class="ledger.status === 'settled' ? 'settled' : 'active'">
              {{ ledger.status === 'settled' ? '已结清' : '在收' }}
            </text>
          </view>
          <text class="ledger-sub">{{ ledger.termName || '未关联学期' }}</text>

          <view class="ledger-amounts">
            <view class="amount-item">
              <text class="amount-label">应收</text>
              <text class="amount-value">{{ moneyText(ledger.receivable) }}</text>
            </view>
            <view class="amount-item">
              <text class="amount-label">已收</text>
              <text class="amount-value">{{ moneyText(ledger.paid) }}</text>
            </view>
            <view class="amount-item">
              <text class="amount-label">未收</text>
              <text class="amount-value warn">{{ moneyText(ledger.unpaid) }}</text>
            </view>
          </view>

          <view v-if="ledger.items.length > 0" class="item-list">
            <view v-for="(item, index) in ledger.items" :key="index" class="item-row">
              <text class="item-name">{{ item.itemName || '收费项目' }}</text>
              <text class="item-amount">{{ moneyText(item.receivable) }}</text>
            </view>
          </view>
        </view>

        <view v-if="overview.payments.length > 0" class="payment-card">
          <view class="card-title">收款记录</view>
          <view v-for="payment in overview.payments" :key="payment.id" class="payment-row">
            <view class="payment-meta">
              <text class="payment-date">{{ payment.paidOn || '-' }}</text>
              <text class="payment-method">{{ paymentMethodText(payment.paymentMethod) }}</text>
            </view>
            <text class="payment-amount">{{ moneyText(payment.amount) }}</text>
          </view>
        </view>

        <text class="tip">金额由机构服务端实时计算,如有疑问请联系机构财务核对。</text>
      </template>
    </template>
  </view>
</template>

<style scoped>
.fees-page {
  min-height: 100vh;
  padding: 32rpx;
  box-sizing: border-box;
}

.page-tip {
  padding: 80rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: #9ca3af;
}

.summary-card {
  display: flex;
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  border-radius: 24rpx;
  padding: 36rpx 20rpx;
  color: #ffffff;
}

.summary-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.summary-label {
  font-size: 24rpx;
  opacity: 0.8;
}

.summary-value {
  margin-top: 12rpx;
  font-size: 36rpx;
  font-weight: 600;
}

.summary-value.warn {
  color: #fde68a;
}

.ledger-card,
.payment-card,
.empty-card {
  margin-top: 24rpx;
  background: #ffffff;
  border-radius: 20rpx;
  padding: 28rpx 32rpx;
  box-shadow: 0 2rpx 16rpx rgba(17, 24, 39, 0.05);
}

.empty-card {
  text-align: center;
  font-size: 26rpx;
  color: #9ca3af;
}

.ledger-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ledger-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #111827;
}

.ledger-tag {
  padding: 6rpx 18rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
}

.ledger-tag.active {
  color: #2563eb;
  background: #eff6ff;
}

.ledger-tag.settled {
  color: #059669;
  background: #ecfdf5;
}

.ledger-sub {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #9ca3af;
}

.ledger-amounts {
  display: flex;
  margin-top: 24rpx;
  padding: 20rpx 0;
  border-top: 1rpx solid #f3f4f6;
  border-bottom: 1rpx solid #f3f4f6;
}

.amount-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.amount-label {
  font-size: 24rpx;
  color: #6b7280;
}

.amount-value {
  margin-top: 8rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: #111827;
}

.amount-value.warn {
  color: #dc2626;
}

.item-list {
  margin-top: 8rpx;
}

.item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f9fafb;
}

.item-row:last-child {
  border-bottom: none;
}

.item-name {
  font-size: 27rpx;
  color: #374151;
}

.item-amount {
  font-size: 27rpx;
  color: #111827;
}

.card-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #111827;
  margin-bottom: 8rpx;
}

.payment-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22rpx 0;
  border-bottom: 1rpx solid #f9fafb;
}

.payment-row:last-child {
  border-bottom: none;
}

.payment-meta {
  display: flex;
  flex-direction: column;
}

.payment-date {
  font-size: 27rpx;
  color: #111827;
}

.payment-method {
  margin-top: 6rpx;
  font-size: 23rpx;
  color: #9ca3af;
}

.payment-amount {
  font-size: 30rpx;
  font-weight: 600;
  color: #111827;
}

.tip {
  display: block;
  margin-top: 28rpx;
  font-size: 23rpx;
  color: #9ca3af;
  line-height: 1.6;
}
</style>
