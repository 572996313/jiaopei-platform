<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { fetchUnreadCount } from '@/api/announcement'

interface WorkEntry {
  icon: string
  title: string
  desc: string
  path: string
  /** tabBar 页只能用 switchTab 跳转 */
  switchTab?: boolean
}

const authStore = useAuthStore()
const portalResolved = ref(Boolean(authStore.currentPortal()))
const unreadCount = ref(0)

const displayName = computed(
  () => authStore.userInfo?.displayName || authStore.userInfo?.username || '',
)
const isEnterprise = computed(() => authStore.isEnterprise())
const isStudent = computed(() => authStore.userInfo?.roles?.includes('MINIAPP_STUDENT') ?? false)

/** 企业端：只放入口页，新增/建账等动作留在各自的列表页里，避免同一件事有两个入口 */
const feeEntries: WorkEntry[] = [
  {
    icon: 'wallet-filled',
    title: '收费台账',
    desc: '查台账、新增、按班建账、收款',
    path: '/pages/enterprise/fee-ledger/index',
  },
  {
    icon: 'list',
    title: '收费项目',
    desc: '保教费、伙食费等',
    path: '/pages/enterprise/fee-item/index',
  },
  {
    icon: 'flag',
    title: '收费价目',
    desc: '校区/班级/学期/学员价',
    path: '/pages/enterprise/fee-price/index',
  },
]

const archiveEntries: WorkEntry[] = [
  {
    icon: 'person-filled',
    title: '学员档案',
    desc: '查学员、新增、维护家长联系人',
    path: '/pages/enterprise/student/index',
  },
  {
    icon: 'staff-filled',
    title: '班级与入班',
    desc: '建班、名册与入班',
    path: '/pages/enterprise/class/index',
  },
]

/** 学生/家长端：首页看结果，这里放需要主动点的服务；档案与绑定统一收在「我的」 */
const studyEntries = computed<WorkEntry[]>(() => {
  const entries: WorkEntry[] = [
    { icon: 'calendar', title: '课程表', desc: '查看上课安排', path: '/pages/timetable/index' },
    {
      icon: 'notification-filled',
      title: '消息通知',
      desc: '学校发布的公告',
      path: '/pages/messages/index',
    },
  ]
  if (isStudent.value) {
    entries.push({
      icon: 'staff-filled',
      title: '我的同学',
      desc: '按班级查看同班名单',
      path: '/pages/classmates/index',
    })
  } else {
    entries.push({
      icon: 'wallet',
      title: '我的费用',
      desc: '查看孩子的应收与收款记录',
      path: '/pages/fees/index',
    })
  }
  return entries
})

onShow(() => {
  if (!authStore.isLoggedIn()) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  authStore
    .refreshUserInfo()
    .catch(() => {
      // 刷新失败继续用会话里记住的端展示，401 会由请求层回登录页
    })
    .finally(() => {
      portalResolved.value = true
      if (!isEnterprise.value) {
        fetchUnreadCount()
          .then((count) => {
            unreadCount.value = count
          })
          .catch(() => {
            unreadCount.value = 0
          })
      }
    })
})

function openEntry(entry: WorkEntry) {
  if (entry.switchTab) {
    uni.switchTab({ url: entry.path })
    return
  }
  uni.navigateTo({ url: entry.path })
}

onPullDownRefresh(async () => {
  try {
    await authStore.refreshUserInfo().catch(() => undefined)
    if (!isEnterprise.value) {
      unreadCount.value = await fetchUnreadCount().catch(() => 0)
    }
  } finally {
    uni.stopPullDownRefresh()
  }
})
</script>

