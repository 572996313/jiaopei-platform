<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { takePendingRoute } from '@/utils/auth-session'
import { isApiError } from '@/utils/request'

const authStore = useAuthStore()
const username = ref('')
const password = ref('')
const submitting = ref(false)

onLoad(() => {
  if (authStore.isLoggedIn()) {
    uni.reLaunch({ url: landingUrl() })
  }
})

/** 企业账号登录后直接落在工作台；学生/家长仍是首页 */
function landingUrl() {
  return takePendingRoute() || (authStore.isEnterprise() ? '/pages/workbench/index' : '/pages/index/index')
}

async function handleLogin() {
  if (submitting.value) {
    return
  }
  if (!username.value.trim()) {
    uni.showToast({ title: '请输入登录账号', icon: 'none' })
    return
  }
  if (!password.value) {
    uni.showToast({ title: '请输入密码', icon: 'none' })
    return
  }
  submitting.value = true
  uni.showLoading({ title: '登录中…' })
  try {
    await authStore.loginByPassword(username.value, password.value)
    uni.hideLoading()
    uni.reLaunch({ url: landingUrl() })
  } catch (error) {
    uni.hideLoading()
    const message = isApiError(error)
      ? error.message
      : (error as Error)?.message || '登录失败，请检查账号密码'
    uni.showToast({ title: message, icon: 'none', duration: 2600 })
  } finally {
    submitting.value = false
  }
}

function backToWechat() {
  uni.reLaunch({ url: '/pages/login/index' })
}
</script>

<template>
  <view class="ent-login-page">
    <view class="login-head">
      <text class="login-title">企业用户登录</text>
      <text class="login-desc">使用管理后台的账号密码登录，进入企业工作台</text>
    </view>

    <view class="login-card">
      <view class="ent-form-item">
        <text class="ent-form-label">登录账号</text>
        <input
          v-model="username"
          class="ent-input"
          type="text"
          maxlength="64"
          placeholder="请输入账号"
          placeholder-class="input-placeholder"
        />
      </view>
      <view class="ent-form-item">
        <text class="ent-form-label">密码</text>
        <input
          v-model="password"
          class="ent-input"
          :password="true"
          maxlength="64"
          placeholder="请输入密码"
          placeholder-class="input-placeholder"
          @confirm="handleLogin"
        />
      </view>
      <button class="ent-btn primary login-btn" :disabled="submitting" @click="handleLogin">
        {{ submitting ? '登录中…' : '登录' }}
      </button>
      <view class="login-tip">
        <text>首次登录若提示需要修改初始密码，请先到管理后台完成改密。</text>
      </view>
    </view>

    <view class="back-row" @click="backToWechat">
      <text class="back-link">‹ 返回微信一键登录</text>
    </view>
  </view>
</template>

<style scoped>
.ent-login-page {
  min-height: 100vh;
  padding: 80rpx 40rpx 60rpx;
  box-sizing: border-box;
  background: linear-gradient(160deg, #eef4ff 0%, #f5f6fa 42%, #f5f6fa 100%);
}

.login-head {
  margin-bottom: 40rpx;
}

.login-title {
  display: block;
  font-size: 46rpx;
  font-weight: 600;
  color: #111827;
}

.login-desc {
  display: block;
  margin-top: 16rpx;
  font-size: 26rpx;
  color: #6b7280;
  line-height: 1.6;
}

.login-card {
  padding: 36rpx 32rpx;
  border-radius: 24rpx;
  background: #ffffff;
  box-shadow: 0 8rpx 32rpx rgba(17, 24, 39, 0.06);
}

.input-placeholder {
  color: #9ca3af;
}

.login-btn {
  width: 100%;
  min-height: 88rpx;
  margin-top: 8rpx;
  font-size: 30rpx;
}

.login-btn[disabled] {
  opacity: 0.6;
}

.login-tip {
  margin-top: 20rpx;
  font-size: 23rpx;
  color: #9ca3af;
  line-height: 1.6;
}

.back-row {
  margin-top: 40rpx;
  text-align: center;
}

.back-link {
  font-size: 27rpx;
  color: #2563eb;
}
</style>
