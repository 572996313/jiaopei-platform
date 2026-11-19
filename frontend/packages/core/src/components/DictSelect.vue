<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { SelectInstance } from 'element-plus'
import { useDictStore } from '@cornerstone/core/stores/dict'

const props = withDefaults(
  defineProps<{
    /** 字典类型编码, snake_case，如 order_status / pay_channel */
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
  }
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
  }
)

const options = computed(() =>
  dictStore
    .optionsOf(props.typeCode)
    .filter((item) => item.status !== 'disabled')
    .map((item) => ({ value: item.itemValue, label: item.itemName }))
)

const mergedOptions = computed(() => {
  const current = modelValue.value
  if (
    !props.keepUnknownValue ||
    !current ||
    options.value.some((option) => option.value === current)
  ) {
    return options.value
  }
  return [{ value: current, label: current }, ...options.value]
})

const selectRef = ref<SelectInstance>()
/** 下拉当前是否展开：el-select 的展开态判断在自己内部，这里备一份给点击拦截用 */
const menuVisible = ref(false)

/**
 * filterable 的 el-select 在已经展开时，点输入区的那一下会被它自己的守卫直接吞掉
 * （源码 toggleMenu 里 filterable && expanded && 非后缀区的点击一律 return），
 * 于是"点一下展开、再点同一处收起"只剩右边箭头能用。
 * 这里在捕获阶段先拦：已展开就手动收起并阻止冒泡，避免组件里的 toggleMenu 再把它翻回来；
 * 未展开放行交给组件展开。后缀区（箭头/清空）必须放行——它们各有自己的开关与清空逻辑。
 */
function handleToggleClick(event: MouseEvent) {
  if (!menuVisible.value) return
  const target = event.target as HTMLElement | null
  if (!target || target.closest('.el-select__suffix')) return
  event.stopPropagation()
  selectRef.value?.blur()
}
</script>

<template>
  <!-- clear 时 el-select 可能发出 undefined,统一归一为空串,避免上层对空值调用 trim 崩溃 -->
  <el-select
    ref="selectRef"
    v-model="modelValue"
    :placeholder="props.placeholder"
    :clearable="props.clearable"
    filterable
    @click.capture="handleToggleClick"
    @visible-change="(visible: boolean) => (menuVisible = visible)"
    @clear="modelValue = ''"
  >
    <el-option
      v-for="option in mergedOptions"
      :key="option.value"
      :label="option.label"
      :value="option.value"
    />
  </el-select>
</template>
