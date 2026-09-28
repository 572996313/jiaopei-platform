<script setup lang="ts">
import { onLoad, onShow } from '@dcloudio/uni-app'
import { computed, ref, watch } from 'vue'
import { fetchStudentLessons, type MiniappLesson } from '@/api/miniapp'
import { useBoundStudent } from '@/composables/use-bound-student'
import LessonRow from '@/components/LessonRow.vue'
import StudentSwitcher from '@/components/StudentSwitcher.vue'

const { students, studentId, ready, showSwitcher, init, switchTo } = useBoundStudent()

const WEEKDAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const weekStart = ref('')
const selectedDate = ref('')
const lessons = ref<MiniappLesson[]>([])
const loading = ref(false)

/** 一周的七天:标签、日期、有没有课,一次算完给导航条和列表共用 */
const days = computed(() => {
  const start = new Date(weekStart.value + 'T00:00:00')
  const today = formatDate(new Date())
  return WEEKDAYS.map((label, index) => {
    const date = new Date(start)
    date.setDate(start.getDate() + index)
    const key = formatDate(date)
    return {
      date: key,
      label,
      dayNumber: date.getDate(),
      isToday: key === today,
      count: lessons.value.filter((item) => item.lessonDate === key).length,
    }
  })
})

const weekLabel = computed(() => {
  if (!weekStart.value) {
    return ''
  }
  const start = new Date(weekStart.value + 'T00:00:00')
  const end = new Date(start)
  end.setDate(end.getDate() + 6)
  return `${monthDay(start)} - ${monthDay(end)}`
})

const selectedDay = computed(() => days.value.find((item) => item.date === selectedDate.value) ?? null)
const selectedDayText = computed(() => {
  if (!selectedDay.value) {
    return ''
  }
  const date = new Date(selectedDay.value.date + 'T00:00:00')
  return `${selectedDay.value.label} ${monthDay(date)}`
})
const selectedDayLessons = computed(() =>
  lessons.value.filter((item) => item.lessonDate === selectedDate.value),
)

function formatDate(date: Date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

function monthDay(date: Date) {
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

function currentWeekMonday() {
  const today = new Date()
  const monday = new Date(today)
  monday.setDate(today.getDate() - ((today.getDay() + 6) % 7))
  return monday
}

function loadLessons() {
  if (!studentId.value || !weekStart.value) {
    lessons.value = []
    return
  }
  loading.value = true
  const end = new Date(weekStart.value + 'T00:00:00')
  end.setDate(end.getDate() + 6)
  fetchStudentLessons(studentId.value, weekStart.value, formatDate(end))
    .then((data) => {
      lessons.value = data
    })
    .catch(() => {
      lessons.value = []
    })
    .finally(() => {
      loading.value = false
    })
}

onLoad((options) => {
  const monday = currentWeekMonday()
  weekStart.value = formatDate(monday)
  // 默认停在今天;今天还没课也停在这里,列表会给"这天没有课"
  selectedDate.value = formatDate(new Date())
  init(decodeURIComponent(options?.studentId || ''))
})

// 页签缓存着这一页,PC 侧改完排课回来要能看到,所以每次显示都重拉
onShow(() => {
  if (ready.value && studentId.value) {
    loadLessons()
  }
})

watch(weekStart, () => {
  // 换周后原来选中那天可能已经不在这一周,落回本周今天或周一
  const monday = new Date(weekStart.value + 'T00:00:00')
  const end = new Date(monday)
  end.setDate(end.getDate() + 6)
  const today = new Date()
  selectedDate.value =
    today >= monday && today <= end ? formatDate(today) : formatDate(monday)
  loadLessons()
})

watch(studentId, (id) => {
  if (id) {
    loadLessons()
  }
})

function shiftWeek(offset: number) {
  const d = new Date(weekStart.value + 'T00:00:00')
  d.setDate(d.getDate() + offset * 7)
  weekStart.value = formatDate(d)
}

function goToday() {
  const monday = currentWeekMonday()
  const key = formatDate(monday)
  if (weekStart.value === key) {
    selectedDate.value = formatDate(new Date())
    return
  }
  weekStart.value = key
}

function goClassmates() {
  uni.navigateTo({ url: '/pages/classmates/index?studentId=' + studentId.value })
}
</script>

<template>
  <view class="timetable-page">
    <StudentSwitcher
      v-if="showSwitcher"
      :students="students"
      :active-id="studentId"
      @select="switchTo"
    />

    <view class="week-nav">
      <view class="nav-btn" @click="shiftWeek(-1)">‹</view>
      <view class="week-label-box" @click="goToday">
        <text class="week-label">{{ weekLabel }}</text>
        <text class="week-today">回本周</text>
      </view>
      <view class="nav-btn" @click="shiftWeek(1)">›</view>
    </view>

    <view class="day-tabs">
      <view
        v-for="day in days"
        :key="day.date"
        class="day-tab"
        :class="{ active: day.date === selectedDate, today: day.isToday }"
        @click="selectedDate = day.date"
      >
        <text class="tab-weekday">{{ day.label }}</text>
        <text class="tab-date">{{ day.dayNumber }}</text>
        <text class="tab-count" :class="{ blank: day.count === 0 }">{{ day.count > 0 ? day.count : '·' }}</text>
      </view>
    </view>

    <view class="day-card">
      <view class="day-card-head">
        <text class="day-card-title">
          {{ selectedDayText }}
          <text v-if="selectedDayLessons.length > 0" class="day-card-count">
            {{ selectedDayLessons.length }} 节课
          </text>
        </text>
        <text v-if="studentId" class="day-card-link" @click="goClassmates">我的同学 ›</text>
      </view>

      <view v-if="loading" class="page-tip">加载中…</view>
      <view v-else-if="!studentId" class="page-tip">
        <text>还没有可查看的学员,先在「我的」页绑定档案</text>
      </view>
      <view v-else-if="selectedDayLessons.length === 0" class="page-tip">
        <text>这天没有课</text>
      </view>
      <template v-else>
        <LessonRow v-for="lesson in selectedDayLessons" :key="lesson.lessonId" :lesson="lesson" />
      </template>
    </view>
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

.week-label-box {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.week-label {
  font-size: 30rpx;
  font-weight: 600;
  color: #111827;
}

.week-today {
  margin-top: 4rpx;
  font-size: 20rpx;
  color: #2563eb;
}

.day-tabs {
  display: flex;
  margin-bottom: 24rpx;
}

.day-tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14rpx 0;
  margin-right: 10rpx;
  background: #ffffff;
  border-radius: 14rpx;
  border: 1rpx solid transparent;
}

.day-tab:last-child {
  margin-right: 0;
}

.day-tab.active {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.tab-weekday {
  font-size: 22rpx;
  color: #9ca3af;
}

.day-tab.active .tab-weekday {
  color: #2563eb;
}

.tab-date {
  margin-top: 4rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: #111827;
}

.tab-count {
  margin-top: 2rpx;
  font-size: 20rpx;
  color: #2563eb;
}

.tab-count.blank {
  color: #d1d5db;
}

.day-card {
  background: #ffffff;
  border-radius: 20rpx;
  padding: 24rpx 28rpx;
  box-shadow: 0 2rpx 12rpx rgba(17, 24, 39, 0.04);
}

.day-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid #f3f4f6;
}

.day-card-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #111827;
}

.day-card-count {
  margin-left: 12rpx;
  font-size: 22rpx;
  font-weight: 400;
  color: #9ca3af;
}

.day-card-link {
  font-size: 24rpx;
  color: #2563eb;
}

.page-tip {
  padding: 60rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: #c4c9d1;
}
</style>
