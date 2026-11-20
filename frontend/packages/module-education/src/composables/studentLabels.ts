import { ref } from 'vue'
import { fetchStudentOptions } from '@cornerstone/module-education/api/education'

/**
 * 学员 id → 显示名的本机缓存。
 * 下拉改成按需搜索后，页面不再持有全量学员列表，但面板标题、确认框、下拉里"已选中的那一项"
 * 都要按 id 回显名称，所以把"见过的"学员（搜索命中过、或表格行自带名称）记在这里；
 * 查不到就回显 `学员 #id`，不硬猜。
 */
const labels = new Map<string, string>()

/**
 * 缓存是普通 Map，Vue 追不到它的变化，所以另挂一个 version：写缓存时自增，读取时先读一下它，
 * 补取回来的名称才能让依赖它的 computed 重算。
 */
const version = ref(0)

/** 下拉与回显统一带上学号，同名学员才分得开 */
export function studentOptionLabel(item: { id?: string; studentNo?: string | null; studentName?: string | null }) {
  const name = item.studentName || item.studentNo || ''
  if (!name) {
    return item.id ? `学员 #${item.id}` : ''
  }
  return item.studentName && item.studentNo ? `${name}（${item.studentNo}）` : name
}

export function rememberStudentOptions(items: { id: string; studentNo?: string | null; studentName?: string | null }[]) {
  let changed = false
  items.forEach((item) => {
    const text = studentOptionLabel(item)
    if (text && !text.startsWith('学员 #')) {
      labels.set(item.id, text)
      changed = true
    }
  })
  if (changed) {
    version.value += 1
  }
}

export function studentLabelOf(id?: string | null) {
  void version.value
  if (!id) {
    return '未选择'
  }
  return labels.get(id) || `学员 #${id}`
}

/**
 * 表格行只有学员 id 时的名称回显通路：把本机没见过的 id 一次请求补回来。
 * 价目列表按学员设了覆盖价时，行上只有 id，不补取就只能显示裸雪花 ID。
 */
export async function ensureStudentLabels(
  institutionId?: string | number | null,
  ids?: (string | null | undefined)[]
) {
  const missing = [...new Set((ids ?? []).filter((id): id is string => Boolean(id)))].filter(
    (id) => !labels.has(id)
  )
  if (!institutionId || missing.length === 0) {
    return
  }
  try {
    rememberStudentOptions(
      await fetchStudentOptions({ institutionId: String(institutionId), ids: missing.join(',') })
    )
  } catch {
    // 补不到名称不影响页面主流程，按 id 显示即可
  }
}
