<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useDictStore } from '@/stores/dict'

const props = withDefaults(
  defineProps<{
    /** 字典类型编码,如 room_type / term_season / teaching_method */
    typeCode: string
    placeholder?: string
    clearable?: boolean
    /** 历史值不在字典中时,是否追加一个兜底选项回显原值 */
    keepUnknownValue?: boolean
  }>(),
  {
    placeholder: '请选择',
    clearable: true,
    keepUnknownValue: true,
  },
)

const modelValue = defineModel<string | null>()

const dictStore = useDictStore()

onMounted(() => {
  void dictStore.ensureLoaded([props.typeCode])
})

watch(
  () => props.typeCode,
  (code) => {
    void dictStore.ensureLoaded([code])
  },
)

const options = computed(() =>
  dictStore
    .optionsOf(props.typeCode)
    .filter((item) => item.status !== 'disabled')
    .map((item) => ({ value: item.itemValue, label: item.itemName })),
)

const mergedOptions = computed(() => {
  const current = modelValue.value
  if (!props.keepUnknownValue || !current || options.value.some((option) => option.value === current)) {
    return options.value
  }
  return [{ value: current, label: current }, ...options.value]
})
</script>

<template>
  <!-- clear 时 el-select 可能发出 undefined,统一归一为空串,避免上层对空值调用 trim 崩溃 -->
  <el-select
    v-model="modelValue"
    :placeholder="props.placeholder"
    :clearable="props.clearable"
    filterable
    @clear="modelValue = ''"
  >
    <el-option v-for="option in mergedOptions" :key="option.value" :label="option.label" :value="option.value" />
  </el-select>
</template>
