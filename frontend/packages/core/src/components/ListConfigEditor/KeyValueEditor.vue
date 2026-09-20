<template>
  <div class="kv-editor">
    <div v-for="(pair, index) in items" :key="index" class="kv-row">
      <el-input v-model="pair.key" :placeholder="keyPlaceholder" size="small" class="kv-key" />
      <el-input
        v-if="!useValueSelect"
        v-model="pair.value"
        :placeholder="valuePlaceholder"
        size="small"
        class="kv-value"
      />
      <el-select
        v-else
        v-model="pair.value"
        :placeholder="valuePlaceholder"
        size="small"
        clearable
        class="kv-value"
      >
        <el-option
          v-for="opt in valueOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
      <el-button text type="danger" size="small" class="kv-remove" @click="removeRow(index)"
        >删除</el-button
      >
    </div>
    <el-button size="small" type="primary" plain @click="addRow">添加一行</el-button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { KeyValuePair } from './types'

const props = withDefaults(
  defineProps<{
    /** 左列(key / 选项值)占位提示 */
    keyPlaceholder?: string
    /** 右列(value / 显示文本或样式)占位提示 */
    valuePlaceholder?: string
    /** 传入时右列变为下拉选择(如 tag 颜色),否则为文本框 */
    valueOptions?: Array<{ value: string; label: string }>
  }>(),
  {
    keyPlaceholder: '键',
    valuePlaceholder: '值',
    valueOptions: () => [],
  }
)

const modelValue = defineModel<KeyValuePair[] | undefined>()

/** 迭代的展示数组(避免 undefined 时模板告警) */
const items = computed<KeyValuePair[]>(() => modelValue.value ?? [])

const useValueSelect = computed(() => props.valueOptions.length > 0)

function addRow() {
  if (!modelValue.value) {
    modelValue.value = []
  }
  modelValue.value.push({ key: '', value: '' })
}

function removeRow(index: number) {
  modelValue.value?.splice(index, 1)
}
</script>

<style scoped>
.kv-editor {
  display: grid;
  gap: 8px;
  width: 100%;
}

.kv-row {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 8px;
  align-items: center;
}

.kv-key,
.kv-value {
  min-width: 0;
}

.kv-remove {
  flex: none;
}
</style>
