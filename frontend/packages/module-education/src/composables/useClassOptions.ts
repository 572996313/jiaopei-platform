import { onUnmounted, ref } from 'vue'
import { fetchClassOptions, type ClassOption } from '@cornerstone/module-education/api/education'
import { rememberClassOptions } from '@cornerstone/module-education/composables/classLabels'

/** 范围参数的取值：小号机构 id（种子数据里的 1）不到 16 位，大 ID 转字符串那步不会命中，运行期可能是数字 */
export type ClassScopeValue = string | number | null | undefined

export interface ClassScope {
  institutionId: ClassScopeValue
  campusId: ClassScopeValue
  termId: ClassScopeValue
}

/**
 * 班级下拉的按需搜索：一屏 30 条、输入关键字 300ms 防抖、命中的都写进名称缓存。
 * 单选与多选两个组件共用这一份，避免两处各写一遍防抖。
 */
export function useClassOptions(readScope: () => ClassScope) {
  const options = ref<ClassOption[]>([])
  const loading = ref(false)
  let debounceTimer: ReturnType<typeof setTimeout> | null = null

  async function load(keyword: string | null) {
    const scope = readScope()
    if (!scope.institutionId) {
      options.value = []
      return
    }
    loading.value = true
    try {
      const list = await fetchClassOptions({
        institutionId: String(scope.institutionId),
        campusId: scope.campusId == null || scope.campusId === '' ? null : String(scope.campusId),
        termId: scope.termId == null || scope.termId === '' ? null : String(scope.termId),
        keyword,
      })
      options.value = list
      rememberClassOptions(list)
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

  onUnmounted(() => {
    if (debounceTimer) {
      clearTimeout(debounceTimer)
      debounceTimer = null
    }
  })

  return { options, loading, load, search, handleVisibleChange }
}
