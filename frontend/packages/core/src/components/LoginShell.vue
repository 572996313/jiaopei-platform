<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { currentBrand } from '@cornerstone/core/extension/module-extensions'

/**
 * 认证页共用的外壳：流动底色 + 品牌区（含周历网格主视觉）+ 右侧表单卡插槽。
 * 登录/注册/激活/改密四页共用这一份，改版式只改这里，不在各页复制 markup。
 *
 * 品牌文案一律来自壳工程 registerBranding，底座不写死任何项目名词。
 */
const brand = computed(() => currentBrand())

/** 主视觉：一周七天的格子，填充位是固定图案，波浪只是装饰，不表示任何真实数据 */
const WEEK_DAYS = ['一', '二', '三', '四', '五', '六', '日']
const FILLED_ROWS = ['0110100', '1101011', '0100110', '1011001', '0110110', '1100101']
const LEAD_CELLS = [
  [1, 3],
  [5, 0],
  [3, 4],
]

const motifCells = FILLED_ROWS.flatMap((row, r) =>
  [...row].map((flag, c) => {
    const isLead = LEAD_CELLS.some(([lr, lc]) => lr === r && lc === c)
    return {
      key: `${r}-${c}`,
      cls: isLead ? 'lead' : flag === '1' ? 'on' : 'off',
      // 相位按行列偏移，负延迟让亮波从左上往右下一格一格落
      phase: `${-(r * 0.34 + c * 0.13).toFixed(2)}s`,
    }
  }),
)

/**
 * 外观模式：明亮 / 夜晚 / 跟随系统，只作用于认证页外壳，业务页仍是浅色。
 * 没选过的人默认明亮——夜晚是这一版新加的一档，不该让没表过态的人一进登录页就被压深。
 */
type ThemeMode = 'light' | 'dark' | 'system'

const THEME_STORAGE_KEY = 'cornerstone-login-theme'
const THEME_OPTIONS: Array<{ mode: ThemeMode; label: string }> = [
  { mode: 'light', label: '明亮' },
  { mode: 'dark', label: '夜晚' },
  { mode: 'system', label: '跟随系统' },
]

function readStoredMode(): ThemeMode {
  const stored = localStorage.getItem(THEME_STORAGE_KEY)
  return stored === 'light' || stored === 'dark' || stored === 'system' ? stored : 'light'
}

// 建立时同步读一次系统偏好，等 onMounted 再读会让「跟随系统」在首帧闪一下亮色
const darkMedia = window.matchMedia('(prefers-color-scheme: dark)')
const themeMode = ref<ThemeMode>(readStoredMode())
const systemDark = ref(darkMedia.matches)
const isLight = computed(
  () => themeMode.value !== 'dark' && (themeMode.value !== 'system' || !systemDark.value),
)

function syncSystemDark(event: MediaQueryListEvent) {
  systemDark.value = event.matches
}

darkMedia.addEventListener('change', syncSystemDark)
onBeforeUnmount(() => darkMedia.removeEventListener('change', syncSystemDark))

function setTheme(mode: ThemeMode) {
  themeMode.value = mode
  localStorage.setItem(THEME_STORAGE_KEY, mode)
}
</script>

<template>
  <div class="login-shell" :class="{ 'is-light': isLight }">
    <div class="login-bg" aria-hidden="true">
      <i class="login-bg-blob blob-1"></i>
      <i class="login-bg-blob blob-2"></i>
      <i class="login-bg-blob blob-3"></i>
      <i class="login-bg-blob blob-4"></i>
      <div class="login-bg-grid"></div>
      <div class="login-bg-noise"></div>
    </div>

    <div class="login-toolbar">
      <div class="login-theme" role="group" aria-label="页面外观">
        <button
          v-for="option in THEME_OPTIONS"
          :key="option.mode"
          type="button"
          class="login-theme-option"
          :aria-pressed="themeMode === option.mode"
          @click="setTheme(option.mode)"
        >
          {{ option.label }}
        </button>
      </div>
    </div>

    <section class="login-panel login-panel-brand">
      <div class="login-brand-copy">
        <div class="login-kicker">{{ brand.loginKicker }}</div>
        <h1 class="login-title">{{ brand.loginTitle }}</h1>
        <p v-if="brand.loginCopy" class="login-copy">{{ brand.loginCopy }}</p>
      </div>

      <div class="login-motif" aria-hidden="true">
        <div class="login-motif-head">
          <span v-for="day in WEEK_DAYS" :key="day">{{ day }}</span>
        </div>
        <div class="login-motif-grid">
          <i
            v-for="cell in motifCells"
            :key="cell.key"
            :class="cell.cls"
            :style="{ '--w': cell.phase }"
          ></i>
        </div>
      </div>

      <div v-if="brand.loginFeatures.length" class="login-feature-list">
        <div v-for="feature in brand.loginFeatures" :key="feature.title" class="login-feature-card">
          <div class="login-feature-title">{{ feature.title }}</div>
          <div class="login-feature-text">{{ feature.text }}</div>
        </div>
      </div>
    </section>

    <section class="login-panel login-panel-form">
      <div class="form-card">
        <slot />
      </div>
    </section>
  </div>
</template>
