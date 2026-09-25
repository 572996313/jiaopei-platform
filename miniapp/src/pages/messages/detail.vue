<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import {
  audienceLabel,
  fetchAnnouncementTypeLabels,
  openMyAnnouncement,
  type MyAnnouncement,
} from '@/api/announcement'

const announcement = ref<MyAnnouncement | null>(null)
const loadFailed = ref(false)
const typeLabels = ref<Record<string, string>>({})

onLoad((options) => {
  const id = options?.id || ''
  if (!id) {
    loadFailed.value = true
    return
  }
  fetchAnnouncementTypeLabels().then((labels) => {
    typeLabels.value = labels
  })
  // 这个请求同时就是"打读"动作：服务端首次打开时记录已读时间
  openMyAnnouncement(id)
    .then((result) => {
      announcement.value = result
    })
    .catch(() => {
      loadFailed.value = true
      uni.showToast({ title: '公告不存在或已撤回', icon: 'none' })
    })
})

function typeLabel(code?: string | null) {
  if (!code) {
    return ''
  }
  return typeLabels.value[code] ?? code
}

function formatTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}
</script>

<template>
  <view class="detail-page">
    <view v-if="loadFailed" class="empty">
      <text class="empty-text">公告不存在或已撤回</text>
    </view>

    <view v-else-if="announcement" class="detail-card">
      <text class="detail-title">{{ announcement.title }}</text>
      <view class="detail-meta">
        <text v-if="typeLabel(announcement.typeCode)" class="tag">
          {{ typeLabel(announcement.typeCode) }}
        </text>
        <text class="meta-text">{{ formatTime(announcement.publishedAt) }}</text>
      </view>
      <text class="detail-content">{{ announcement.content }}</text>
      <view class="detail-foot">
        <text class="foot-text">
          你以「{{ audienceLabel(announcement.audienceCode) || '未知来源' }}」收到这条公告
        </text>
      </view>
    </view>

    <view v-else class="loading-tip">加载中…</view>
  </view>
</template>

<style scoped>
.detail-page {
  min-height: 100vh;
  padding: 24rpx;
  box-sizing: border-box;
}

.detail-card {
  background: #ffffff;
  border-radius: 20rpx;
  padding: 36rpx 32rpx;
  box-shadow: 0 2rpx 16rpx rgba(17, 24, 39, 0.05);
}

.detail-title {
  display: block;
  font-size: 36rpx;
  font-weight: 600;
  color: #111827;
  line-height: 1.4;
}

.detail-meta {
  margin-top: 16rpx;
  display: flex;
  align-items: center;
}

.tag {
  font-size: 20rpx;
  color: #2563eb;
  background: #eff6ff;
  border-radius: 999rpx;
  padding: 4rpx 14rpx;
  margin-right: 12rpx;
}

.meta-text {
  font-size: 22rpx;
  color: #9ca3af;
}

.detail-content {
  display: block;
  margin-top: 28rpx;
  font-size: 28rpx;
  color: #374151;
  line-height: 1.8;
  white-space: pre-wrap;
}

.detail-foot {
  margin-top: 36rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid #f3f4f6;
}

.foot-text {
  font-size: 22rpx;
  color: #9ca3af;
}

.empty {
  padding: 160rpx 0;
  text-align: center;
}

.empty-text {
  font-size: 26rpx;
  color: #9ca3af;
}

.loading-tip {
  text-align: center;
  color: #9ca3af;
  font-size: 24rpx;
  margin-top: 60rpx;
}
</style>
