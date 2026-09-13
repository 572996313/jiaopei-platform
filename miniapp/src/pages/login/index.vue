<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { isApiError } from '@/utils/request'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const loading = ref(false)

onShow(() => {
  // 已登录用户从分享/扫码等入口回到小程序时,直接进入首页
  if (authStore.isLoggedIn) {
    uni.reLaunch({ url: '/pages/index/index' })
  }
})

async function handleWechatLogin() {
  if (loading.value) {
    return
  }
  loading.value = true
  uni.showLoading({ title: '登录中…' })
  try {
    const bound = await authStore.loginByWechat()
    uni.hideLoading()
    if (bound) {
      uni.reLaunch({ url: '/pages/index/index' })
    } else {
      // 首次使用,引导绑定系统账号(仅需一次)
      uni.navigateTo({ url: '/pages/bind/index' })
    }
  } catch (error) {
    uni.hideLoading()
    const message = isApiError(error)
      ? error.message
      : (error as Error)?.message || '登录失败,请重试'
    uni.showToast({ title: message, icon: 'none' })
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <view class="login-page">
    <view class="hero">
      <view class="logo">教培</view>
      <text class="title">教培学堂</text>
      <text class="subtitle">家长/学生服务端</text>
    </view>

    <view class="actions">
      <button class="login-btn" :disabled="loading" @click="handleWechatLogin">微信一键登录</button>
      <text class="hint">首次使用需绑定系统账号,绑定后微信免密登录</text>
    </view>
  </view>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #2563eb 0%, #3b82f6 42%, #f5f6fa 42%);
  display: flex;
  flex-direction: column;
}

.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 180rpx 0 120rpx;
  color: #ffffff;
}

.logo {
  width: 160rpx;
  height: 160rpx;
  border-radius: 40rpx;
  background: rgba(255, 255, 255, 0.18);
  border: 2rpx solid rgba(255, 255, 255, 0.35);
  font-size: 60rpx;
  font-weight: 600;
  line-height: 160rpx;
  text-align: center;
  margin-bottom: 32rpx;
}

.title {
  font-size: 44rpx;
  font-weight: 600;
}

.subtitle {
  margin-top: 12rpx;
  font-size: 26rpx;
  opacity: 0.85;
}

.actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 80rpx;
}

.login-btn {
  width: 100%;
  height: 96rpx;
  line-height: 96rpx;
  border-radius: 48rpx;
  background: #2563eb;
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 500;
}

.login-btn[disabled] {
  opacity: 0.6;
  color: #ffffff;
  background: #2563eb;
}

.hint {
  margin-top: 28rpx;
  font-size: 24rpx;
  color: #9ca3af;
}
</style>
