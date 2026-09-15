<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { fetchStudentLessons, type MiniappLesson } from '@/api/miniapp'

const studentId = ref('')
const weekStart = ref('')
const lessons = ref<MiniappLesson[]>([])
const loading = ref(false)
const studentName = ref('')

// 周一..周日
const WEEKDAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

const grid = computed(() => {
  const start = new Date(weekStart.value + 'T00:00:00')
  const cells = Array.from({ length: 7 }, (_, i) => {
    const date = new Date(start)
    date.setDate(start.getDate() + i)
    return {
      label: WEEKDAYS[i],
      dateText: `${date.getMonth() + 1}月${date.getDate()}日`,
      date: formatDate(date),
      lessons: lessons.value.filter((l) => l.lessonDate === formatDate(date)),
    }
  })
  return cells
})

onLoad((options) => {
  studentId.value = decodeURIComponent(options?.studentId || '')
  const today = new Date()
  // 以本周一为起始
  const monday = new Date(today)
  monday.setDate(today.getDate() - ((today.getDay() + 6) % 7))
  weekStart.value = formatDate(monday)
  loadLessons()
})

async function loadLessons() {
  if (!studentId.value) {
    return
  }
  loading.value = true
  try {
    const endDate = new Date(weekStart.value + 'T00:00:00')
    endDate.setDate(endDate.getDate() + 6)
    lessons.value = await fetchStudentLessons(
      studentId.value,
      weekStart.value,
      formatDate(endDate),
    )
  } catch {
    lessons.value = []
  } finally {
    loading.value = false
  }
}

function formatDate(date: Date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

function prevWeek() {
  const d = new Date(weekStart.value + 'T00:00:00')
  d.setDate(d.getDate() - 7)
  weekStart.value = formatDate(d)
  loadLessons()
}

function nextWeek() {
  const d = new Date(weekStart.value + 'T00:00:00')
  d.setDate(d.getDate() + 7)
  weekStart.value = formatDate(d)
  loadLessons()
}

function lessonStatusText(status: string) {
  const map: Record<string, string> = {
    scheduled: '已排课',
    rescheduled: '已调课',
    cancelled: '已停课',
    makeup: '补课',
    completed: '已结课',
  }
  return map[status] || status
}
</script>

<template>
  <view class="timetable-page">
    <view class="week-nav">
      <view class="nav-btn" @click="prevWeek">‹</view>
      <text class="week-label">{{ weekStart }} 起</text>
      <view class="nav-btn" @click="nextWeek">›</view>
    </view>

    <view class="week-grid">
      <view v-for="day in grid" :key="day.date" class="day-col">
        <view class="day-head">
          <text class="day-label">{{ day.label }}</text>
          <text class="day-date">{{ day.dateText }}</text>
        </view>
        <view v-if="day.lessons.length === 0" class="day-empty">
          <text class="empty-text">无课</text>
        </view>
        <view
          v-for="lesson in day.lessons"
          :key="lesson.lessonId"
          class="lesson-card"
          :class="{ cancelled: lesson.lessonStatus === 'cancelled' }"
        >
          <text class="lesson-time">{{ lesson.startTime }}–{{ lesson.endTime }}</text>
          <text class="lesson-title">{{ lesson.courseName || lesson.lessonTitle || '课程' }}</text>
          <text class="lesson-sub">{{ lesson.className || '' }}</text>
          <view class="status-tag" :class="{ cancelled: lesson.lessonStatus === 'cancelled' }">
            {{ lessonStatusText(lesson.lessonStatus) }}
          </view>
        </view>
      </view>
    </view>

    <view v-if="loading" class="loading-tip">加载中…</view>
  </view>
</template>

<style scoped>
.timetable-page {
  min-height: 100vh;
  padding: 24rpx;
  box-sizing: border-box;
}

.week-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.nav-btn {
  width: 64rpx;
  height: 64rpx;
  line-height: 60rpx;
  text-align: center;
  background: #ffffff;
  border-radius: 50%;
  font-size: 36rpx;
  color: #2563eb;
  box-shadow: 0 2rpx 8rpx rgba(17, 24, 39, 0.06);
}

.week-label {
  font-size: 30rpx;
  font-weight: 600;
  color: #111827;
}

.week-grid {
  display: flex;
  overflow-x: auto;
}

.day-col {
  flex: 1;
  min-width: 180rpx;
  background: #ffffff;
  border-radius: 16rpx;
  margin-right: 16rpx;
  padding: 20rpx 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(17, 24, 39, 0.04);
}

.day-col:last-child {
  margin-right: 0;
}

.day-head {
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid #f3f4f6;
}

.day-label {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #111827;
}

.day-date {
  display: block;
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #9ca3af;
}

.day-empty {
  padding: 32rpx 0;
  text-align: center;
}

.empty-text {
  font-size: 24rpx;
  color: #c4c9d1;
}

.lesson-card {
  position: relative;
  margin-top: 20rpx;
  padding: 16rpx 12rpx;
  border-radius: 12rpx;
  background: #eff6ff;
  border-left: 6rpx solid #2563eb;
}

.lesson-card.cancelled {
  background: #f9fafb;
  border-left-color: #d1d5db;
  opacity: 0.7;
}

.lesson-time {
  display: block;
  font-size: 22rpx;
  color: #2563eb;
  font-weight: 600;
}

.lesson-card.cancelled .lesson-time {
  color: #9ca3af;
}

.lesson-title {
  display: block;
  margin-top: 8rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #111827;
  line-height: 1.3;
}

.lesson-sub {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #6b7280;
}

.status-tag {
  margin-top: 10rpx;
  display: inline-block;
  font-size: 20rpx;
  color: #2563eb;
  background: #ffffff;
  border-radius: 999rpx;
  padding: 2rpx 12rpx;
}

.status-tag.cancelled {
  color: #6b7280;
}

.loading-tip {
  text-align: center;
  color: #9ca3af;
  font-size: 24rpx;
  margin-top: 24rpx;
}
</style>