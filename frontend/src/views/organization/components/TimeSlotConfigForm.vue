<template>
  <el-form label-position="top" class="ts-config-form" :disabled="disabled">
    <div class="ts-config-groups">
      <fieldset v-for="group in groupDefs" :key="group.key" class="ts-config-group">
        <legend>{{ group.label }}</legend>
        <div class="ts-config-fields">
          <div class="ts-field">
            <span class="ts-field-label">节数</span>
            <el-input-number
              v-model="form[group.key].count"
              :min="0"
              :max="12"
              size="small"
              controls-position="right"
            />
          </div>
          <div class="ts-field">
            <span class="ts-field-label">课程时长(分)</span>
            <el-input-number
              v-model="form[group.key].durationMinutes"
              :min="1"
              :max="600"
              :step="5"
              size="small"
              controls-position="right"
            />
          </div>
          <div class="ts-field">
            <span class="ts-field-label">课间时长(分)</span>
            <el-input-number
              v-model="form[group.key].breakMinutes"
              :min="0"
              :max="240"
              :step="5"
              size="small"
              controls-position="right"
            />
          </div>
          <div class="ts-field">
            <span class="ts-field-label">首节开始</span>
            <el-time-picker
              v-model="form[group.key].startTime"
              value-format="HH:mm"
              format="HH:mm"
              size="small"
              placeholder="时间"
              style="width: 100px"
            />
          </div>
        </div>
        <div v-if="group.bigBreakKey" class="ts-config-fields ts-big-break">
          <div class="ts-field">
            <span class="ts-field-label">大课间时长(分)</span>
            <el-input-number
              v-model="form[group.bigBreakKey].minutes"
              :min="0"
              :max="240"
              :step="5"
              size="small"
              controls-position="right"
            />
          </div>
          <div class="ts-field">
            <span class="ts-field-label">大课间位置(第几节后)</span>
            <el-select
              v-model="form[group.bigBreakKey].afterSection"
              size="small"
              style="width: 110px"
            >
              <el-option label="不设置" :value="0" />
              <el-option
                v-for="index in form[group.key].count"
                :key="index"
                :label="`第${index}节后`"
                :value="index"
              />
            </el-select>
          </div>
        </div>
      </fieldset>
    </div>

    <fieldset class="ts-config-group">
      <legend>套用星期</legend>
      <el-checkbox-group v-model="form.weekdays" class="ts-weekday-group">
        <el-checkbox v-for="day in WEEKDAY_OPTIONS" :key="day.value" :value="day.value">
          {{ day.label }}
        </el-checkbox>
      </el-checkbox-group>
      <div class="ts-weekday-shortcuts">
        <el-button link type="primary" size="small" @click="setWeekdays([1, 2, 3, 4, 5])">
          周一~周五
        </el-button>
        <el-button link type="primary" size="small" @click="setWeekdays([1, 2, 3, 4, 5, 6, 7])">
          周一~周日
        </el-button>
        <el-button link type="primary" size="small" @click="setWeekdays([6, 7])">周末</el-button>
      </div>
    </fieldset>
  </el-form>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { TimeSlotConfig } from '@/api/organization'
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

.ts-config-groups {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 12px;
}

.ts-config-group {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 10px 12px;
  margin: 0 0 12px;
}

.ts-config-group legend {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  padding: 0 6px;
}

.ts-config-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
}

.ts-big-break {
  margin-top: 8px;
}

.ts-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ts-field-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ts-weekday-group {
  display: flex;
  flex-wrap: wrap;
  gap: 0 8px;
}

.ts-weekday-shortcuts {
  margin-top: 4px;
  display: flex;
  gap: 8px;
}
</style>
