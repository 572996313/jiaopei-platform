import { defineStore } from 'pinia'
import { ref } from 'vue'
import { requestGet } from '@cornerstone/core/api/request'
import { fetchDataSourceDefinitions } from '@cornerstone/core/api/system'
import type { DataSourceDefinition } from '@cornerstone/core/api/system'
import type { QueryOption } from '@cornerstone/core/components/ConfigurableList/types'

/**
 * 选项数据源缓存:按 sourceCode 缓存 sys_data_source 定义,供下拉选项加载消费。
 * 列表配置 / 弹窗表单里的 optionsLoader 名字即数据源编码:
 * 优先走配置化的数据源定义(调 endpoint + valueKey/labelKey 组装选项),
 * 配置不存在或加载失败时由调用方回退宿主注入的上下文(context)。
 */
export const useDataSourceStore = defineStore('dataSource', () => {
  const definitions = ref<Record<string, DataSourceDefinition>>({})
  /** 已请求但未配置/已停用的编码(负缓存,避免每次渲染都重复请求) */
  const missingCodes = ref<Set<string>>(new Set())
  /** 已加载的下拉选项缓存(仅无参数数据源,按编码缓存) */
  const optionsCache = ref<Record<string, QueryOption[]>>({})
  const loadingPromises = new Map<string, Promise<DataSourceDefinition | null>>()

  async function loadDefinition(code: string): Promise<DataSourceDefinition | null> {
    const pending = loadingPromises.get(code)
    if (pending) {
      return pending
    }
    const task = (async () => {
      try {
        const map = await fetchDataSourceDefinitions([code])
        const definition = map[code]
        if (definition) {
          definitions.value = { ...definitions.value, [code]: definition }
          return definition
        }
        missingCodes.value = new Set(missingCodes.value).add(code)
        return null
      } catch (error) {
        // 加载失败不清缓存:下次调用重试
        throw error
      }
    })().finally(() => loadingPromises.delete(code))
    loadingPromises.set(code, task)
    return task
  }

  /** 批量确保定义已加载(返回成功加载的定义) */
  async function ensureLoaded(codes: string[]): Promise<Record<string, DataSourceDefinition>> {
    const result: Record<string, DataSourceDefinition> = {}
    const need = Array.from(
      new Set(codes.filter((code) => code && code.trim() && !missingCodes.value.has(code)))
    )
    if (need.length === 0) {
      return { ...definitions.value }
    }
    await Promise.all(
      need.map(async (code) => {
        const definition = await loadDefinition(code)
        if (definition) {
          result[code] = definition
        }
      })
    )
    return result
  }

  function getDefinition(code: string): DataSourceDefinition | undefined {
    return definitions.value[code]
  }

  /** 读取根值:支持 point 语法(如 user.name),缺省返回 undefined */
  function readValue(item: Record<string, unknown>, key?: string | null): unknown {
    if (!key) {
      return undefined
    }
    const parts = key.split('.')
    let value: unknown = item
    for (const part of parts) {
      if (value === null || value === undefined || typeof value !== 'object') {
        return undefined
      }
      value = (value as Record<string, unknown>)[part]
    }
    return value
  }

  /**
   * 按定义调用接口拉取并组装下拉选项。
   * 参数经 param_defs 声明后,调用方按 name 传值(params 只透传声明的参数)。
   * dict 类型数据源直接读字典缓存。
   * 无参数数据源的选项结果按编码缓存,避免重复请求。
   */
  async function getOptions(
    code: string,
    params?: Record<string, unknown>
  ): Promise<QueryOption[]> {
    const hasParams = params && Object.keys(params).length > 0
    if (!hasParams && optionsCache.value[code]) {
      return optionsCache.value[code]
    }
    const definition = definitions.value[code] ?? (await loadDefinition(code))
    if (!definition) {
      return []
    }
    let options: QueryOption[]
    if (definition.sourceType === 'dict') {
      const { fetchDictOptions } = await import('@cornerstone/core/api/system')
      const groups = await fetchDictOptions([definition.dictType || ''])
      const items = groups.find((group) => group.typeCode === definition.dictType)?.items || []
      options = items
        .filter((item) => item.status !== 'disabled')
        .map((item) => ({
          label: item.itemName,
          value: item.itemValue,
        }))
    } else {
      const requestParams: Record<string, unknown> = {}
      const declared = parseParamNames(definition.paramDefs)
      if (declared.length) {
        for (const name of declared) {
          const value = params?.[name]
          if (value !== null && value !== undefined && value !== '') {
            requestParams[name] = value
          }
        }
      } else {
        // 未声明参数时透传全部参数(兼容未配置 paramDefs 的旧定义)
        Object.assign(requestParams, params)
      }

      const rows = await requestGet<unknown[]>(definition.endpoint || '', requestParams).catch(
        () => null
      )
      if (!Array.isArray(rows)) {
        return []
      }
      const valueKey = definition.valueKey || 'id'
      options = rows
        .map((item) => {
          const record = (item ?? {}) as Record<string, unknown>
          const value = readValue(record, valueKey)
          const rawLabel = readValue(record, definition.labelKey)
          return {
            label:
              rawLabel === null || rawLabel === undefined || rawLabel === ''
                ? String(value)
                : String(rawLabel),
            value: String(value),
          }
        })
        .filter(
          (option) => option.value !== 'undefined' && option.value !== 'null' && option.value !== ''
        )
    }
    if (!hasParams) {
      optionsCache.value = { ...optionsCache.value, [code]: options }
    }
    return options
  }

  function parseParamNames(paramDefs?: string | null): string[] {
    if (!paramDefs || !paramDefs.trim()) {
      return []
    }
    try {
      const list = JSON.parse(paramDefs)
      if (!Array.isArray(list)) {
        return []
      }
      return list
        .map((item: Record<string, unknown>) => (typeof item.name === 'string' ? item.name : ''))
        .filter(Boolean)
    } catch {
      return []
    }
  }

  /**
   * 同步读取已缓存的下拉选项(无参数数据源)。尚未加载时返回 []。
   * 与 getOptions 配合:组件预加载(preloadCodes)完成后,本方法读取响应式缓存触发重渲染。
   */
  function optionsFor(code: string): QueryOption[] {
    return optionsCache.value[code] ?? []
  }

  /** 只预加载一批数据源的定义(不拉选项数据),供管理页展示接口路径/字典类型 */
  async function preloadDefinitions(codes: string[]): Promise<void> {
    const unique = Array.from(new Set(codes.filter((code) => code && code.trim())))
    await Promise.all(
      unique.map(async (code) => {
        if (missingCodes.value.has(code) || definitions.value[code]) {
          return
        }
        try {
          await loadDefinition(code)
        } catch {
          // 忽略:定义加载失败不阻断页面,getDefinition 返回 undefined
        }
      })
    )
  }

  /**
   * 预加载一批数据源编码的定义与选项(无参数),供配置驱动的下拉即时取用。
   * 失败只影响该数据源的选项(回退空),不抛错阻断页面。
   */
  async function preloadCodes(codes: string[]): Promise<void> {
    const unique = Array.from(new Set(codes.filter((code) => code && code.trim())))
    await Promise.all(
      unique.map(async (code) => {
        if (missingCodes.value.has(code)) {
          return
        }
        const definition = definitions.value[code] ?? (await loadDefinition(code))
        if (!definition) {
          return
        }
        if (optionsCache.value[code]) {
          return
        }
        try {
          await getOptions(code)
        } catch {
          // 忽略:选项预加载失败不阻断页面,optionsFor 返回空
        }
      })
    )
  }

  /** 数据源配置变更后调用,强制刷新全部定义与选项缓存 */
  function invalidate() {
    definitions.value = {}
    missingCodes.value = new Set()
    optionsCache.value = {}
  }

  return {
    definitions,
    getDefinition,
    ensureLoaded,
    getOptions,
    optionsFor,
    preloadCodes,
    preloadDefinitions,
    invalidate,
  }
})
