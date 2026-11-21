<script setup lang="ts">
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { fetchHomeOverview, type HomeOverview } from '@/api/miniapp'
import {
  fetchFeeLedgerPage,
  ledgerStatusLabel,
  moneyText,
  type FeeLedgerRowView,
} from '@/api/enterprise'
import { useBranding } from '@/composables/use-branding'
import LessonRow from '@/components/LessonRow.vue'

const authStore = useAuthStore()
// 名称与登录页同源(系统参数 brand.name);分享/扫码直达时没经过登录页,这里补发一次
const { brandName, loadBranding } = useBranding()
loadBranding()

const displayName = computed(
  () => authStore.userInfo?.displayName || authStore.userInfo?.username || '',
)
const isStudent = computed(() => authStore.userInfo?.roles?.includes('MINIAPP_STUDENT') ?? false)
// 企业端=管理后台账号(portal=admin);学生/家长端不渲染任何收费入口
const isEnterprise = computed(() => authStore.isEnterprise())
// 冷启动时 /auth/me 还没回来,先按会话里记住的端判断,避免先闪一眼家长首页
const portalResolved = ref(Boolean(authStore.currentPortal()))

// 企业端首页概览:只取台账数量与最近在收台账,不改任何数据
const ledgerSummary = ref<{ total: number; activeTotal: number; settledTotal: number } | null>(null)
const recentLedgers = ref<FeeLedgerRowView[]>([])
const recentLoading = ref(false)
const recentError = ref('')

const overview = ref<HomeOverview | null>(null)
// 首屏还没拉到数据时不能显示"今天没有课",那是把"不知道"说成结论
const loaded = computed(() => overview.value !== null)
const bound = computed(() => overview.value?.bound === true)
const todayLessons = computed(() => overview.value?.todayLessons ?? [])

/** 一屏最多给三节,多了引导去完整课表,免得首页变成课表副本 */
const MAX_TODAY_ROWS = 3
const visibleTodayLessons = computed(() => todayLessons.value.slice(0, MAX_TODAY_ROWS))
const moreTodayCount = computed(() => Math.max(0, todayLessons.value.length - MAX_TODAY_ROWS))

const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const dateLine = computed(() => {
  const now = new Date()
  return `${now.getMonth() + 1}月${now.getDate()}日 ${WEEKDAYS[now.getDay()]}`
})

const greetSubLine = computed(() => {
  if (!loaded.value || !bound.value) {
    return `欢迎来到${brandName.value}`
  }
  const count = todayLessons.value.length
  return `${dateLine.value} · ${count > 0 ? `今日 ${count} 节课` : '今天没有课'}`
})

/** 企业端问候语只报台账数量;没读到就只说收费概览,不编数字 */
const enterpriseSubLine = computed(() => {
  const summary = ledgerSummary.value
  if (!summary) {
    return '收费概览'
  }
  return `共 ${summary.total} 份台账 · ${summary.activeTotal} 份在收`
})

/**
 * 卡片底部那行补充信息,四态按优先级取一条:
 * 有下一节就报时间;否则报本周量;本周空且有人没分班就说清为什么空。
 */
const hintLine = computed(() => {
  const data = overview.value
  if (!loaded.value || !bound.value || data === null) {
    return ''
  }
  const next = data.nextLesson ?? null
  if (next) {
    return `下一节:${whenText(next.lessonDate)} ${next.startTime.slice(0, 5)} ${next.courseName || '课程'}`
  }
  if (data.weekLessonCount > 0) {
    return `本周共 ${data.weekLessonCount} 节课`
  }
  const names = data.studentsWithoutClass ?? []
  if (names.length > 0) {
    return `${names.join('、')}还没有分班,分班后这里才会有课表`
  }
  return '本周暂无排课'
})

/** 只区分今天/明天,更远的一律给月日——首页不需要完整日历语义 */
function whenText(date: string) {
  const today = new Date()
  const target = new Date(date + 'T00:00:00')
  const diffDays = Math.round((target.getTime() - new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime()) / 86400000)
  if (diffDays === 0) {
    return '今天'
  }
  if (diffDays === 1) {
    return '明天'
  }
  return `${target.getMonth() + 1}月${target.getDate()}日`
}

function loadHome() {
  fetchHomeOverview()
    .then((data) => {
      overview.value = data
    })
    .catch(() => {
      overview.value = null
    })
}

