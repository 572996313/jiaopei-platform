<template>
  <el-form class="ts-config-form" :disabled="disabled" @submit.prevent>
    <!--
      四个时段的参数排成一张小表:列头只写一次,四行对齐。
      竖排标签或按组分栏都会让控件错位换行,既难看又吃高度——这页下面还有十行网格要留给它。
    -->
    <div class="ts-param-grid">
      <div class="ts-param-row ts-param-head">
        <span class="ts-param-cell ts-param-label">时段</span>
        <span class="ts-param-cell">节数</span>
        <span class="ts-param-cell">时长(分)</span>
        <span class="ts-param-cell">课间(分)</span>
        <span class="ts-param-cell">首节开始</span>
        <span class="ts-param-cell">大课间(分)</span>
        <span class="ts-param-cell">大课间位置</span>
      </div>

      <div v-for="group in groupDefs" :key="group.key" class="ts-param-row">
        <span class="ts-param-cell ts-param-label">{{ group.label }}</span>
        <span class="ts-param-cell">
          <el-input-number
            v-model="form[group.key].count"
            :min="0"
            :max="12"
            size="small"
            controls-position="right"
          />
        </span>
        <span class="ts-param-cell">
          <el-input-number
            v-model="form[group.key].durationMinutes"
            :min="1"
            :max="600"
            :step="5"
            size="small"
            controls-position="right"
          />
        </span>
        <span class="ts-param-cell">
          <el-input-number
            v-model="form[group.key].breakMinutes"
            :min="0"
            :max="240"
            :step="5"
            size="small"
            controls-position="right"
          />
        </span>
        <span class="ts-param-cell">
          <el-time-picker
            v-model="form[group.key].startTime"
            value-format="HH:mm"
            format="HH:mm"
            size="small"
            placeholder="时间"
          />
        </span>
        <template v-if="group.bigBreakKey">
          <span class="ts-param-cell">
            <el-input-number
              v-model="form[group.bigBreakKey].minutes"
              :min="0"
              :max="240"
              :step="5"
              size="small"
              controls-position="right"
            />
          </span>
          <span class="ts-param-cell">
            <el-select v-model="form[group.bigBreakKey].afterSection" size="small">
              <el-option label="不设置" :value="0" />
              <el-option
                v-for="index in form[group.key].count"
                :key="index"
                :label="`第${index}节后`"
                :value="index"
              />
            </el-select>
          </span>
        </template>
        <template v-else>
          <span class="ts-param-cell ts-param-na">—</span>
          <span class="ts-param-cell ts-param-na">—</span>
        </template>
      </div>

      <div class="ts-param-row ts-param-weekday-row">
        <span class="ts-param-cell ts-param-label">套用星期</span>
        <div class="ts-param-cell ts-param-weekday-cell">
          <el-checkbox-group v-model="form.weekdays" class="ts-weekday-group">
            <el-checkbox v-for="day in WEEKDAY_OPTIONS" :key="day.value" :value="day.value">
              {{ day.label }}
            </el-checkbox>
          </el-checkbox-group>
          <span class="ts-weekday-shortcuts">
            <el-button link type="primary" size="small" @click="setWeekdays([1, 2, 3, 4, 5])">
              周一~周五
            </el-button>
            <el-button link type="primary" size="small" @click="setWeekdays([1, 2, 3, 4, 5, 6, 7])">
              周一~周日
            </el-button>
            <el-button link type="primary" size="small" @click="setWeekdays([6, 7])">周末</el-button>
          </span>
        </div>
      </div>
    </div>
  </el-form>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { TimeSlotConfig } from '@cornerstone/module-organization/api/organization'
import { SECTION_GROUPS, WEEKDAY_OPTIONS } from './time-slot-utils'

const props = defineProps<{
  modelValue: TimeSlotConfig
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: TimeSlotConfig]
}>()

const groupDefs = SECTION_GROUPS.map((group) => ({
  key: group.key,
  label: group.label,
  bigBreakKey: group.bigBreakKey,
}))

const form = reactive<TimeSlotConfig>(JSON.parse(JSON.stringify(props.modelValue)))

/** 外部回填时抑制一次回写,避免父子组件互相触发 */
let syncing = false

watch(
  () => props.modelValue,
  (value) => {
    syncing = true
    Object.assign(form, JSON.parse(JSON.stringify(value)))
    queueMicrotask(() => {
      syncing = false
    })
  },
  { deep: true }
)

watch(
  form,
  () => {
    if (!syncing) {
      emit('update:modelValue', JSON.parse(JSON.stringify(form)))
    }
  },
  { deep: true }
)

function setWeekdays(days: number[]) {
  form.weekdays = [...days]
}
</script>

<style scoped>
.ts-config-form {
  width: 100%;
}

/* 首列给时段名(晚自习三字),其余六列等宽;整块限宽,不让输入框铺满整卡 */
.ts-param-grid {
  max-width: 880px;
}

.ts-param-row {
  display: grid;
  grid-template-columns: 88px repeat(6, minmax(0, 1fr));
  align-items: center;
  gap: 2px 10px;
}

.ts-param-row + .ts-param-row {
  margin-top: 4px;
}

.ts-param-head {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  padding-bottom: 4px;
  border-bottom: 1px solid var(--line);
  margin-bottom: 2px;
}

.ts-param-cell {
  min-width: 0;
}

.ts-param-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.ts-param-na {
  color: var(--el-text-color-placeholder);
  text-align: center;
}

.ts-param-cell :deep(.el-input-number),
.ts-param-cell :deep(.el-select),
.ts-param-cell :deep(.el-date-editor) {
  width: 100%;
  min-width: 0;
}

.ts-param-weekday-row {
  margin-top: 4px;
  padding-top: 4px;
  border-top: 1px solid var(--line);
}

.ts-param-weekday-cell {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0 12px;
  /* 限宽那一列只占首列,星期要铺满整行 */
  grid-column: 2 / -1;
}

.ts-weekday-group {
  display: flex;
  flex-wrap: wrap;
  gap: 0 6px;
}

.ts-weekday-group :deep(.el-checkbox) {
  margin-right: 0;
}

.ts-weekday-shortcuts {
  display: flex;
  gap: 8px;
  margin-left: auto;
}
</style>
