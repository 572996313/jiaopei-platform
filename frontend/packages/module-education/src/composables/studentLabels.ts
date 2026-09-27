/**
 * 学员 id → 显示名的本机缓存。
 * 下拉改成按需搜索后，页面不再持有全量学员列表，但面板标题、确认框、下拉里"已选中的那一项"
 * 都要按 id 回显名称，所以把"见过的"学员（搜索命中过、或表格行自带名称）记在这里；
 * 查不到就回显 `学员 #id`，不硬猜。
 */
const labels = new Map<string, string>()

/** 下拉与回显统一带上学号，同名学员才分得开 */
export function studentOptionLabel(item: { id?: string; studentNo?: string | null; studentName?: string | null }) {
  const name = item.studentName || item.studentNo || ''
  if (!name) {
    return item.id ? `学员 #${item.id}` : ''
  }
  return item.studentName && item.studentNo ? `${name}（${item.studentNo}）` : name
}

export function rememberStudentOptions(items: { id: string; studentNo?: string | null; studentName?: string | null }[]) {
  items.forEach((item) => {
    const text = studentOptionLabel(item)
    if (text && !text.startsWith('学员 #')) {
      labels.set(item.id, text)
    }
  })
}

export function studentLabelOf(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return labels.get(id) || `学员 #${id}`
}
