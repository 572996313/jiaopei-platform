import { computed, ref, toValue, type MaybeRefOrGetter } from 'vue'
import { fetchEnabledCodeResources } from '@cornerstone/core/api/system'

/**
 * 已启用自动编号的资源码，模块级单例（一份请求喂全站的新增页）。
 *
 * 没启用（或拉不到：网络错、模块没装配）时集合里没有这个资源，也就是**不预填建议号、不显示「生成」**，
 * 编号回到纯手工填写。预填的号一律显示在输入框里、可以改，服务端不会背着重写它；
 * 这颗按钮与这条建议都只是快捷入口，没有它页面照样能用，所以不重试、不弹错。
 * 反过来也不能"默认全开"：没启用的资源点了必报"未启用自动编号"。
 */
const enabledResources = ref<Set<string> | null>(null)
let inflight: Promise<void> | null = null

function ensureLoaded() {
  if (enabledResources.value || inflight) {
    return inflight
  }
  inflight = fetchEnabledCodeResources()
    .then((codes) => {
      enabledResources.value = new Set(codes)
    })
    .catch(() => {
      enabledResources.value = new Set<string>()
    })
    .finally(() => {
      inflight = null
    })
  return inflight
}

/** 编号规则维护页保存成功后调用：让各新增页的「生成」按钮显隐立刻跟上 */
export function invalidateEnabledCodeResources() {
  enabledResources.value = null
  inflight = null
  void ensureLoaded()
}

/**
 * 一个编号输入框的自动编号状态。
 *
 * @param resource 资源码（如 student），与后端 CodeRuleContributor 登记的一致
 * @returns autoEnabled 启用了才预填建议号并显示「生成」；没启用就是普通输入框
 */
export function useAutoCode(resource: MaybeRefOrGetter<string>) {
  void ensureLoaded()
  const autoEnabled = computed(() => enabledResources.value?.has(toValue(resource)) ?? false)
  return { autoEnabled }
}
