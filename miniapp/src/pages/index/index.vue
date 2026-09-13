<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const displayName = computed(
  () => authStore.userInfo?.displayName || authStore.userInfo?.username || '',
)

const features = [
  { icon: 'calendar', title: '课程表', desc: '查看孩子的上课安排' },
  { icon: 'checkbox-filled', title: '考勤记录', desc: '出勤与请假情况' },
  { icon: 'notification-filled', title: '消息通知', desc: '调课、停课提醒' },
  { icon: 'wallet-filled', title: '课耗明细', desc: '课时使用与余额' },
]

onShow(() => {
  if (!authStore.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  authStore.refreshUserInfo().catch(() => {
    // 401 时请求层已统一跳回登录页
  })
})
</script>

<template>
  <view class="home-page">
    <view class="greeting">
      <text class="greet-line">你好,{{ displayName || '同学家长' }}</text>
      <text class="sub-line">欢迎来到教培学堂</text>
    </view>

    <view class="section-title">常用服务</view>
    <view class="feature-grid">
      <view v-for="item in features" :key="item.title" class="feature-card">
        <uni-icons :type="item.icon" size="26" color="#2563eb" />
        <text class="feature-title">{{ item.title }}</text>
        <text class="feature-desc">{{ item.desc }}</text>
        <text class="feature-tag">敬请期待</text>
      </view>
    </view>

    <view class="todo-note">骨架版本,业务页面将在后续迭代中逐步开放</view>
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

.todo-note {
  margin-top: 16rpx;
  text-align: center;
  font-size: 24rpx;
  color: #9ca3af;
}
</style>
