import { computed, ref } from 'vue'
import { fetchBranding } from '@/api/branding'

type BrandField = 'name' | 'tagline' | 'loginCopy' | 'loginFormTitle'

/** 每条文案对应的系统参数键名;未配置时把键名直接显示出来,才知道去后台改哪一条 */
const CONFIG_KEY: Record<BrandField, string> = {
  name: 'brand.name',
  tagline: 'brand.tagline',
  loginCopy: 'brand.login_copy',
  loginFormTitle: 'brand.login_form_title',
}

/**
 * 模块级单例:登录页与首页读的是同一个对象,一次请求全站共用。
 * 不落本机缓存——它是服务端数据,缓存等于"后台改完名字,小程序还挂着旧名"。
 * 与 PC 端 core 的 composables/use-branding.ts 同口径。
 */
const values = ref<Record<BrandField, string>>({ name: '', tagline: '', loginCopy: '', loginFormTitle: '' })
const mark = ref('')
const loaded = ref(false)
let inflight: Promise<void> | null = null

function text(field: BrandField) {
  const value = values.value[field]
  if (value) return value
  // 请求还没回来时留空,否则首帧会闪一下"未配置"
  return loaded.value ? `未配置(${CONFIG_KEY[field]})` : ''
}

export function useBranding() {
  return {
    brandName: computed(() => text('name')),
    brandTagline: computed(() => text('tagline')),
    loginCopy: computed(() => text('loginCopy')),
    loginFormTitle: computed(() => text('loginFormTitle')),
    /** 角标字母,由后端按系统名称首字推导,没有独立参数 */
    brandMark: computed(() => mark.value),
    loadBranding,
  }
}

/** 首次调用发请求,之后的调用共用同一份结果;拉不到就按"未配置"处理 */
export function loadBranding() {
  if (!inflight) {
    inflight = fetchBranding()
      .then((data) => {
        values.value = {
          name: (data.name ?? '').trim(),
          tagline: (data.tagline ?? '').trim(),
          loginCopy: (data.loginCopy ?? '').trim(),
          loginFormTitle: (data.loginFormTitle ?? '').trim(),
        }
        mark.value = (data.mark ?? '').trim()
      })
      .catch(() => {
        // 装饰性文案,失败不提示也不重试:宁可显示"未配置",也不显示构建期写死的旧名字
      })
      .finally(() => {
        loaded.value = true
      })
  }
  return inflight
}
