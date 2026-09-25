import { computed, ref, watch } from 'vue'

/**
 * 全站外观档:明亮 / 夜晚 / 跟随系统。登录页与登录后外壳共用这一份状态,
 * 任一处切档整站跟着变,不再出现"登录选了夜晚、进后台又变亮"的割裂。
 *
 * 实现靠两件事同时成立:
 *  1. html 上挂/摘 `dark` 类 —— Element Plus 官方暗色变量表(theme-chalk/dark/css-vars.css)
 *     就是按 `html.dark` 生效的,这一步把表格/表单/弹窗/下拉整体压深;
 *  2. 本文件切档时同步刷新 `color-scheme`,原生滚动条与表单控件跟着走,
 *     业务样式只需在 `html.dark{}` 里覆盖自己的令牌。
 *
 * 模块级单例:状态挂在模块上而不是某个组件上,谁先 import 谁初始化,
 * main.ts 顶部 import 本模块即可保证首帧就带上正确的档,不等组件 onMounted。
 */
export type ThemeMode = 'light' | 'dark' | 'system'

const THEME_STORAGE_KEY = 'cornerstone-theme'
/** 外壳改版前登录页独立存的外观档,读一次迁到统一键,老用户的选择不清零 */
const LEGACY_LOGIN_THEME_KEY = 'cornerstone-login-theme'

const THEME_OPTIONS: Array<{ mode: ThemeMode; label: string }> = [
  { mode: 'light', label: '明亮' },
  { mode: 'dark', label: '夜晚' },
  { mode: 'system', label: '跟随系统' },
]

function normalizeMode(value: string | null): ThemeMode | null {
  return value === 'light' || value === 'dark' || value === 'system' ? value : null
}

function readStoredMode(): ThemeMode {
  return normalizeMode(localStorage.getItem(THEME_STORAGE_KEY)) ??
    normalizeMode(localStorage.getItem(LEGACY_LOGIN_THEME_KEY)) ??
    'light'
}

const themeMode = ref<ThemeMode>(readStoredMode())

// 建立时同步读一次系统偏好:等 onMounted 再读会让「跟随系统」在首帧闪一下亮色
const darkMedia = window.matchMedia('(prefers-color-scheme: dark)')
const systemDark = ref(darkMedia.matches)
darkMedia.addEventListener('change', (event) => {
  systemDark.value = event.matches
})

const isDark = computed(
  () => themeMode.value === 'dark' || (themeMode.value === 'system' && systemDark.value),
)

function applyThemeClass(dark: boolean) {
  const root = document.documentElement
  root.classList.toggle('dark', dark)
  root.style.colorScheme = dark ? 'dark' : 'light'
}

// immediate: 模块被 import 的那一刻就把类刷上,和首屏样式同帧生效
watch(isDark, applyThemeClass, { immediate: true })

export function useTheme() {
  const isLight = computed(() => !isDark.value)

  function setTheme(mode: ThemeMode) {
    themeMode.value = mode
    localStorage.setItem(THEME_STORAGE_KEY, mode)
  }

  return { themeMode, isDark, isLight, setTheme, THEME_OPTIONS }
}
