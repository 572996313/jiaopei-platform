<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { listMyStudents, type StudentBrief } from '@/api/miniapp'

const authStore = useAuthStore()
const boundStudents = ref<StudentBrief[]>([])

const displayName = computed(
  () => authStore.userInfo?.displayName || authStore.userInfo?.username || '未登录',
)
const avatarChar = computed(() => displayName.value.slice(0, 1) || '客')

// 角色码转中文展示;未映射的角色码原样显示
const ROLE_LABELS: Record<string, string> = {
  MINIAPP_STUDENT: '学生',
  MINIAPP_PARENT: '家长',
  PLATFORM_ADMIN: '管理员',
}
const roleText = computed(() => {
  const roles = authStore.userInfo?.roles ?? []
  return roles.length > 0
    ? roles.map((code) => ROLE_LABELS[code] ?? code).join('、')
    : '暂无角色'
})

onShow(() => {
  if (!authStore.isLoggedIn) {
    return
  }
  loadStudents()
})

async function loadStudents() {
  try {
    boundStudents.value = await listMyStudents()
  } catch {
    boundStudents.value = []
  }
}

function goAddStudent() {
  uni.navigateTo({ url: '/pages/bind-student/index' })
}

function goTimetable(studentId: string) {
  uni.navigateTo({ url: '/pages/timetable/index?studentId=' + studentId })
}

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

function handleDeregister() {
  uni.showModal({
    title: '注销账号',
    content: '注销后将删除你的账号信息,且无法恢复,确定注销吗?',
    confirmColor: '#dc2626',
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      uni.showLoading({ title: '注销中…' })
      try {
        await authStore.deregister()
        uni.hideLoading()
        uni.showToast({ title: '已注销', icon: 'success' })
        setTimeout(() => {
          uni.reLaunch({ url: '/pages/login/index' })
        }, 600)
      } catch (error) {
        uni.hideLoading()
        uni.showToast({ title: '注销失败,请重试', icon: 'none' })
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

    <view class="section-title">我的学员</view>
    <view class="info-card">
      <view v-if="boundStudents.length === 0" class="empty-row">
        <text class="empty-text">还没有绑定学员,先去绑定学员档案吧</text>
      </view>
      <view
        v-for="student in boundStudents"
        :key="student.id"
        class="student-row"
        @click="goTimetable(student.id)"
      >
        <view class="student-avatar">{{ student.studentName.slice(0, 1) }}</view>
        <view class="student-meta">
          <text class="student-name">{{ student.studentName }}</text>
          <text class="student-class">{{ student.className || '未分班' }}</text>
        </view>
        <text class="student-arrow">›</text>
      </view>
      <view class="add-row" @click="goAddStudent">
        <text class="add-text">＋ 绑定学员档案</text>
      </view>
    </view>

    <button class="logout-btn" @click="handleLogout">退出登录</button>
    <button class="deregister-btn" @click="handleDeregister">注销账号</button>
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

.section-title {
  margin: 40rpx 0 20rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: #111827;
}

.student-row {
  display: flex;
  align-items: center;
  padding: 26rpx 0;
  border-bottom: 1rpx solid #f3f4f6;
}

.student-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: #eff6ff;
  color: #2563eb;
  font-size: 32rpx;
  text-align: center;
  line-height: 72rpx;
  margin-right: 24rpx;
}

.student-meta {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.student-name {
  font-size: 30rpx;
  color: #111827;
  font-weight: 500;
}

.student-class {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #9ca3af;
}

.student-arrow {
  font-size: 36rpx;
  color: #c4c9d1;
}

.empty-row {
  padding: 30rpx 0;
}

.empty-text {
  font-size: 26rpx;
  color: #9ca3af;
}

.add-row {
  padding: 26rpx 0 10rpx;
}

.add-text {
  font-size: 28rpx;
  color: #2563eb;
  text-align: center;
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

.deregister-btn {
  margin-top: 24rpx;
  width: 100%;
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 46rpx;
  background: #ffffff;
  color: #9ca3af;
  font-size: 32rpx;
  border: 1rpx solid #e5e7eb;
}

.deregister-btn::after {
  border: none;
}
</style>
