<script setup lang="ts">
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import {
  audienceLabel,
  fetchAnnouncementTypeLabels,
  fetchMyAnnouncements,
  fetchUnreadCount,
  type MyAnnouncement,
} from '@/api/announcement'

const PAGE_SIZE = 10

const items = ref<MyAnnouncement[]>([])
const total = ref(0)
const page = ref(1)
const unreadOnly = ref(false)
const unreadCount = ref(0)
const loading = ref(false)
const typeLabels = ref<Record<string, string>>({})

onShow(() => {
  typeLabels.value = {}
  fetchAnnouncementTypeLabels().then((labels) => {
    typeLabels.value = labels
  })
  loadFirstPage()
  loadUnreadCount()
})

async function loadFirstPage() {
  page.value = 1
  await loadPage()
}

async function loadPage() {
  if (loading.value) {
    return
  }
  loading.value = true
  try {
    const result = await fetchMyAnnouncements(page.value, PAGE_SIZE, unreadOnly.value)
    items.value = page.value === 1 ? result.records : items.value.concat(result.records)
    total.value = result.total
  } catch {
    if (page.value === 1) {
      items.value = []
    }
    uni.showToast({ title: '消息加载失败,请重试', icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function loadUnreadCount() {
  try {
    unreadCount.value = await fetchUnreadCount()
  } catch {
    unreadCount.value = 0
  }
}

function switchTab(unread: boolean) {
  if (unreadOnly.value === unread) {
    return
  }
  unreadOnly.value = unread
  loadFirstPage()
}

function goDetail(item: MyAnnouncement) {
  uni.navigateTo({ url: '/pages/messages/detail?id=' + item.id })
}

function typeLabel(code?: string | null) {
  if (!code) {
    return ''
  }
  return typeLabels.value[code] ?? code
}

function formatTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}

onPullDownRefresh(async () => {
  await Promise.all([loadFirstPage(), loadUnreadCount()])
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  if (items.value.length >= total.value) {
    return
  }
  page.value += 1
  loadPage()
})
</script>

<template>
  <view class="msg-page">
    <view class="tabs">
      <view class="tab" :class="{ active: !unreadOnly }" @click="switchTab(false)">全部</view>
      <view class="tab" :class="{ active: unreadOnly }" @click="switchTab(true)">
        未读<text v-if="unreadCount > 0">（{{ unreadCount }}）</text>
      </view>
    </view>

    <view v-if="!loading && items.length === 0" class="empty">
      <text class="empty-text">{{ unreadOnly ? '没有未读公告' : '暂时还没有收到公告' }}</text>
    </view>

    <view
      v-for="item in items"
      :key="item.id"
      class="msg-card"
      :class="{ unread: !item.readAt }"
      @click="goDetail(item)"
    >
      <view class="msg-head">
        <view class="msg-title">
          <text v-if="item.top" class="tag top">置顶</text>
          <text v-if="!item.readAt" class="dot" />
          <text class="title-text">{{ item.title }}</text>
        </view>
        <text class="msg-time">{{ formatTime(item.publishedAt) }}</text>
      </view>
      <text class="msg-preview">{{ item.content }}</text>
      <view class="msg-foot">
        <text v-if="typeLabel(item.typeCode)" class="tag">{{ typeLabel(item.typeCode) }}</text>
        <text class="foot-sub">收到于 {{ formatTime(item.publishedAt) }} · {{ audienceLabel(item.audienceCode) || '未知来源' }}</text>
      </view>
    </view>

    <view v-if="loading" class="loading-tip">加载中…</view>
    <view v-else-if="items.length > 0 && items.length >= total" class="loading-tip">没有更多了</view>
  </view>
</template>

<style scoped>
.msg-page {
  min-height: 100vh;
  padding: 24rpx;
  box-sizing: border-box;
}

.tabs {
  display: flex;
  background: #ffffff;
  border-radius: 16rpx;
  padding: 8rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(17, 24, 39, 0.04);
}

.tab {
  flex: 1;
  text-align: center;
  font-size: 28rpx;
  color: #6b7280;
  padding: 16rpx 0;
  border-radius: 12rpx;
}

.tab.active {
  background: #eff6ff;
  color: #2563eb;
  font-weight: 600;
}

.empty {
  padding: 120rpx 0;
  text-align: center;
}

.empty-text {
  font-size: 26rpx;
  color: #9ca3af;
}

.msg-card {
  background: #ffffff;
  border-radius: 20rpx;
  padding: 28rpx 28rpx 24rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 16rpx rgba(17, 24, 39, 0.05);
}

.msg-card.unread {
  border-left: 6rpx solid #2563eb;
}

.msg-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.msg-title {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.title-text {
  font-size: 30rpx;
  font-weight: 600;
  color: #111827;
  line-height: 1.4;
}

.dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: #dc2626;
  margin-right: 12rpx;
  flex-shrink: 0;
}

.msg-time {
  font-size: 22rpx;
  color: #9ca3af;
  margin-left: 16rpx;
  flex-shrink: 0;
}

.msg-preview {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: #6b7280;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.msg-foot {
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

.tag.top {
  color: #dc2626;
  background: #fef2f2;
}

.foot-sub {
  font-size: 22rpx;
  color: #9ca3af;
}

.loading-tip {
  text-align: center;
  color: #9ca3af;
  font-size: 24rpx;
  margin-top: 16rpx;
}
</style>
