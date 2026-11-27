<template>
  <el-form-item label="课时目标">
    <div class="lesson-target-row">
      <el-select v-model="mode" class="lesson-target-mode">
        <el-option label="每周" value="weekly" />
        <el-option label="每月" value="monthly" />
      </el-select>
      <el-input-number
        v-model="input"
        :min="1"
        :step="1"
        :controls="false"
        placeholder="未设定"
        class="lesson-target-value"
      />
      <span class="lesson-target-unit">{{ monthly ? '节 / 月' : '节 / 周' }}</span>
    </div>
    <div class="education-context-text">{{ hint }}</div>
  </el-form-item>
  <el-form-item label="整班总课时">
    <div class="lesson-target-row">
      <el-input-number
        v-model="total"
        :min="1"
        :step="1"
        :controls="false"
        placeholder="未设定"
        class="lesson-target-value"
      />
      <span class="lesson-target-unit">节</span>
    </div>
    <div class="education-context-text">
      这一班合同/教学计划里一共上多少节。填了才在「课时完成度」页显示整班进度，不参与每周应排节数的计算。
    </div>
  </el-form-item>
</template>

<script setup lang="ts">
// 班级课时目标的一整段表单字段（新增与编辑弹窗共用）。
// 操作员手上常没有"每周几节"这个数：合同写"一共 40 节"、收费写"一个月 8 次"，
// 所以让 ta 按自己那份台账的口径填，折算成每周节数由系统做，并把过程摊在回显里。
import { computed } from 'vue'

const props = defineProps<{
  mode: string
  input: number | null
  total: number | null
}>()

const emit = defineEmits<{
  'update:mode': [value: string]
  'update:input': [value: number | null]
  'update:total': [value: number | null]
}>()

const mode = computed({
  get: () => props.mode,
  set: (value: string) => emit('update:mode', value),
})
const input = computed({
  get: () => props.input,
  set: (value: number | null) => emit('update:input', value),
})
const total = computed({
  get: () => props.total,
  set: (value: number | null) => emit('update:total', value),
})

const monthly = computed(() => mode.value === 'monthly')

// 与后端 LessonTargetConverter 同一个式子：每月节数 × 12 ÷ 52 四舍五入。
// 52 写成整数而不是除以 4.33，是为了让回显里那串数字操作员拿计算器按得出来；
// 两边必须一致，否则保存前看到的和保存后报表里的是两个数。
const weekly = computed(() =>
  monthly.value ? Math.round(((input.value ?? 0) * 12) / 52) : (input.value ?? 0),
)

const hint = computed(() => {
  const value = input.value
  if (value === null || value === undefined) {
    return '留空表示未设定；课时完成度会回落到该班排课计划的展开口径。'
  }
  if (!monthly.value) {
    return `每周 ${value} 节，自动补排和课时完成度都按这个数算。`
  }
  const exact = ((value * 12) / 52).toFixed(2)
  if (weekly.value >= 1) {
    return `每月 ${value} 节 ≈ 每周 ${weekly.value} 节（${value}×12÷52=${exact}，四舍五入），报表按每周 ${weekly.value} 节算。`
  }
  return `每月 ${value} 节摊到一周不足 1 节（${value}×12÷52=${exact}），按"未设定周目标"处理：课时完成度回落到排课计划展开口径，不报错。`
})
</script>

<style scoped>
.lesson-target-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.lesson-target-mode {
  flex: 0 0 88px;
}

.lesson-target-value {
  flex: 1 1 auto;
  min-width: 0;
}

.lesson-target-unit {
  flex: 0 0 auto;
  color: var(--text-secondary);
}
</style>
