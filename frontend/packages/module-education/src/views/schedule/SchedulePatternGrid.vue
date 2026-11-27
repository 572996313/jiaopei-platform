<template>
  <div class="pattern-grid">
    <div class="pattern-grid-toolbar">
      <span class="pattern-grid-tip">点击单元格选择每周固定节次，再点一次取消</span>
      <span v-if="hasVaried" class="pattern-grid-tip">● 表示这一天的开始时间与本行其他天不同</span>
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
              'is-varied': !!row.cells[day] && isVaried(row, row.cells[day]!),
            }"
            :title="row.cells[day] ? cellTitle(row, day) : '该星期没有此节次'"
            @click="toggleCell(row, day)"
          >
            <template v-if="row.cells[day]">
              <span class="pattern-grid-slot-name">{{ timeSlotShortName(row.cells[day]!.slotName) }}</span>
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
import {
  compactTimeRange,
  timeSlotShortName,
  weekdayLabel,
} from '@cornerstone/module-education/utils/schedule'

interface PatternRow {
  key: string
  label: string
  order: number
  /** 机构通用节次(campus_id 为空)与本校区节次同名时用来区分两块行 */
  generic: boolean
  /** 本行各天里出现最多的开始时间,用来标出"这天时间不一样"的格子 */
  baseStart: string
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

// 行身份用「模板 + 节次编码」:编码跨星期稳定(MS1/AM1/PM1…),而名称是操作员手填的,
// 按名称归并会在"周几+节次"式命名下把一行拆成 7 行、每行只剩一个格子。
const gridRows = computed<PatternRow[]>(() => {
  const rows = new Map<string, PatternRow>()
  for (const slot of props.timeSlots) {
    if (!slot.weekday || !slot.id) {
      continue
    }
    const order = slot.sortNo ?? Number.MAX_SAFE_INTEGER
    const generic = !slot.campusId
    const key = `${generic ? 'G' : 'C'}::${slot.templateId ?? ''}::${slot.slotCode || slot.slotName || `时段#${slot.id}`}`
    let row = rows.get(key)
    if (!row) {
      row = { key, label: '', order, generic, cells: {}, baseStart: '' }
      rows.set(key, row)
    }
    row.order = Math.min(row.order, order)
    const existing = row.cells[slot.weekday]
    if (!existing || (existing.sortNo ?? Number.MAX_SAFE_INTEGER) > order) {
      row.cells[slot.weekday] = slot
    }
  }
  const list = Array.from(rows.values())
  for (const row of list) {
    const days = weekdays.filter((day) => row.cells[day])
    // 名称取最早那天(周一~周日里最先出现的),星期前缀由列头承担
    const first = days.length > 0 ? row.cells[days[0]] : undefined
    row.label = timeSlotShortName(first?.slotName) || '未命名节次'
    row.baseStart = dominantStart(days.map((day) => row.cells[day]?.startTime))
  }
  // 机构通用节次与本校区节次同名时并列成两块,不加区分就成了两行"上1"不知道点哪个
  const labelCount = new Map<string, number>()
  for (const row of list) {
    labelCount.set(row.label, (labelCount.get(row.label) || 0) + 1)
  }
  for (const row of list) {
    if ((labelCount.get(row.label) || 0) > 1) {
      row.label = `${row.label}（${row.generic ? '机构通用' : '本校区'}）`
    }
  }
  return list.sort(
    (left, right) =>
      left.order - right.order || left.label.localeCompare(right.label, 'zh-Hans-CN')
  )
})

function dominantStart(times: Array<string | null | undefined>) {
  const counts = new Map<string, number>()
  for (const time of times) {
    const value = time?.slice(0, 5)
    if (!value) {
      continue
    }
    counts.set(value, (counts.get(value) || 0) + 1)
  }
  let best = ''
  let bestCount = 0
  for (const [value, count] of counts) {
    if (count > bestCount) {
      best = value
      bestCount = count
    }
  }
  return best
}

const boardColumns = computed(() => `88px repeat(7, minmax(96px, 1fr))`)

function isSelected(slot: TimeSlotSummary) {
  return props.modelValue.some(
    (item) => item.timeSlotId === slot.id && item.weekday === slot.weekday
  )
}

function isVaried(row: PatternRow, slot: TimeSlotSummary) {
  const start = slot.startTime?.slice(0, 5)
  return Boolean(row.baseStart && start && start !== row.baseStart)
}

const hasVaried = computed(() =>
  gridRows.value.some((row) =>
    weekdays.some((day) => row.cells[day] && isVaried(row, row.cells[day]!))
  )
)

function cellTitle(row: PatternRow, day: number) {
  const slot = row.cells[day]
  if (!slot) {
    return ''
  }
  const varied = isVaried(row, slot) ? `（本行其他天是 ${row.baseStart} 开始）` : ''
  return `${weekdayLabel(day)} ${row.label} ${compactTimeRange(slot.startTime, slot.endTime)}${varied}`
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
  position: relative;
  cursor: pointer;
  color: var(--text-secondary);
  transition:
    border-color 0.15s,
    background 0.15s;
}

/* 同一节次在这天的开始时间与本行其他天不同 */
.pattern-grid-cell.is-slot.is-varied::after {
  content: '';
  position: absolute;
  top: 5px;
  right: 5px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--accent);
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
