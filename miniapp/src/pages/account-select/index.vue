<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { isApiError } from '@/utils/request'
import { takePendingRoute } from '@/utils/auth-session'

const authStore = useAuthStore()
const submittingKey = ref('')
const accounts = authStore.pendingSelection?.accounts ?? []

/** 端标识转中文：账号列表里要让用户一眼分清家长端与企业后台 */
const PORTAL_LABELS: Record<string, string> = {
  admin: '管理后台',
  student: '学生端',
  parent: '家长端',
}

onLoad(() => {
  if (!authStore.pendingSelection) {
    uni.showToast({ title: '选择已过期，请重新登录', icon: 'none' })
    setTimeout(() => uni.reLaunch({ url: '/pages/login/index' }), 900)
  }
})

function subtitle(account: { institutionName: string; portal: string }) {
  return [account.institutionName, PORTAL_LABELS[account.portal] || account.portal]
    .filter(Boolean)
    .join(' · ')
}

async function choose(account: { key: string }) {
  if (submittingKey.value) {
    return
  }
  submittingKey.value = account.key
  try {
    const auth = await authStore.loginBySelection(account.key)
    uni.reLaunch({
      url: takePendingRoute() || (auth.portal === 'admin' ? '/pages/workbench/index' : '/pages/index/index'),
    })
  } catch (error) {
    const message = isApiError(error) ? error.message : (error as Error)?.message || '登录失败，请重试'
    uni.showToast({ title: message, icon: 'none' })
  } finally {
    submittingKey.value = ''
  }
}
</script>

<template>
  <view class="select-page">
    <view class="select-head">
      <text class="select-title">选择登录账号</text>
      <text class="select-desc">当前微信绑定了多个账号，请选择本次要使用的身份</text>
    </view>

    <view class="account-list">
      <view
        v-for="account in accounts"
        :key="account.key"
        class="account-card"
        hover-class="account-card-hover"
        @click="choose(account)"
      >
        <view class="account-avatar">{{ (account.displayName || '?').slice(0, 1) }}</view>
        <view class="account-meta">
          <text class="account-name">{{ account.displayName }}</text>
          <text class="account-sub">{{ subtitle(account) }}</text>
        </view>
        <text class="account-arrow">{{ submittingKey === account.key ? '…' : '›' }}</text>
      </view>
    </view>

    <text class="select-tip">选错了不影响绑定关系，下次登录仍可重新选择。</text>
  </view>
</template>

<style scoped>
.select-page {
  min-height: 100vh;
  padding: 48rpx 32rpx;
  box-sizing: border-box;
  background: #f5f7fb;
}

.select-head {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-bottom: 36rpx;
}

.select-title {
  font-size: 40rpx;
  font-weight: 600;
  color: #0f1e35;
}

.select-desc {
  font-size: 26rpx;
  color: #64748b;
  line-height: 1.6;
}

.account-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.account-card {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 28rpx;
  background: #ffffff;
  border-radius: 20rpx;
  box-shadow: 0 6rpx 24rpx rgba(15, 30, 53, 0.06);
}

.account-card-hover {
  background: #eef2ff;
}

.account-avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #2563eb, #38bdf8);
  color: #ffffff;
  font-size: 36rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.account-meta {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  min-width: 0;
}

.account-name {
  font-size: 32rpx;
  font-weight: 600;
  color: #0f1e35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-sub {
  font-size: 24rpx;
  color: #64748b;
}

.account-arrow {
  font-size: 36rpx;
  color: #94a3b8;
}

.select-tip {
  display: block;
  margin-top: 36rpx;
  font-size: 24rpx;
  color: #94a3b8;
  text-align: center;
}
</style>
