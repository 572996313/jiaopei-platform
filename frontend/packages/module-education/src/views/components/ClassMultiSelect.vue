<template>
  <el-select
    :model-value="modelValue"
    :placeholder="placeholder"
    :clearable="clearable"
    :disabled="disabled"
    multiple
    collapse-tags
    collapse-tags-tooltip
    filterable
    remote
    reserve-keyword
    :remote-method="search"
    :loading="loading"
    style="width: 100%"
    @update:model-value="handleInput"
    @visible-change="handleVisibleChange"
  >
    <el-option v-for="item in visibleOptions" :key="item.id" :label="item.label" :value="item.id" />
  </el-select>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { classKnownOf, classLabelOf, classOptionLabel, ensureClassKnown } from '@cornerstone/module-education/composables/classLabels'
import { useClassOptions, type ClassScopeValue } from '@cornerstone/module-education/composables/useClassOptions'

const props = withDefaults(
  defineProps<{
    modelValue?: string[] | null
    /** 机构 id：小号机构（如种子数据里的 1）不到 16 位，大 ID 转字符串那步不会命中，运行期可能是数字 */
    institutionId?: ClassScopeValue
    campusId?: ClassScopeValue
    termId?: ClassScopeValue
    placeholder?: string
    clearable?: boolean
    disabled?: boolean
  }>(),
  {
    modelValue: () => [],
    institutionId: null,
    campusId: null,
    termId: null,
    placeholder: '输入班级名称或编码搜索班级',
    clearable: true,
    disabled: false,
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
}>()

const { options, loading, search, handleVisibleChange } = useClassOptions(() => ({
  institutionId: props.institutionId,
  campusId: props.campusId,
  termId: props.termId,
}))

const visibleOptions = computed(() => {
  const list = options.value.map((item) => ({ id: item.id, label: classOptionLabel(item) }))
  // 已勾选但不在当前这批搜索结果里的班（换关键字后很常见），名称钉在列表前面，
  // 否则标签上会退化成裸 ID
  const missing = (props.modelValue || []).filter((id) => !list.some((item) => item.id === id))
  return [...missing.map((id) => ({ id, label: classLabelOf(id) })), ...list]
})

function handleInput(value: string[]) {
  emit('update:modelValue', Array.isArray(value) ? value : [])
}

// 勾选过的班不在当前搜索结果里时，名称靠缓存；缓存也没有就按 id 补取，否则标签显示成裸 ID
watch(
  () => props.modelValue,
  (ids) => {
    ;(ids || []).forEach((id) => {
      if (!classKnownOf(id)) {
        void ensureClassKnown(props.institutionId, id)
      }
    })
  },
  { immediate: true }
)
</script>
