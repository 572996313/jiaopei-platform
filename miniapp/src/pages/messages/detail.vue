<script setup lang="ts">
import { onLoad, onShareAppMessage } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import {
  audienceLabel,
  fetchAnnouncementTypeLabels,
  openMyAnnouncement,
  type MyAnnouncement,
} from '@/api/announcement'

/** 扫小程序码进来时 scene 的全部内容：a=公告ID，前缀与后端 MiniappQrcodeServiceImpl 一致 */
const SCENE_ANNOUNCEMENT = /^a=(\d+)$/

const announcement = ref<MyAnnouncement | null>(null)
const announcementId = ref('')
const loadFailed = ref(false)
/** 扫码进来的码可能已经在群里挂了好几天,失败时要说清"怎样才看得到",而不是一句"不存在" */
const enteredByScan = ref(false)
const typeLabels = ref<Record<string, string>>({})

onLoad((options) => {
  const scene = options?.scene ? decodeParam(options.scene) : ''
  const fromScene = SCENE_ANNOUNCEMENT.exec(scene)
  enteredByScan.value = Boolean(fromScene)
  // 应用内跳转与分享卡片带 id;只有扫码这一条路传 scene
  announcementId.value = options?.id || (fromScene ? fromScene[1] : '')
  if (!announcementId.value) {
    loadFailed.value = true
    return
  }
  fetchAnnouncementTypeLabels().then((labels) => {
    typeLabels.value = labels
  })
  // 这个请求同时就是"打读"动作：服务端首次打开时记录已读时间
  openMyAnnouncement(announcementId.value)
    .then((result) => {
      announcement.value = result
    })
    .catch(() => {
      loadFailed.value = true
      uni.showToast({ title: '公告不存在或已撤回', icon: 'none' })
    })
})

/** 转发给同事/家长群：卡片标题用公告标题,点开直达这一条而不是消息列表 */
onShareAppMessage(() => ({
  title: announcement.value?.title ? `【通知】${announcement.value.title}` : '公告详情',
  path: '/pages/messages/detail?id=' + announcementId.value,
}))

const failedText = computed(() =>
  enteredByScan.value
    ? '看不到这条公告：它只发给已绑定学员的家长、以及已建档并绑定账号的教师,已撤回的公告也不再可见。'
    : '公告不存在或已撤回'
)

function goBindStudent() {
  uni.navigateTo({ url: '/pages/bind-student/index' })
}

/** 微信把 scene 原样(百分号编码)传进 options,部分平台已解过一层,解不开就按原值判 */
function decodeParam(value: string): string {
  try {
    return decodeURIComponent(value)
  } catch {
    return value
  }
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
</script>

<template>
  <view class="detail-page">
    <view v-if="loadFailed" class="empty">
      <text class="empty-text">{{ failedText }}</text>
      <button v-if="enteredByScan" class="bind-btn" @click="goBindStudent">去绑定学员</button>
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
  line-height: 1.7;
}

.bind-btn {
  margin-top: 40rpx;
  width: 320rpx;
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 40rpx;
  background: #2563eb;
  color: #ffffff;
  font-size: 28rpx;
}

.loading-tip {
  text-align: center;
  color: #9ca3af;
  font-size: 24rpx;
  margin-top: 60rpx;
}
</style>
