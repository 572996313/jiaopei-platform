<template>
  <div class="pattern-grid">
    <div class="pattern-grid-toolbar">
      <span class="pattern-grid-tip">点击单元格选择每周固定节次，再点一次取消</span>
      <span class="pattern-grid-count">已选 {{ modelValue.length }} 个节次</span>
    </div>
    <div class="pattern-grid-scroll">
      <div class="pattern-grid-board" :style="{ gridTemplateColumns: boardColumns }">
        <div class="pattern-grid-cell is-corner">节次</div>
        <div
          v-for="day in weekdays"
          :key="`head-${day}`"
          class="pattern-grid-cell is-head"
        >
          {{ weekdayLabel(day) }}
        </div>

        <template v-for="row in gridRows" :key="row.key">
          <div class="pattern-grid-cell is-row-label">{{ row.label }}</div>
          <div
            v-for="day in weekdays"
            :key="`${row.key}-${day}`"
            class="pattern-grid-cell is-slot"
            :class="{
              'is-empty': !row.cells[day],
              'is-selected': !!row.cells[day] && isSelected(row.cells[day]!),
            }"
            :title="row.cells[day] ? cellTitle(row, day) : '该星期没有此节次'"
            @click="toggleCell(row, day)"
          >
            <template v-if="row.cells[day]">
              <span class="pattern-grid-slot-name">{{ row.cells[day]!.slotName }}</span>
              <span class="pattern-grid-slot-time">{{
                compactTimeRange(row.cells[day]!.startTime, row.cells[day]!.endTime)
              }}</span>
            </template>
          </div>
        </template>
      </div>
      <div v-if="gridRows.length === 0" class="education-empty">
        当前范围下暂无可用时间段，请先到「时间段管理」维护节次。
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 通用节次×星期网格选择器：行=节次(按时间段名称归并)，列=周一~周日，点选组成每周固定模式。
// 后续矩阵式 Excel 导入、规则设置等页面可复用同一套选点交互。
import { computed } from 'vue'
import type { TimeSlotSummary } from '@cornerstone/module-organization/api/organization'
import type { SchedulePlanSlotItem } from '@cornerstone/module-education/api/education'
import { compactTimeRange, weekdayLabel } from './schedule-utils'

interface PatternRow {
  key: string
  label: string
  order: number
  cells: Record<number, TimeSlotSummary | undefined>
}

const props = defineProps<{
  timeSlots: TimeSlotSummary[]
  modelValue: SchedulePlanSlotItem[]
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: SchedulePlanSlotItem[]): void
}>()

const weekdays = [1, 2, 3, 4, 5, 6, 7]

// 按 slotName 归并成行(同一节次名在不同星期是不同时间段行)，排序取 sortNo/开始时间
const gridRows = computed<PatternRow[]>(() => {
  const rows = new Map<string, PatternRow>()
  for (const slot of props.timeSlots) {
    if (!slot.weekday || !slot.id) {
      continue
    }
    const key = slot.slotName || `时段#${slot.id}`
    let row = rows.get(key)
    if (!row) {
      row = { key, label: key, order: slot.sortNo ?? Number.MAX_SAFE_INTEGER, cells: {} }
      rows.set(key, row)
    }
    const order = slot.sortNo ?? Number.MAX_SAFE_INTEGER
    row.order = Math.min(row.order, order)
    const existing = row.cells[slot.weekday]
    if (!existing || (existing.sortNo ?? Number.MAX_SAFE_INTEGER) > order) {
      row.cells[slot.weekday] = slot
    }
  }
  return Array.from(rows.values()).sort(
    (left, right) =>
      left.order - right.order ||
      left.label.localeCompare(right.label, 'zh-Hans-CN')
  )
})

const boardColumns = computed(() => `88px repeat(7, minmax(96px, 1fr))`)

function isSelected(slot: TimeSlotSummary) {
  return props.modelValue.some(
    (item) => item.timeSlotId === slot.id && item.weekday === slot.weekday
  )
}

function cellTitle(row: PatternRow, day: number) {
  const slot = row.cells[day]
  if (!slot) {
    return ''
  }
  return `${weekdayLabel(day)} ${slot.slotName || ''} ${compactTimeRange(slot.startTime, slot.endTime)}`
}

function toggleCell(row: PatternRow, day: number) {
  const slot = row.cells[day]
  if (!slot) {
    return
  }
  const exists = isSelected(slot)
  const next = props.modelValue.filter(
    (item) => !(item.timeSlotId === slot.id && item.weekday === day)
  )
  if (!exists) {
    next.push({ weekday: day, timeSlotId: slot.id })
  }
  next.sort((left, right) => left.weekday - right.weekday)
  emit('update:modelValue', next)
}
</script>

<style scoped>
.pattern-grid {
  display: grid;
  gap: 10px;
}

.pattern-grid-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pattern-grid-tip {
  color: var(--text-secondary);
  font-size: 12px;
}

.pattern-grid-count {
  margin-left: auto;
  color: var(--text-strong);
  font-size: 12px;
  font-weight: 600;
}

.pattern-grid-scroll {
  overflow-x: auto;
}

.pattern-grid-board {
  display: grid;
  gap: 6px;
  min-width: 760px;
}

.pattern-grid-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  min-height: 52px;
  padding: 6px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--card-bg);
  font-size: 12px;
}

.pattern-grid-cell.is-corner,
.pattern-grid-cell.is-head,
.pattern-grid-cell.is-row-label {
  background: var(--subtle-bg);
  color: var(--text-strong);
  font-weight: 600;
}

.pattern-grid-cell.is-slot {
  cursor: pointer;
  color: var(--text-secondary);
  transition:
    border-color 0.15s,
    background 0.15s;
}

.pattern-grid-cell.is-slot:hover {
  border-color: var(--hover-line);
}

.pattern-grid-cell.is-slot.is-empty {
  cursor: not-allowed;
  background: var(--subtle-bg);
  border-style: dashed;
}

.pattern-grid-cell.is-slot.is-selected {
  border-color: var(--accent);
  background: var(--hover-bg);
  color: var(--tint-ink);
  font-weight: 700;
}

.pattern-grid-slot-name {
  font-weight: inherit;
}

.pattern-grid-slot-time {
  color: var(--text-faint);
  font-size: 11px;
}

.pattern-grid-cell.is-selected .pattern-grid-slot-time {
  color: var(--tint-ink);
}
</style>
