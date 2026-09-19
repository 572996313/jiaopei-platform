import type {
  TimeSlotApplySlotPayload,
  TimeSlotBigBreakConfig,
  TimeSlotConfig,
  TimeSlotSectionConfig,
  TimeSlotSummary,
} from '@/api/organization'

/** 单元格时间区间(HH:mm) */
export interface TimeRange {
  start: string
  end: string
}

/** 网格一行:一个节次在各星期上的时间(空表示该天不上此节) */
export interface SectionRow {
  slotCode: string
  slotName: string
  slotType: string
  sortNo: number
  cells: Record<number, TimeRange | null>
}

export const WEEKDAY_OPTIONS = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' },
]

interface SectionGroupMeta {
  key: 'morningStudy' | 'morning' | 'afternoon' | 'eveningStudy'
  bigBreakKey?: 'morningBigBreak' | 'afternoonBigBreak'
  codePrefix: string
  slotType: string
  label: string
  sortBase: number
  nameOf: (index: number, count: number) => string
}

export const SECTION_GROUPS: SectionGroupMeta[] = [
  {
    key: 'morningStudy',
    codePrefix: 'MS',
    slotType: 'morning_study',
    label: '早自习',
    sortBase: 100,
    nameOf: (index, count) => (count > 1 ? `早${index}` : '早自习'),
  },
  {
    key: 'morning',
    bigBreakKey: 'morningBigBreak',
    codePrefix: 'AM',
    slotType: 'teaching',
    label: '上午',
    sortBase: 200,
    nameOf: (index) => `上${index}`,
  },
  {
    key: 'afternoon',
    bigBreakKey: 'afternoonBigBreak',
    codePrefix: 'PM',
    slotType: 'teaching',
    label: '下午',
    sortBase: 300,
    nameOf: (index) => `下${index}`,
  },
  {
    key: 'eveningStudy',
    codePrefix: 'ES',
    slotType: 'self_study',
    label: '晚自习',
    sortBase: 400,
    nameOf: (index, count) => (count > 1 ? `晚${index}` : '晚自习'),
  },
]

export function slotTypeLabel(value?: string | null) {
  switch (value) {
    case 'morning_study':
      return '早自习'
    case 'teaching':
      return '授课'
    case 'break':
      return '课间休息'
    case 'self_study':
      return '晚自习'
    default:
      return value || '-'
  }
}

export function defaultTimeSlotConfig(): TimeSlotConfig {
  return {
    morningStudy: { count: 1, durationMinutes: 20, breakMinutes: 10, startTime: '07:50' },
    morning: { count: 4, durationMinutes: 45, breakMinutes: 10, startTime: '08:00' },
    morningBigBreak: { minutes: 0, afterSection: 0 },
    afternoon: { count: 3, durationMinutes: 45, breakMinutes: 10, startTime: '14:00' },
    afternoonBigBreak: { minutes: 0, afterSection: 0 },
    eveningStudy: { count: 2, durationMinutes: 45, breakMinutes: 10, startTime: '19:00' },
    weekdays: [1, 2, 3, 4, 5],
  }
}

/** 后端 config 为自由 JSON,这里做形状校验,不合规则回落默认值 */
export function asTimeSlotConfig(value: unknown): TimeSlotConfig {
  const fallback = defaultTimeSlotConfig()
  if (!value || typeof value !== 'object') {
    return fallback
  }
  const raw = value as Partial<TimeSlotConfig>
  const section = (item?: Partial<TimeSlotSectionConfig>): TimeSlotSectionConfig => ({
    count: clampInt(item?.count, 0, 12, fallback.morning.count),
    durationMinutes: clampInt(item?.durationMinutes, 1, 600, 45),
    breakMinutes: clampInt(item?.breakMinutes, 0, 240, 10),
    startTime: normalizeTimeText(item?.startTime) || '08:00',
  })
  const bigBreak = (item?: Partial<TimeSlotBigBreakConfig>): TimeSlotBigBreakConfig => ({
    minutes: clampInt(item?.minutes, 0, 240, 0),
    afterSection: clampInt(item?.afterSection, 0, 12, 0),
  })
  const weekdays = Array.isArray(raw.weekdays)
    ? raw.weekdays.filter((day) => Number.isInteger(day) && day >= 1 && day <= 7)
    : [...fallback.weekdays]
  return {
    morningStudy: section(raw.morningStudy),
    morning: section(raw.morning),
    morningBigBreak: bigBreak(raw.morningBigBreak),
    afternoon: section(raw.afternoon),
    afternoonBigBreak: bigBreak(raw.afternoonBigBreak),
    eveningStudy: section(raw.eveningStudy),
    weekdays: weekdays.length > 0 ? weekdays.sort((a, b) => a - b) : [...fallback.weekdays],
  }
}

function clampInt(value: unknown, min: number, max: number, fallback: number): number {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return fallback
  }
  return Math.min(max, Math.max(min, Math.round(value)))
}

