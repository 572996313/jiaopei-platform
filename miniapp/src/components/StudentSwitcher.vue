<script setup lang="ts">
import type { StudentBrief } from '@/api/miniapp'

defineProps<{
  students: StudentBrief[]
  activeId: string
}>()

const emit = defineEmits<{ (e: 'select', id: string): void }>()
</script>

<template>
  <view class="student-switcher">
    <view
      v-for="item in students"
      :key="item.id"
      class="switch-chip"
      :class="{ active: String(item.id) === activeId }"
      @click="emit('select', String(item.id))"
    >
      {{ item.studentName }}
    </view>
  </view>
</template>

<style scoped>
.student-switcher {
  display: flex;
  flex-wrap: wrap;
  margin-bottom: 24rpx;
}

.switch-chip {
  margin: 0 16rpx 16rpx 0;
  padding: 10rpx 30rpx;
  border-radius: 999rpx;
  font-size: 26rpx;
  color: #6b7280;
  background: #ffffff;
  border: 1rpx solid #e5e7eb;
}

.switch-chip.active {
  color: #2563eb;
  background: #eff6ff;
  border-color: #bfdbfe;
  font-weight: 600;
}
</style>
