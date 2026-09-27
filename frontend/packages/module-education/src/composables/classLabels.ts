import { ref } from 'vue'
import { fetchClassOptions, type ClassOption } from '@cornerstone/module-education/api/education'

/**
 * 班级 id → 班级条目（名称 + 所属校区/学期）的本机缓存。
 *
 * 下拉改成按需搜索后，页面不再持有全量班级列表，但筛选栏"已选中的那个班"、课表行上的班级名、
 * 确认框标题、排课弹窗要按班级带出的校区与学期，都要按 id 取值。所以把"见过的"班级（搜索命中过、
 * 或接口行/详情自带名称）记在这里。
 *
 * 缓存是普通 Map，Vue 追不到它的变化，所以另挂一个 version：写缓存时自增，读取时先读一下它，
 * 补取回来的名称才能让依赖它的 computed 重算。
 */
const known = new Map<string, ClassOption>()
const version = ref(0)

/** 与下拉一致的取词顺序：班级名称优先，没有名称用编码 */
export function classOptionLabel(item: { id?: string; classCode?: string | null; className?: string | null }) {
  return item.className || item.classCode || (item.id ? `班级 #${item.id}` : '')
}

export function rememberClassOptions(items: ClassOption[]) {
  let changed = false
  items.forEach((item) => {
    if (item?.id) {
      known.set(item.id, item)
      changed = true
    }
  })
  if (changed) {
    version.value += 1
  }
}

export function classKnownOf(id?: string | null) {
  void version.value
  return id ? (known.get(id) ?? null) : null
}

export function classLabelOf(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  const hit = classKnownOf(id)
  return hit ? classOptionLabel(hit) : `班级 #${id}`
}

/**
 * 拿某个班的名称与归属；缓存里没有就按 id 补取一条。
 *
 * 从周课表格子、待补课列表这类入口进弹窗时，那个班可能一次都没被搜过，不补取就会在框里
 * 显示成裸 ID，弹窗也带不出该班的校区与学期。
 */
export async function ensureClassKnown(
  institutionId?: string | number | null,
  id?: string | null
): Promise<ClassOption | null> {
  const hit = classKnownOf(id)
  // 见过名称就算数：补取只在完全没有记录时发生
  if (id && hit?.className) {
    return hit
  }
  await ensureClassLabels(institutionId, [id])
  return classKnownOf(id) ?? hit
}

/**
 * 表格行只有班级 id 时的名称回显通路：把本机没见过的 id 一次请求补回来。
 *
 * 课次/计划/规则三张列表的后端视图都只有 classId（教师、教室同理，但那两类条数小、仍走全量数组当字典），
 * 原先靠页面持有的全量班级数组翻译，数组不再全量加载就必须按 id 回查。
 */
export async function ensureClassLabels(
  institutionId?: string | number | null,
  ids?: (string | null | undefined)[]
) {
  const missing = [...new Set((ids ?? []).filter((id): id is string => Boolean(id)))].filter(
    (id) => !known.has(id)
  )
  if (!institutionId || missing.length === 0) {
    return
  }
  try {
    rememberClassOptions(
      await fetchClassOptions({ institutionId: String(institutionId), ids: missing.join(',') })
    )
  } catch {
    // 补不到名称不影响页面主流程，按 id 显示即可
  }
}