<template>
  <view v-if="!portalResolved" class="ent-page">
    <view class="ent-card ent-empty">加载中…</view>
  </view>

  <view v-else class="ent-page">
    <view class="workbench-head">
      <view class="head-main">
        <text class="head-kicker">{{ isEnterprise ? '企业工作台' : '学习服务台' }}</text>
        <text class="head-title">{{ displayName || '你好' }}</text>
        <text class="head-sub">
          {{ isEnterprise ? '收费、学员与班级的日常操作都在这里' : '需要主动办理的服务集中在这里' }}
        </text>
      </view>
      <view class="head-badge">
        <text class="badge-label">看的</text>
        <text class="badge-sep">/</text>
        <text class="badge-label strong">做的</text>
      </view>
    </view>

    <template v-if="isEnterprise">
      <view class="ent-section-title">收费管理</view>
      <view class="feature-grid">
        <view
          v-for="item in feeEntries"
          :key="item.title"
          class="feature-card"
          hover-class="ent-card-hover"
          @click="openEntry(item)"
        >
          <uni-icons :type="item.icon" size="26" color="#2563eb" />
          <text class="feature-title">{{ item.title }}</text>
          <text class="feature-desc">{{ item.desc }}</text>
        </view>
      </view>

      <view class="ent-section-title">学员与班级</view>
      <view class="feature-grid">
        <view
          v-for="item in archiveEntries"
          :key="item.title"
          class="feature-card"
          hover-class="ent-card-hover"
          @click="openEntry(item)"
        >
          <uni-icons :type="item.icon" size="26" color="#2563eb" />
          <text class="feature-title">{{ item.title }}</text>
          <text class="feature-desc">{{ item.desc }}</text>
        </view>
      </view>

      <view class="ent-card wb-tip-card">
        <view class="ent-card-title">
          <text>操作口径</text>
        </view>
        <view class="ent-tip">
          工作台只放各功能入口；新增台账、按班建账在「收费台账」里，新增学员在「学员档案」里，
          建班和入班在「班级与入班」里，避免同一件事出现两个按钮。金额由服务端实时计算。
        </view>
      </view>
    </template>

    <template v-else>
      <view class="ent-section-title">学习服务</view>
      <view class="feature-grid">
        <view
          v-for="item in studyEntries"
          :key="item.title"
          class="feature-card"
          hover-class="ent-card-hover"
          @click="openEntry(item)"
        >
          <uni-icons :type="item.icon" size="26" color="#2563eb" />
          <text
            v-if="item.title === '消息通知' && unreadCount > 0"
            class="badge"
          >{{ unreadCount > 99 ? '99+' : unreadCount }}</text>
          <text class="feature-title">{{ item.title }}</text>
          <text class="feature-desc">{{ item.desc }}</text>
        </view>
      </view>

      <view class="ent-card wb-tip-card">
        <view class="ent-card-title">
          <text>分区说明</text>
        </view>
        <view class="ent-tip">
          首页只看今天和最近的情况；查费用、看完整课表、查公告、看同班名单这些需要主动点的服务放在工作台，档案与孩子绑定统一收在「我的」。
        </view>
      </view>
    </template>
  </view>
</template>

<style scoped>
.workbench-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 36rpx 32rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  color: #ffffff;
}

.head-main {
  flex: 1;
  min-width: 0;
}

.head-kicker {
  display: block;
  font-size: 22rpx;
  letter-spacing: 3rpx;
  opacity: 0.8;
}

.head-title {
  display: block;
  margin-top: 12rpx;
  font-size: 38rpx;
  font-weight: 600;
}

.head-sub {
  display: block;
  margin-top: 12rpx;
  font-size: 25rpx;
  line-height: 1.6;
  opacity: 0.9;
}

.head-badge {
  flex: 0 0 auto;
  margin-left: 20rpx;
  padding: 10rpx 16rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.35);
  border-radius: 999rpx;
  font-size: 21rpx;
  white-space: nowrap;
}

.badge-label {
  color: rgba(255, 255, 255, 0.7);
}

.badge-label.strong {
  color: #ffffff;
  font-weight: 600;
}

.badge-sep {
  margin: 0 6rpx;
  color: rgba(255, 255, 255, 0.45);
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

.badge {
  position: absolute;
  top: 24rpx;
  right: 24rpx;
  min-width: 34rpx;
  height: 34rpx;
  line-height: 34rpx;
  padding: 0 8rpx;
  text-align: center;
  font-size: 20rpx;
  color: #ffffff;
  background: #dc2626;
  border-radius: 999rpx;
}

.wb-tip-card {
  margin-top: 4rpx;
}
</style>
