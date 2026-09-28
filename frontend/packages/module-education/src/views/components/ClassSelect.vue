<template>
  <el-select
    :model-value="modelValue"
    :placeholder="placeholder"
    :clearable="clearable"
    :disabled="disabled"
    filterable
    remote
    reserve-keyword
    :remote-method="search"
    :loading="loading"
    class="archive-select"
    @update:model-value="handleInput"
    @visible-change="handleVisibleChange"
  >
    <!-- 名称晚到（按 id 补取回来）时要把这条选项换成新实例：Element Plus 只在选项增减时
         重算框内显示，key 只写 id 的话标签变了也不会重算，下拉会一直停在裸 ID。 -->
    <el-option
      v-for="item in visibleOptions"
      :key="`${item.id}-${item.label}`"
      :label="item.label"
      :value="item.id"
    />
  </el-select>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import type { ClassOption } from '@cornerstone/module-education/api/education'
import {
  classKnownOf,
  classLabelOf,
  classOptionLabel,
  ensureClassKnown,
} from '@cornerstone/module-education/composables/classLabels'
import {
  useClassOptions,
  type ClassScopeValue,
} from '@cornerstone/module-education/composables/useClassOptions'

const props = withDefaults(
  defineProps<{
    modelValue?: string | null
    /** 机构 id：小号机构（如种子数据里的 1）不到 16 位，大 ID 转字符串那步不会命中，运行期可能是数字 */
    institutionId?: ClassScopeValue
    campusId?: ClassScopeValue
    termId?: ClassScopeValue
    placeholder?: string
    clearable?: boolean
    disabled?: boolean
  }>(),
  {
    modelValue: null,
    institutionId: null,
    campusId: null,
    termId: null,
    placeholder: '输入班级名称或编码搜索班级',
    clearable: true,
    disabled: false,
  }
)

const emit = defineEmits<{
  /** 清空发空串而不是 null：宿主里的班级字段两种都有（筛选栏用 null、弹窗表单用 ''），统一成一个值 */
  'update:modelValue': [value: string]
  /** 选中项换掉时把整条选项给宿主，宿主据此带出该班的校区与学期；清空时给 null */
  change: [option: ClassOption | null]
}>()

const { options, loading, search, handleVisibleChange } = useClassOptions(() => ({
  institutionId: props.institutionId,
  campusId: props.campusId,
  termId: props.termId,
}))

const visibleOptions = computed(() => {
  const list = options.value.map((item) => ({ id: item.id, label: classOptionLabel(item) }))
  const picked = props.modelValue
  if (!picked || list.some((item) => item.id === picked)) {
    return list
  }
  // 选中的那个班不在当前这批搜索结果里（换关键字后很常见），把名称钉在列表前面，
  // 否则下拉收起来时框内会退化成裸 ID
  return [{ id: picked, label: classLabelOf(picked) }, ...list]
})

function handleInput(value: string | null) {
  const next = value || ''
  emit('update:modelValue', next)
  emit('change', next ? (options.value.find((item) => item.id === next) ?? null) : null)
}

// 从课表行、待补课列表这类入口带进来的班一次都没被搜过，缓存里没有名称就按 id 补取一条，
// 否则框内会显示成裸 ID，宿主弹窗也带不出该班的校区与学期
watch(
  () => props.modelValue,
  (id) => {
    if (id && !classKnownOf(id)) {
      void ensureClassKnown(props.institutionId, id)
    }
  },
  { immediate: true }
)
</script>

<style scoped>
/* 档案下拉默认占满整行（弹窗表单），进了筛选栏要和别的条件并排。
   宽度收在组件里统一管，内联 style 压不过页面规则，别各页自己写。 */
.archive-select {
  width: 100%;
}

.filter-bar .archive-select {
  width: 240px;
}
</style>
