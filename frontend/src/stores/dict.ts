import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { fetchDictOptions } from '@/api/system'
import type { DictItemSummary } from '@/api/system'

/**
 * 业务字典缓存:按 typeCode 缓存启用的字典条目,供表单下拉与列表回显使用。
 * 调用 ensureLoaded(typeCodes) 批量加载缺失的类型;读取使用 optionsOf/labelOf。
 * 字典在 /system/dicts 页面维护,维护后可调用 invalidate 强制刷新。
 */
export const useDictStore = defineStore('dict', () => {
  const itemsByType = ref<Record<string, DictItemSummary[]>>({})
  const loadingPromises = new Map<string, Promise<unknown>>()

  const loadedTypeCodes = computed(() => Object.keys(itemsByType.value))

  async function ensureLoaded(typeCodes: string[]) {
    const missing = Array.from(
      new Set(typeCodes.filter((code) => code && !(code in itemsByType.value)))
    )
    if (missing.length === 0) {
      return
    }
    const pending = Promise.all(missing.map((code) => loadType(code)))
    await Promise.all(missing.map((code) => trackLoading(code, pending)))
  }

  async function loadType(typeCode: string) {
    try {
      const groups = await fetchDictOptions([typeCode])
      const items = groups.find((group) => group.typeCode === typeCode)?.items || []
      itemsByType.value = { ...itemsByType.value, [typeCode]: items }
    } catch (error) {
      // 加载失败时缓存空列表,避免每次渲染重复请求;需要时调用 invalidate 重试
      itemsByType.value = { ...itemsByType.value, [typeCode]: [] }
      throw error
    }
  }

  function trackLoading(typeCode: string, pending: Promise<unknown>) {
    const existing = loadingPromises.get(typeCode)
    if (existing) {
      return existing
    }
    const tracked = pending.catch(() => undefined).finally(() => loadingPromises.delete(typeCode))
    loadingPromises.set(typeCode, tracked)
    return tracked
  }

  function optionsOf(typeCode: string) {
    return itemsByType.value[typeCode] || []
  }

  function labelOf(typeCode: string, value?: string | null) {
    if (value === null || value === undefined || value === '') {
      return undefined
    }
    return optionsOf(typeCode).find((item) => item.itemValue === value)?.itemName
  }

  function invalidate(typeCode?: string) {
    if (!typeCode) {
      itemsByType.value = {}
      return
    }
    const next = { ...itemsByType.value }
    delete next[typeCode]
    itemsByType.value = next
  }

  return { itemsByType, loadedTypeCodes, ensureLoaded, optionsOf, labelOf, invalidate }
})
