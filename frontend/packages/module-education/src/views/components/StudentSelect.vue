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
import { computed, onUnmounted, ref } from 'vue'
import {
  fetchStudentOptions,
  type StudentOption,
} from '@cornerstone/module-education/api/education'
import {
  rememberStudentOptions,
  studentLabelOf,
  studentOptionLabel,
} from '@cornerstone/module-education/composables/studentLabels'

const props = withDefaults(
  defineProps<{
    modelValue?: string | null
    /** 机构 id：小号机构（如种子数据里的 1）不到 16 位，大 ID 转字符串那步不会命中，运行期可能是数字 */
    institutionId?: string | number | null
    campusId?: string | number | null
    placeholder?: string
    clearable?: boolean
    disabled?: boolean
  }>(),
  {
    modelValue: null,
    institutionId: null,
    campusId: null,
    placeholder: '输入姓名或学号搜索学员',
    clearable: true,
    disabled: false,
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: string | null]
  /** 选中项换掉时把整条记录给宿主，宿主据此记名称、联动其他筛选 */
  change: [option: StudentOption | null]
}>()

interface SelectOption {
  id: string
  label: string
}

const options = ref<StudentOption[]>([])
const loading = ref(false)
let debounceTimer: ReturnType<typeof setTimeout> | null = null

const visibleOptions = computed<SelectOption[]>(() => {
  const list = options.value.map((item) => ({ id: item.id, label: studentOptionLabel(item) }))
  const picked = props.modelValue
  if (!picked || list.some((item) => item.id === picked)) {
    return list
  }
  // 选中的那个人不在当前这批搜索结果里（换关键字后很常见），把名称钉在列表前面，
  // 否则下拉收起来时框内会退化成裸 ID
  return [{ id: picked, label: studentLabelOf(picked) }, ...list]
})

async function load(keyword: string | null) {
  if (!props.institutionId) {
    options.value = []
    return
  }
  loading.value = true
  try {
    const list = await fetchStudentOptions({
      institutionId: String(props.institutionId),
      campusId: props.campusId == null ? null : String(props.campusId),
      keyword,
    })
    options.value = list
    rememberStudentOptions(list)
  } catch {
    options.value = []
  } finally {
    loading.value = false
  }
}

function search(query: string) {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
  }
  const keyword = query.trim()
  if (!keyword) {
    void load(null)
    return
  }
  debounceTimer = setTimeout(() => {
    debounceTimer = null
    void load(keyword)
  }, 300)
}

/** 打开下拉时先给一屏默认结果，不要求操作员必须先打字 */
function handleVisibleChange(visible: boolean) {
  if (visible && options.value.length === 0) {
    void load(null)
  }
}

function handleInput(value: string | null) {
  const next = value || null
  emit('update:modelValue', next)
  emit('change', next ? (options.value.find((item) => item.id === next) ?? null) : null)
}

onUnmounted(() => {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
    debounceTimer = null
  }
})
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