/** "07:55:00" / "7:55" -> 分钟数;非法返回 NaN */
export function toMinutes(time?: string | null): number {
  if (!time) {
    return Number.NaN
  }
  const match = /^(\d{1,2}):(\d{2})/.exec(time)
  if (!match) {
    return Number.NaN
  }
  const hours = Number(match[1])
  const minutes = Number(match[2])
  if (hours > 23 || minutes > 59) {
    return Number.NaN
  }
  return hours * 60 + minutes
}

export function toTimeText(minutes: number): string {
  const wrapped = ((minutes % 1440) + 1440) % 1440
  const hours = Math.floor(wrapped / 60)
  const rest = wrapped % 60
  return `${String(hours).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
}

export function normalizeTimeText(time?: string | null): string {
  const minutes = toMinutes(time)
  return Number.isNaN(minutes) ? '' : toTimeText(minutes)
}

export function rangeDuration(range: TimeRange | null): number {
  if (!range) {
    return 0
  }
  return toMinutes(range.end) - toMinutes(range.start)
}

/** 按节次参数推算全部单元格时间(所有选中星期共用同一套节次) */
export function generateRowsFromConfig(config: TimeSlotConfig): SectionRow[] {
  const rows: SectionRow[] = []
  for (const group of SECTION_GROUPS) {
    const section = config[group.key]
    const bigBreak = group.bigBreakKey ? config[group.bigBreakKey] : undefined
    let cursor = toMinutes(section.startTime)
    for (let index = 1; index <= section.count; index++) {
      if (Number.isNaN(cursor)) {
        break
      }
      const start = cursor
      const end = start + section.durationMinutes
      const gap =
        bigBreak && bigBreak.minutes > 0 && bigBreak.afterSection === index
          ? bigBreak.minutes
          : section.breakMinutes
      const cells: Record<number, TimeRange | null> = {}
      for (const weekday of config.weekdays) {
        cells[weekday] = { start: toTimeText(start), end: toTimeText(end) }
      }
      rows.push({
        slotCode: `${group.codePrefix}${index}`,
        slotName: group.nameOf(index, section.count),
        slotType: group.slotType,
        sortNo: group.sortBase + index,
        cells,
      })
      cursor = end + gap
    }
  }
  return rows
}

/** 把已保存的时间段行(后端返回)还原为网格行 */
export function buildRowsFromSlots(slots: TimeSlotSummary[]): SectionRow[] {
  const groups = new Map<string, SectionRow>()
  for (const slot of slots) {
    if (!slot.slotCode || !slot.weekday || !slot.startTime || !slot.endTime) {
      continue
    }
    let row = groups.get(slot.slotCode)
    if (!row) {
      row = {
        slotCode: slot.slotCode,
        slotName: slot.slotName || slot.slotCode,
        slotType: slot.slotType || 'teaching',
        sortNo: slot.sortNo ?? 999,
        cells: {},
      }
      groups.set(slot.slotCode, row)
    }
    row.cells[slot.weekday] = {
      start: normalizeTimeText(slot.startTime),
      end: normalizeTimeText(slot.endTime),
    }
  }
  return [...groups.values()].sort((a, b) => a.sortNo - b.sortNo)
}

/** 网格行 -> apply 接口入参(跳过空单元格) */
export function rowsToApplySlots(rows: SectionRow[]): TimeSlotApplySlotPayload[] {
  const slots: TimeSlotApplySlotPayload[] = []
  for (const row of rows) {
    for (const [weekdayText, range] of Object.entries(row.cells)) {
      if (!range || !range.start || !range.end) {
        continue
      }
      slots.push({
        weekday: Number(weekdayText),
        slotCode: row.slotCode,
        slotName: row.slotName,
        startTime: `${range.start}:00`,
        endTime: `${range.end}:00`,
        slotType: row.slotType,
        sortNo: row.sortNo,
      })
    }
  }
  return slots
}

/** 找出同一星期列内时间重叠的单元格,返回 `${slotCode}|${weekday}` 集合 */
export function findCellConflicts(rows: SectionRow[], weekdays: number[]): Set<string> {
  const conflicts = new Set<string>()
  for (const weekday of weekdays) {
    const cells = rows
      .map((row) => ({ row, range: row.cells[weekday] }))
      .filter(
        (item): item is { row: SectionRow; range: TimeRange } =>
          Boolean(item.range && item.range.start && item.range.end)
      )
      .sort((a, b) => toMinutes(a.range.start) - toMinutes(b.range.start))
    for (let i = 1; i < cells.length; i++) {
      if (toMinutes(cells[i].range.start) < toMinutes(cells[i - 1].range.end)) {
        conflicts.add(`${cells[i].row.slotCode}|${weekday}`)
        conflicts.add(`${cells[i - 1].row.slotCode}|${weekday}`)
      }
    }
  }
  return conflicts
}
