<script setup lang="ts">
import { computed } from 'vue'

/** 首页与完整课表的行字段不完全一样,这里只声明渲染真正用得上的那几个 */
interface LessonLike {
  startTime: string
  endTime: string
  courseName: string | null
  classroomName: string | null
  teacherName: string | null
  lessonStatus: string
  /** 完整课表的课次自带标题,首页的口径是课程名 */
  lessonTitle?: string | null
  studentName?: string | null
}

const props = defineProps<{
  lesson: LessonLike
  /** 家长绑定多个孩子时显示姓名角标,学生端不用 */
  showStudent?: boolean
}>()

const STATUS_TEXT: Record<string, string> = {
  scheduled: '',
  rescheduled: '已调课',
  cancelled: '已停课',
  makeup: '补课',
  completed: '已结课',
}

const isCancelled = computed(() => props.lesson.lessonStatus === 'cancelled')
const statusText = computed(() => STATUS_TEXT[props.lesson.lessonStatus] ?? props.lesson.lessonStatus)
const title = computed(() => props.lesson.courseName || props.lesson.lessonTitle || '课程')
/** 教师只给姓,和教室拼在一行;两者都可能没填,全空就不占一行 */
const sub = computed(() =>
  [props.lesson.teacherName, props.lesson.classroomName].filter(Boolean).join(' · '),
)
const badge = computed(() => (props.showStudent ? props.lesson.studentName : ''))

/** 后端给的是 09:25:00,这一屏只到分钟就够,秒没有信息量 */
function clock(value: string) {
  const parts = String(value ?? '').split(':')
  return parts.length >= 2 ? `${parts[0]}:${parts[1]}` : value
}
</script>

<template>
  <view class="lesson-row" :class="{ cancelled: isCancelled }">
    <view class="time-col">
      <text class="start">{{ clock(lesson.startTime) }}</text>
      <text class="end">{{ clock(lesson.endTime) }}</text>
    </view>
    <view class="main-col">
      <text class="title">{{ title }}</text>
      <text v-if="sub" class="sub">{{ sub }}</text>
    </view>
    <view class="tail-col">
      <text v-if="badge" class="student-badge">{{ badge }}</text>
      <text v-if="statusText" class="status-tag" :class="{ muted: isCancelled }">{{ statusText }}</text>
    </view>
  </view>
</template>

<style scoped>
.lesson-row {
  display: flex;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #f3f4f6;
}

.lesson-row:last-child {
  border-bottom: none;
}

.time-col {
  display: flex;
  flex-direction: column;
  width: 110rpx;
  flex-shrink: 0;
}

.start {
  font-size: 30rpx;
  font-weight: 600;
  color: #2563eb;
}

.end {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #c4c9d1;
}

.main-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.title {
  font-size: 30rpx;
  font-weight: 500;
  color: #111827;
}

.sub {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #6b7280;
}

.tail-col {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  flex-shrink: 0;
  margin-left: 16rpx;
}

.student-badge {
  font-size: 22rpx;
  color: #2563eb;
  background: #eff6ff;
  border-radius: 999rpx;
  padding: 4rpx 14rpx;
}

.status-tag {
  margin-top: 6rpx;
  font-size: 20rpx;
  color: #b45309;
  background: #fef3c7;
  border-radius: 999rpx;
  padding: 4rpx 14rpx;
}

/* 停课是"不用去"的课,和正常课次区分开靠颜色而不是排版 */
.lesson-row.cancelled .start,
.lesson-row.cancelled .title {
  color: #9ca3af;
}

.status-tag.muted {
  color: #6b7280;
  background: #f3f4f6;
}
</style>
