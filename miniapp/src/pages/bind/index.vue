<script setup lang="ts">
import { ref } from 'vue'
import { isApiError } from '@/utils/request'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const username = ref('')
const password = ref('')
const submitting = ref(false)

async function handleBind() {
  if (!username.value.trim() || !password.value) {
    uni.showToast({ title: '请输入账号和密码', icon: 'none' })
    return
  }
  if (submitting.value) {
    return
  }
  submitting.value = true
  uni.showLoading({ title: '绑定中…' })
  try {
    await authStore.bindAccount(username.value.trim(), password.value)
    uni.hideLoading()
    uni.showToast({ title: '绑定成功', icon: 'success' })
    setTimeout(() => {
      uni.reLaunch({ url: '/pages/index/index' })
    }, 600)
  } catch (error) {
    uni.hideLoading()
    const message = isApiError(error)
      ? error.message
      : (error as Error)?.message || '绑定失败,请重试'
    uni.showToast({ title: message, icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="bind-page">
    <view class="header">
      <text class="title">绑定系统账号</text>
      <text class="desc">输入平台分配的账号密码,完成一次绑定后即可微信免密登录</text>
    </view>

    <view class="form-card">
      <view class="form-item">
        <text class="label">账号</text>
        <input
          v-model="username"
          class="input"
          type="text"
          placeholder="请输入账号"
          placeholder-class="placeholder"
        />
      </view>
      <view class="form-item">
        <text class="label">密码</text>
        <input
          v-model="password"
          class="input"
          password
          placeholder="请输入密码"
          placeholder-class="placeholder"
        />
      </view>
      <button class="submit-btn" :disabled="submitting" @click="handleBind">绑定</button>
    </view>
  </view>
</template>

<style scoped>
.bind-page {
  min-height: 100vh;
  padding: 60rpx 48rpx;
  box-sizing: border-box;
}

.header {
  margin-bottom: 48rpx;
}

.title {
  display: block;
  font-size: 44rpx;
  font-weight: 600;
  color: #111827;
}

.desc {
  display: block;
  margin-top: 16rpx;
  font-size: 26rpx;
  color: #6b7280;
  line-height: 1.6;
}

.form-card {
  background: #ffffff;
  border-radius: 24rpx;
  padding: 40rpx 36rpx;
  box-shadow: 0 4rpx 24rpx rgba(17, 24, 39, 0.06);
}

.form-item {
  margin-bottom: 36rpx;
}

.label {
  display: block;
  font-size: 28rpx;
  color: #374151;
  margin-bottom: 14rpx;
}

.input {
  height: 88rpx;
  padding: 0 24rpx;
  border-radius: 12rpx;
  background: #f5f6fa;
  font-size: 30rpx;
}

.placeholder {
  color: #9ca3af;
}

.submit-btn {
  margin-top: 16rpx;
  width: 100%;
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 46rpx;
  background: #2563eb;
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 500;
}

.submit-btn[disabled] {
  opacity: 0.6;
  color: #ffffff;
  background: #2563eb;
}
</style>
