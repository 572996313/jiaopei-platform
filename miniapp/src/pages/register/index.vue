<script setup lang="ts">
import { ref } from 'vue'
import { isApiError } from '@/utils/request'
import { useAuthStore } from '@/stores/auth'
import type { MiniappRole } from '@/api/auth'

const authStore = useAuthStore()
const role = ref<MiniappRole | null>(null)
const nickname = ref('')
const submitting = ref(false)

const roleOptions: Array<{ value: MiniappRole; icon: string; title: string; desc: string }> = [
  { value: 'STUDENT', icon: 'person-filled', title: '我是学生', desc: '查看自己的课程与考勤' },
  { value: 'PARENT', icon: 'staff-filled', title: '我是家长', desc: '关注孩子的课程与考勤' },
]

function selectRole(value: MiniappRole) {
  role.value = value
}

async function handleRegister() {
  if (!role.value) {
    uni.showToast({ title: '请先选择你的身份', icon: 'none' })
    return
  }
  if (submitting.value) {
    return
  }
  submitting.value = true
  uni.showLoading({ title: '注册中…' })
  try {
    await authStore.registerAccount(role.value, nickname.value)
    uni.hideLoading()
    uni.showToast({ title: '注册成功', icon: 'success' })
    setTimeout(() => {
      uni.reLaunch({ url: '/pages/index/index' })
    }, 600)
  } catch (error) {
    uni.hideLoading()
    const message = isApiError(error)
      ? error.message
      : (error as Error)?.message || '注册失败,请重试'
    uni.showToast({ title: message, icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="register-page">
    <view class="header">
      <text class="title">选择你的身份</text>
      <text class="desc">选择后将自动注册进入系统,身份注册后不可自行更改</text>
    </view>

    <view class="role-grid">
      <view
        v-for="item in roleOptions"
        :key="item.value"
        class="role-card"
        :class="{ active: role === item.value }"
        @click="selectRole(item.value)"
      >
        <uni-icons :type="item.icon" size="34" :color="role === item.value ? '#2563eb' : '#9ca3af'" />
        <text class="role-title">{{ item.title }}</text>
        <text class="role-desc">{{ item.desc }}</text>
        <view v-if="role === item.value" class="check-dot" />
      </view>
    </view>

    <view class="nickname-card">
      <text class="label">昵称(选填)</text>
      <input
        v-model="nickname"
        class="input"
        type="nickname"
        maxlength="32"
        placeholder="不填则显示为“微信用户”"
        placeholder-class="placeholder"
      />
    </view>

    <button class="submit-btn" :disabled="submitting" @click="handleRegister">
      {{ role ? '确认注册' : '请先选择身份' }}
    </button>
  </view>
</template>

<style scoped>
.register-page {
  min-height: 100vh;
  padding: 60rpx 48rpx;
  box-sizing: border-box;
}

.header {
  margin-bottom: 44rpx;
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

.role-grid {
  display: flex;
  justify-content: space-between;
  margin-bottom: 32rpx;
}

.role-card {
  position: relative;
  width: 48.5%;
  box-sizing: border-box;
  background: #ffffff;
  border: 3rpx solid #e5e7eb;
  border-radius: 24rpx;
  padding: 40rpx 32rpx;
  display: flex;
  flex-direction: column;
  transition: border-color 0.15s;
}

.role-card.active {
  border-color: #2563eb;
  background: #eff6ff;
}

.role-title {
  margin-top: 24rpx;
  font-size: 34rpx;
  font-weight: 600;
  color: #111827;
}

.role-desc {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #6b7280;
}

.check-dot {
  position: absolute;
  top: 20rpx;
  right: 20rpx;
  width: 24rpx;
  height: 24rpx;
  border-radius: 50%;
  background: #2563eb;
}

.nickname-card {
  background: #ffffff;
  border-radius: 20rpx;
  padding: 32rpx;
  box-shadow: 0 2rpx 16rpx rgba(17, 24, 39, 0.05);
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
  margin-top: 48rpx;
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
