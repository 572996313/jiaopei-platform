<template>
  <div class="ts-grid-wrap">
    <table class="ts-grid">
      <thead>
        <tr>
          <th class="ts-grid-corner">节次/星期</th>
          <th v-for="day in weekdays" :key="day" class="ts-grid-day">
            {{ weekdayText(day) }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.slotCode" :class="rowClass(row)">
          <th class="ts-grid-row-head">
            <el-input
              v-if="editable"
              v-model="row.slotName"
              size="small"
              class="ts-row-name-input"
              placeholder="节次名称"
            />
            <span v-else>{{ row.slotName }}</span>
            <div class="ts-row-meta">
              <span class="ts-row-code">{{ row.slotCode }}</span>
              <el-button
                v-if="editable"
                link
                type="danger"
                size="small"
                @click="$emit('remove-row', row)"
              >
                删除
              </el-button>
            </div>
          </th>
          <td
            v-for="day in weekdays"
            :key="day"
            class="ts-grid-cell"
            :class="{ 'is-conflict': conflicts.has(`${row.slotCode}|${day}`) }"
          >
            <template v-if="!editable">
              <span v-if="row.cells[day]">{{ cellText(row.cells[day]) }}</span>
              <span v-else class="ts-cell-empty">-</span>
            </template>
            <el-popover
              v-else
              trigger="click"
              placement="top"
              :width="230"
              popper-class="ts-cell-popover"
            >
              <template #reference>
                <div class="ts-cell-button">
                  <span v-if="row.cells[day]">{{ cellText(row.cells[day]) }}</span>
                  <span v-else class="ts-cell-empty">未排</span>
                </div>
              </template>
              <div class="ts-cell-editor">
                <div class="ts-cell-editor-row">
                  <span>开始</span>
                  <el-time-picker
                    :model-value="row.cells[day]?.start || ''"
                    value-format="HH:mm"
                    format="HH:mm"
                    size="small"
                    placeholder="选择"
                    style="width: 100px"
                    @update:model-value="(v: unknown) => setCellStart(row, day, String(v ?? ''))"
                  />
                </div>
                <div class="ts-cell-editor-row">
                  <span>结束</span>
                  <el-time-picker
                    :model-value="row.cells[day]?.end || ''"
                    value-format="HH:mm"
                    format="HH:mm"
                    size="small"
                    placeholder="选择"
                    style="width: 100px"
                    @update:model-value="(v: unknown) => setCellEnd(row, day, String(v ?? ''))"
                  />
                </div>
                <div class="ts-cell-editor-row ts-cell-editor-actions">
                  <span>{{ durationText(row.cells[day]) }}</span>
                  <el-button link type="danger" size="small" @click="clearCell(row, day)">
                    清空
                  </el-button>
                </div>
              </div>
            </el-popover>
          </td>
        </tr>
        <tr v-if="rows.length === 0">
          <td :colspan="weekdays.length + 1" class="ts-grid-empty">
            暂无节次,请先在左侧设置参数后点击「按参数生成」
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import type { SectionRow, TimeRange } from './time-slot-utils'
import { rangeDuration, toMinutes, toTimeText } from './time-slot-utils'

defineProps<{
  rows: SectionRow[]
  weekdays: number[]
  conflicts: Set<string>
  editable?: boolean
}>()

defineEmits<{
  'remove-row': [row: SectionRow]
}>()

function weekdayText(day: number) {
  return `星期${'一二三四五六日'[day - 1]}`
}

function rowClass(row: SectionRow) {
  return `ts-type-${row.slotType}`
}

function cellText(range: TimeRange) {
  return `${range.start} - ${range.end}`
}

function durationText(range: TimeRange | null | undefined) {
  if (!range || !range.start || !range.end) {
    return ''
  }
  return `时长 ${rangeDuration(range)} 分钟`
}

function setCellStart(row: SectionRow, day: number, value: string) {
  const current = row.cells[day]
  const start = toMinutes(value)
  if (Number.isNaN(start)) {
    if (current) {
      row.cells[day] = null
    }
    return
  }
  const duration =
    current && current.start && current.end ? Math.max(rangeDuration(current), 1) : 45
  row.cells[day] = { start: value, end: toTimeText(start + duration) }
}

function setCellEnd(row: SectionRow, day: number, value: string) {
  const current = row.cells[day]
  const end = toMinutes(value)
  const start = toMinutes(current?.start || '')
  if (Number.isNaN(end) || Number.isNaN(start) || end <= start) {
    return
  }
  row.cells[day] = { start: current!.start, end: value }
}

function clearCell(row: SectionRow, day: number) {
  row.cells[day] = null
}
</script>

<style scoped>
.ts-grid-wrap {
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.ts-grid {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 13px;
}

.ts-grid th,
.ts-grid td {
  border: 1px solid var(--el-border-color-lighter);
  padding: 6px 8px;
  text-align: center;
}

.ts-grid thead th {
  background: var(--el-fill-color-light);
  font-weight: 600;
}

.ts-grid-corner {
  width: 130px;
}

.ts-grid-row-head {
  background: var(--el-fill-color-lighter);
  font-weight: 600;
}

.ts-row-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 2px;
}

.ts-row-code {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  font-weight: 400;
}

.ts-row-name-input {
  width: 100%;
}

.ts-cell-button {
  cursor: pointer;
  min-height: 20px;
  border-radius: 4px;
}

.ts-cell-button:hover {
  outline: 1px dashed var(--el-color-primary);
}

.ts-cell-empty {
  color: var(--el-text-color-placeholder);
}

.ts-grid-cell.is-conflict {
  background: var(--el-color-danger-light-8);
  color: var(--el-color-danger);
}

.ts-grid-empty {
  color: var(--el-text-color-secondary);
  padding: 24px 0;
}

/* 按节次类型着色,贴近校历课表观感 */
.ts-grid tr.ts-type-morning_study td {
  background: #d9f2f7;
}

.ts-grid tr.ts-type-teaching td {
  background: #fdf3cf;
}

.ts-grid tr.ts-type-self_study td {
  background: #dfe2ef;
}

.ts-grid tr.ts-type-break td {
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-secondary);
}
</style>