/** 企业端概览:数量 + 最近在收台账,都是只读;读不到就不显示数字,不编造结论 */
async function loadEnterpriseHome() {
  recentLoading.value = true
  recentError.value = ''
  try {
    const [all, active, settled, recent] = await Promise.all([
      fetchFeeLedgerPage({ page: 1, pageSize: 1 }),
      fetchFeeLedgerPage({ page: 1, pageSize: 1, status: 'active' }),
      fetchFeeLedgerPage({ page: 1, pageSize: 1, status: 'settled' }),
      fetchFeeLedgerPage({ page: 1, pageSize: 5, status: 'active' }),
    ])
    ledgerSummary.value = {
      total: all.total,
      activeTotal: active.total,
      settledTotal: settled.total,
    }
    recentLedgers.value = recent.records
  } catch (error) {
    ledgerSummary.value = null
    recentLedgers.value = []
    recentError.value = (error as Error)?.message || '收费概览加载失败，下拉重试'
  } finally {
    recentLoading.value = false
  }
}

function goWorkbench() {
  uni.switchTab({ url: '/pages/workbench/index' })
}

function openLedger(item: FeeLedgerRowView) {
  uni.navigateTo({ url: `/pages/enterprise/fee-ledger/detail?id=${item.id}` })
}

function enterHome() {
  if (isEnterprise.value) {
    void loadEnterpriseHome()
    return
  }
  loadHome()
}

onShow(() => {
  if (!authStore.isLoggedIn()) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  // 先拿实时端信息再决定加载哪套首页:企业账号不能去调 /api/miniapp/**
  authStore
    .refreshUserInfo()
    .catch(() => {
      // 静默重登也救不回来时,请求层已清掉本地会话并跳回登录页
    })
    .finally(() => {
      portalResolved.value = true
      enterHome()
    })
})

onPullDownRefresh(async () => {
  if (isEnterprise.value) {
    try {
      await loadEnterpriseHome()
    } finally {
      uni.stopPullDownRefresh()
    }
    return
  }
  loadHome()
  uni.stopPullDownRefresh()
})

function goTimetable() {
  uni.navigateTo({ url: '/pages/timetable/index' })
}

// 学生要在"绑定已有"和"新建档案"之间选,那两条按钮在「我的」页,不在这里重复一遍
function goBind() {
  if (isStudent.value) {
    uni.switchTab({ url: '/pages/mine/index' })
    return
  }
  uni.navigateTo({ url: '/pages/bind-student/index' })
}
</script>

