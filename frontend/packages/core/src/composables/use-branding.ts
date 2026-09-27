import { computed, ref } from 'vue'
import { fetchBranding } from '@cornerstone/core/api/branding'

type BrandField = 'name' | 'tagline' | 'loginCopy' | 'loginFormTitle'

/** 每条文案对应的系统参数键名；未配置时把键名直接显示出来，运维才知道改哪一条 */
const CONFIG_KEY: Record<BrandField, string> = {
  name: 'brand.name',
  tagline: 'brand.tagline',
  loginCopy: 'brand.login_copy',
  loginFormTitle: 'brand.login_form_title',
}

/**
 * 模块级单例：全站一份，登录页与后台横幅读的是同一个对象。
 * 不放 localStorage——它是服务端数据，本机缓存会让改完参数"看不到生效"。
 */
const values = ref<Record<BrandField, string>>({ name: '', tagline: '', loginCopy: '', loginFormTitle: '' })
const mark = ref('')
const loaded = ref(false)
let inflight: Promise<void> | null = null

function text(field: BrandField) {
  const value = values.value[field]
  if (value) return value
  // 请求还没回来时留空，否则首帧会闪一下"未配置"
  return loaded.value ? `未配置(${CONFIG_KEY[field]})` : ''
}

/**
 * 浏览器标签标题与横幅同源：未配置或拉不到时同样显示维护提示，
 * 不回落 index.html 里那个构建期名字——那正是"改完参数标签还挂着旧名"的由来。
 */
function applyDocumentTitle() {
  document.title = text('name')
}

export function useBranding() {
  return {
    brandName: computed(() => text('name')),
    brandTagline: computed(() => text('tagline')),
    loginCopy: computed(() => text('loginCopy')),
    loginFormTitle: computed(() => text('loginFormTitle')),
    /** 窄栏角标，由后端按系统名称首字推导，没有独立参数 */
    brandMark: computed(() => mark.value),
    loadBranding,
  }
}

/** 首次调用发请求，之后的调用（登录页 → 后台）共用同一份结果 */
function loadBranding() {
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
        // 拉不到就按"未配置"处理：宁可提示去维护，也不显示构建期的旧名字
      })
      .finally(() => {
        loaded.value = true
        applyDocumentTitle()
      })
  }
  return inflight
}
