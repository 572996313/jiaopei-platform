/**
 * 文本归一：去首尾空白，空串 / null / undefined / 纯空白一律折成 undefined，
 * 交给请求层的 sanitizeParams 直接丢弃，后端就按"没传这个条件"处理。
 *
 * <p>入参必须容忍 null/undefined：el-select 这类可清空控件清空时发出的是 **undefined**
 * （Element Plus 的 valueOnClear 默认值，不是空串），查询条件里存的就未必是 string。
 * 历史上各页面各写一份 `value.trim()` 的版本，导致"选了类型再点清空、点查询"时抛
 * `Cannot read properties of undefined (reading 'trim')`——统一收口到这里。</p>
 */
export function trimToUndefined(value?: string | number | null) {
  if (value === null || value === undefined) {
    return undefined
  }
  const normalized = String(value).trim()
  return normalized ? normalized : undefined
}