<template>
  <view v-if="!portalResolved" class="home-page">
    <view class="boot-tip">
      <text>加载中…</text>
    </view>
  </view>

  <!-- 企业端(管理后台账号)：只读收费概览，操作入口在「工作台」 -->
  <view v-else-if="isEnterprise" class="home-page">
    <view class="greeting">
      <text class="greet-line">你好,{{ displayName || '企业用户' }}</text>
      <text class="sub-line">{{ enterpriseSubLine }}</text>
    </view>

    <view class="today-card">
      <view class="card-head">
        <text class="card-title">收费概览</text>
        <text class="card-link" @click="goWorkbench">进入工作台 ›</text>
      </view>
      <view class="ent-summary-grid">
        <view class="ent-summary-item">
          <view class="ent-summary-box">
            <text class="ent-summary-label">台账总数</text>
            <text class="ent-summary-value">{{ ledgerSummary ? ledgerSummary.total : '-' }}</text>
          </view>
        </view>
        <view class="ent-summary-item">
          <view class="ent-summary-box">
            <text class="ent-summary-label">在收台账</text>
            <text class="ent-summary-value">{{ ledgerSummary ? ledgerSummary.activeTotal : '-' }}</text>
          </view>
        </view>
        <view class="ent-summary-item">
          <view class="ent-summary-box">
            <text class="ent-summary-label">已结清</text>
            <text class="ent-summary-value">{{ ledgerSummary ? ledgerSummary.settledTotal : '-' }}</text>
          </view>
        </view>
      </view>
      <view class="ent-tip">
        首页只看汇总与在收台账；新增、收款、维护学员和班级请到「工作台」。
      </view>
    </view>

    <view class="section-title">在收台账</view>
    <view v-if="recentLoading" class="ent-card ent-empty">加载中…</view>
    <view v-else-if="recentError" class="ent-card ent-empty">{{ recentError }}</view>
    <view v-else-if="recentLedgers.length === 0" class="ent-card ent-empty">
      当前没有在收台账，需要建账时去「工作台」。
    </view>
    <view
      v-for="row in recentLedgers"
      :key="row.id"
      class="ent-card"
      hover-class="ent-card-hover"
      @click="openLedger(row)"
    >
      <view class="ent-item-title">
        <text class="ent-item-name">{{ row.studentName || '未命名学员' }}</text>
        <text class="ent-tag blue">{{ ledgerStatusLabel(row.status) }}</text>
      </view>
      <view class="ent-row">
        <text class="ent-label">班级</text>
        <text class="ent-value">{{ row.className || '-' }}</text>
      </view>
      <view class="ent-row">
        <text class="ent-label">应收 / 未收</text>
        <text class="ent-value">
          <text class="ent-amount">{{ moneyText(row.receivable) }}</text>
          /
          <text class="ent-amount warn">{{ moneyText(row.unpaid) }}</text>
        </text>
      </view>
      <view class="ent-btn-row right">
        <view class="ent-btn ghost small">查看台账 ›</view>
      </view>
    </view>

    <view class="ent-tip">
      台账金额由服务端实时计算；首页不放入口，避免看数据和改数据混在一起。
    </view>
  </view>

  <view v-else class="home-page">
    <view class="greeting">
      <text class="greet-line">你好,{{ displayName || '同学家长' }}</text>
      <text class="sub-line">{{ greetSubLine }}</text>
    </view>

    <view class="today-card">
      <view class="card-head">
        <text class="card-title">今日课程</text>
        <text v-if="bound" class="card-link" @click="goTimetable">完整课表 ›</text>
      </view>

      <view v-if="!loaded" class="card-tip">
        <text>加载中…</text>
      </view>
      <view v-else-if="!bound" class="card-tip">
        <text class="tip-text">还没有绑定学员档案</text>
        <view class="tip-btn" @click="goBind">{{ isStudent ? '绑定或创建档案' : '添加孩子' }}</view>
      </view>
      <template v-else>
        <view v-if="todayLessons.length > 0" class="lesson-list">
          <LessonRow
            v-for="(item, index) in visibleTodayLessons"
            :key="index"
            :lesson="item"
            :show-student="!isStudent"
          />
          <view v-if="moreTodayCount > 0" class="more-link" @click="goTimetable">
            还有 {{ moreTodayCount }} 节,看完整课表 ›
          </view>
        </view>
        <view v-else class="card-tip">
          <text class="tip-text">今天没有课</text>
        </view>
      </template>

      <view v-if="loaded && bound" class="card-hint">
        <text>{{ hintLine }}</text>
      </view>
    </view>

    <view class="ent-tip">
      首页只看今天的安排；完整课表、公告、档案和同班名单等需要主动点的服务在「工作台」。
    </view>
  </view>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
  padding: 32rpx 32rpx 48rpx;
  box-sizing: border-box;
}

.boot-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 200rpx;
  font-size: 26rpx;
  color: #9ca3af;
}

.greeting {
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  border-radius: 24rpx;
  padding: 44rpx 40rpx;
  color: #ffffff;
}

.greet-line {
  display: block;
  font-size: 38rpx;
  font-weight: 600;
}

.sub-line {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  opacity: 0.85;
}

.today-card {
  margin-top: 28rpx;
  background: #ffffff;
  border-radius: 24rpx;
  padding: 28rpx 32rpx;
  box-shadow: 0 2rpx 16rpx rgba(17, 24, 39, 0.05);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #111827;
}

.card-link {
  font-size: 24rpx;
  color: #2563eb;
}

.lesson-list {
  margin-top: 8rpx;
}

.card-tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40rpx 0 24rpx;
}

.tip-text {
  font-size: 26rpx;
  color: #9ca3af;
}

.tip-btn {
  margin-top: 24rpx;
  padding: 12rpx 40rpx;
  border-radius: 999rpx;
  font-size: 26rpx;
  color: #2563eb;
  background: #eff6ff;
}

.more-link {
  padding: 20rpx 0 4rpx;
  text-align: center;
  font-size: 24rpx;
  color: #2563eb;
}

.card-hint {
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #f3f4f6;
  font-size: 24rpx;
  color: #6b7280;
}

.section-title {
  margin: 40rpx 0 24rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: #111827;
}
</style>
