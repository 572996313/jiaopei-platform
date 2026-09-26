import { ref } from 'vue'

/**
 * 表头排序接线(单列、服务端排序)。
 *
 * 排序由后端完成:视图把 sortField/sortOrder 传给分页接口,接口按各自 Service 的列白名单
 * 映射成真实列,白名单外的字段直接 BAD_REQUEST。所以列上开 sortable 之前,先确认这一列的
 * prop 在该接口白名单里;这个 composable 只负责三件事:
 *  1. 把 Element Plus 的 ascending/descending/null 翻成后端的 asc/desc/不传;
 *  2. 点回无序(第三次点击)时清空,让接口回到自己的默认排序;
 *  3. 吃掉 resetSort() 里 clearSort() 的回调,避免重置筛选时多发一次请求。
 *
 * 用法:
 *   const sort = useTableSort({ onSorted: () => { page.value = 1; void load() },
 *     clearTableSort: () => tableRef.value?.clearSort() })
 *   <el-table ref="tableRef" @sort-change="sort.onSortChange">
 *     <el-table-column prop="username" sortable="custom" />
 *   拉数参数里加 `...sort.sortParams()`,重置筛选时调 `sort.resetSort()`。
 */
export function useTableSort(options: {
  /** 排序变化后由视图执行:一般是回到第一页再拉数 */
  onSorted: () => void
  /** 清掉表头上的排序箭头(视图手里的 el-table 实例) */
  clearTableSort?: () => void
}) {
  const sortField = ref('')
  const sortOrder = ref('')

  /** 拼进分页参数:没有排序就不传这两个键,后端走接口默认排序 */
  function sortParams(): { sortField?: string; sortOrder?: string } {
    return sortField.value ? { sortField: sortField.value, sortOrder: sortOrder.value } : {}
  }

  function onSortChange(payload: { prop?: string; order?: string | null }) {
    const cleared = !payload.order
    const nextField = cleared ? '' : (payload.prop ?? '')
    const nextOrder = cleared ? '' : payload.order === 'descending' ? 'desc' : 'asc'
    // resetSort() 已把状态清空,随后 clearSort() 的回调会带 order=null 再进来,这里直接吃掉
    if (nextField === sortField.value && nextOrder === sortOrder.value) {
      return
    }
    sortField.value = nextField
    sortOrder.value = nextOrder
    options.onSorted()
  }

  /** 回到默认排序(不发请求,由调用方自己拉数) */
  function resetSort() {
    sortField.value = ''
    sortOrder.value = ''
    options.clearTableSort?.()
  }

  return { sortField, sortOrder, sortParams, onSortChange, resetSort }
}
