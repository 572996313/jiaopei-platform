<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import {
  fetchGenderOptions,
  fetchMyStudentProfile,
  listMyStudents,
  unbindStudent,
  type DictOptionItem,
  type StudentBrief,
  type StudentOwnProfile,
} from '@/api/miniapp'

const authStore = useAuthStore()
const boundStudents = ref<StudentBrief[]>([])
const genderOptions = ref<DictOptionItem[]>([])
const myProfile = ref<StudentOwnProfile | null>(null)

const displayName = computed(
  () => authStore.userInfo?.displayName || authStore.userInfo?.username || '未登录',
)
const avatarChar = computed(() => displayName.value.slice(0, 1) || '客')
const isStudent = computed(() => authStore.userInfo?.roles?.includes('MINIAPP_STUDENT') ?? false)

// 角色码转中文展示
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
  if (!authStore.isLoggedIn()) {
    return
  }
  if (isStudent.value) {
    loadMyProfile()
  } else {
    loadStudents()
  }
})

async function loadMyProfile() {
  // 性别回显走 gender 字典,与 PC 端同一套值;拉取失败退回原始值显示
  fetchGenderOptions()
    .then((items) => {
      genderOptions.value = items
    })
    .catch(() => undefined)
  try {
    myProfile.value = await fetchMyStudentProfile()
  } catch {
    myProfile.value = null
  }
}

function genderText(gender?: string | null) {
  if (!gender) {
    return '-'
  }
  return genderOptions.value.find((item) => item.itemValue === gender)?.itemName || gender
}

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

function handleUnbind(student: StudentBrief) {
  uni.showModal({
    title: '解绑学员',
    content: `确定解绑「${student.studentName}」吗?解绑后将无法查看该学员课表,可重新绑定。`,
    confirmColor: '#dc2626',
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      try {
        await unbindStudent(student.id)
        uni.showToast({ title: '已解绑', icon: 'success' })
        loadStudents()
      } catch {
        uni.showToast({ title: '解绑失败,请重试', icon: 'none' })
      }
    },
  })
}

function goBindSelf() {
  uni.navigateTo({ url: '/pages/student-profile/index?mode=bind' })
}

function goCreateSelf() {
  uni.navigateTo({ url: '/pages/student-profile/index?mode=create' })
}

function goEditProfile() {
  uni.navigateTo({ url: '/pages/student-profile/index?mode=edit' })
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
      } catch {
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
        <text class="info-label">身份</text>
        <text class="info-value">{{ roleText }}</text>
      </view>
      <view class="info-row">
        <text class="info-label">所属机构</text>
        <text class="info-value">{{ authStore.userInfo?.institutionId || '-' }}</text>
      </view>
    </view>

    <!-- 学生端:未绑定档案 -->
    <template v-if="isStudent && myProfile && myProfile.bound === false">
      <view class="section-title">我的档案</view>
      <view class="info-card">
        <view class="empty-row">
          <text class="empty-text">你还没有绑定学员档案</text>
          <text class="empty-sub-text">绑定后可查看自己的课表,一个学生账号只能绑定一个档案</text>
        </view>
        <view class="add-row" @click="goBindSelf">
          <text class="add-text">按学号绑定已有档案</text>
        </view>
        <view class="add-row" @click="goCreateSelf">
          <text class="add-text">没有档案?自助创建</text>
        </view>
      </view>
    </template>

    <!-- 学生端:已绑定档案 -->
    <template v-if="isStudent && myProfile && myProfile.bound === true">
      <view class="section-title">我的档案</view>
      <view class="info-card">
        <view class="profile-row">
          <view class="profile-avatar">{{ (myProfile.studentName || '?').slice(0, 1) }}</view>
          <view class="profile-meta">
            <text class="profile-name">{{ myProfile.studentName }}</text>
            <text class="profile-class">{{ myProfile.className || '未分班' }}</text>
          </view>
        </view>
        <view class="profile-detail">
          <text class="detail-label">学号</text>
          <text class="detail-value">{{ myProfile.studentNo || '-' }}</text>
        </view>
        <view class="profile-detail">
          <text class="detail-label">性别</text>
          <text class="detail-value">{{ genderText(myProfile.gender) }}</text>
        </view>
        <view class="profile-detail">
          <text class="detail-label">生日</text>
          <text class="detail-value">{{ myProfile.birthday || '-' }}</text>
        </view>
        <view class="profile-actions">
          <text class="action-link timetable" @click="goTimetable(myProfile?.studentId || '')">我的课表</text>
          <text class="action-link edit" @click="goEditProfile">编辑档案</text>
        </view>
      </view>
    </template>

    <!-- 家长端:我的学员 -->
    <template v-if="!isStudent">
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
          <view class="student-actions">
            <text class="unbind-btn" @click.stop="handleUnbind(student)">解绑</text>
            <text class="student-arrow">›</text>
          </view>
        </view>
        <view class="add-row" @click="goAddStudent">
          <text class="add-text">+ 绑定学员档案</text>
        </view>
      </view>
    </template>

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

/* 学生档案 */
.profile-row {
  display: flex;
  align-items: center;
  padding: 26rpx 0;
  border-bottom: 1rpx solid #f3f4f6;
}

.profile-avatar {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: #eff6ff;
  color: #2563eb;
  font-size: 36rpx;
  text-align: center;
  line-height: 80rpx;
  margin-right: 24rpx;
}

.profile-meta {
  flex: 1;
}

.profile-name {
  font-size: 32rpx;
  font-weight: 600;
  color: #111827;
}

.profile-class {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #9ca3af;
}

.profile-detail {
  display: flex;
  justify-content: space-between;
  padding: 22rpx 0;
  border-bottom: 1rpx solid #f3f4f6;
}

.detail-label {
  font-size: 28rpx;
  color: #6b7280;
}

.detail-value {
  font-size: 28rpx;
  color: #111827;
}

.profile-actions {
  display: flex;
  justify-content: space-between;
  padding: 22rpx 0 10rpx;
}

.action-link {
  font-size: 28rpx;
  font-weight: 500;
}

.action-link.timetable {
  color: #2563eb;
}

.action-link.edit {
  color: #6b7280;
}

/* 家长学员列表 */
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

.student-actions {
  display: flex;
  align-items: center;
}

.unbind-btn {
  font-size: 24rpx;
  color: #dc2626;
  border: 1rpx solid #fecaca;
  border-radius: 999rpx;
  padding: 6rpx 20rpx;
  margin-right: 16rpx;
}

.empty-row {
  padding: 30rpx 0;
}

.empty-text {
  font-size: 28rpx;
  color: #6b7280;
}

.empty-sub-text {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #9ca3af;
}

.add-row {
  padding: 26rpx 0 10rpx;
}

.add-text {
  font-size: 28rpx;
  color: #2563eb;
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