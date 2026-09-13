<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const displayName = computed(
  () => authStore.userInfo?.displayName || authStore.userInfo?.username || '未登录',
)
const avatarChar = computed(() => displayName.value.slice(0, 1) || '客')
const roleText = computed(() => {
  const roles = authStore.userInfo?.roles ?? []
  return roles.length > 0 ? roles.join('、') : '暂无角色'
})

function handleLogout() {
  uni.showModal({
    title: '提示',
    content: '确定退出登录吗?',
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      try {
        await authStore.logout()
      } finally {
        uni.reLaunch({ url: '/pages/login/index' })
      }
    },
  })
}
</script>

<template>
  <view class="mine-page">
    <view class="user-card">
      <view class="avatar">{{ avatarChar }}</view>
      <view class="user-meta">
        <text class="name">{{ displayName }}</text>
        <text class="account">@{{ authStore.userInfo?.username || '-' }}</text>
      </view>
    </view>

    <view class="info-card">
      <view class="info-row">
        <text class="info-label">角色</text>
        <text class="info-value">{{ roleText }}</text>
      </view>
      <view class="info-row">
        <text class="info-label">所属机构</text>
        <text class="info-value">{{ authStore.userInfo?.institutionId || '-' }}</text>
      </view>
    </view>

    <button class="logout-btn" @click="handleLogout">退出登录</button>
  </view>
</template>

<style scoped>
.mine-page {
  min-height: 100vh;
  padding: 32rpx;
  box-sizing: border-box;
}

.user-card {
  display: flex;
  align-items: center;
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  border-radius: 24rpx;
  padding: 44rpx 40rpx;
  color: #ffffff;
}

.avatar {
  width: 110rpx;
  height: 110rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  font-size: 48rpx;
  text-align: center;
  line-height: 110rpx;
  margin-right: 28rpx;
}

.user-meta {
  display: flex;
  flex-direction: column;
}

.name {
  font-size: 36rpx;
  font-weight: 600;
}

.account {
  margin-top: 8rpx;
  font-size: 26rpx;
  opacity: 0.85;
}

.info-card {
  margin-top: 24rpx;
  background: #ffffff;
  border-radius: 20rpx;
  padding: 8rpx 32rpx;
  box-shadow: 0 2rpx 16rpx rgba(17, 24, 39, 0.05);
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 30rpx 0;
  border-bottom: 1rpx solid #f3f4f6;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  font-size: 28rpx;
  color: #6b7280;
}

.info-value {
  font-size: 28rpx;
  color: #111827;
}

.logout-btn {
  margin-top: 48rpx;
  width: 100%;
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 46rpx;
  background: #ffffff;
  color: #dc2626;
  font-size: 32rpx;
  border: 1rpx solid #fecaca;
}

.logout-btn::after {
  border: none;
}
</style>
