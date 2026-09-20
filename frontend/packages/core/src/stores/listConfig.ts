import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchListConfigByCode } from '@cornerstone/core/api/system'
import type { ListConfigSummary } from '@cornerstone/core/api/system'

/**
 * 列表页配置缓存:按 resourceCode 缓存 sys_list_config 记录,供 ConfigurableList 组件消费。
 * 列表配置管理页保存后调用 invalidate() 强制刷新,前端无需重新构建即可生效。
 */
export const useListConfigStore = defineStore('listConfig', () => {
  const configs = ref<Record<string, ListConfigSummary>>({})
  const loadingPromises = new Map<string, Promise<ListConfigSummary>>()

  async function getOf(resourceCode: string): Promise<ListConfigSummary> {
    const cached = configs.value[resourceCode]
    if (cached) {
      return cached
    }
    const pending = loadingPromises.get(resourceCode)
    if (pending) {
      return pending
    }
    const task = loadConfig(resourceCode)
    loadingPromises.set(resourceCode, task)
    try {
      return await task
    } finally {
      loadingPromises.delete(resourceCode)
    }
  }

  async function loadConfig(resourceCode: string) {
    const config = await fetchListConfigByCode(resourceCode)
    configs.value = { ...configs.value, [resourceCode]: config }
    return config
  }

  function invalidate(resourceCode?: string) {
    if (!resourceCode) {
      configs.value = {}
      return
    }
    const next = { ...configs.value }
    delete next[resourceCode]
    configs.value = next
  }

  return { configs, getOf, invalidate }
})
