<script setup lang="ts">
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { fetchUnreadCount } from '@/api/announcement'
import { fetchHomeOverview, type HomeOverview } from '@/api/miniapp'
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

// path 为空的卡片还没做，统一显示"敬请期待"
const features = [
  { icon: 'calendar', title: '课程表', desc: '查看孩子的上课安排', path: '/pages/timetable/index' },
  { icon: 'checkbox-filled', title: '考勤记录', desc: '出勤与请假情况', path: '' },
  { icon: 'notification-filled', title: '消息通知', desc: '学校发布的公告', path: '/pages/messages/index' },
  { icon: 'wallet-filled', title: '课耗明细', desc: '课时使用与余额', path: '' },
]

const unreadCount = ref(0)
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

onShow(() => {
  if (!authStore.isLoggedIn()) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  authStore.refreshUserInfo().catch(() => {
    // 静默重登也救不回来时,请求层已清掉本地会话并跳回登录页
  })
  // 页签是缓存的,PC 侧入班/排课后只能在这里重拉;不依赖"每次进入都重新挂载"
  loadHome()
  // 收件箱从详情页返回时会话仍有效，这里只补一次未读数
  fetchUnreadCount()
    .then((count) => {
      unreadCount.value = count
    })
    .catch(() => {
      unreadCount.value = 0
    })
})

onPullDownRefresh(async () => {
  loadHome()
  try {
    unreadCount.value = await fetchUnreadCount()
  } catch {
    unreadCount.value = 0
  } finally {
    uni.stopPullDownRefresh()
  }
})

function openFeature(path: string) {
  if (!path) {
    uni.showToast({ title: '功能开发中', icon: 'none' })
    return
  }
  uni.navigateTo({ url: path })
}

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
  <view class="home-page">
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

    <view class="section-title">常用服务</view>
    <view class="feature-grid">
      <view
        v-for="item in features"
        :key="item.title"
        class="feature-card"
        @click="openFeature(item.path)"
      >
        <uni-icons :type="item.icon" size="26" color="#2563eb" />
        <text v-if="item.path && unreadCount > 0" class="badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</text>
        <text class="feature-title">{{ item.title }}</text>
        <text class="feature-desc">{{ item.desc }}</text>
        <text v-if="!item.path" class="feature-tag">敬请期待</text>
      </view>
    </view>
  </view>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
  padding: 32rpx 32rpx 48rpx;
  box-sizing: border-box;
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

.feature-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
}

.feature-card {
  position: relative;
  width: 48.5%;
  box-sizing: border-box;
  background: #ffffff;
  border-radius: 20rpx;
  padding: 32rpx 28rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 16rpx rgba(17, 24, 39, 0.05);
  display: flex;
  flex-direction: column;
}

.feature-title {
  margin-top: 20rpx;
  font-size: 30rpx;
  font-weight: 500;
  color: #111827;
}

.feature-desc {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #6b7280;
}

.feature-tag {
  position: absolute;
  top: 24rpx;
  right: 24rpx;
  font-size: 20rpx;
  color: #9ca3af;
  background: #f3f4f6;
  border-radius: 999rpx;
  padding: 4rpx 14rpx;
}

.badge {
  position: absolute;
  top: 24rpx;
  right: 24rpx;
  min-width: 34rpx;
  height: 34rpx;
  line-height: 34rpx;
  padding: 0 8rpx;
  text-align: center;
  font-size: 20rpx;
  color: #ffffff;
  background: #dc2626;
  border-radius: 999rpx;
}
</style>
