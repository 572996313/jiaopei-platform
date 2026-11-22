<template>
  <span class="user-avatar" :style="sizeStyle" :title="name || undefined">
    <img v-if="url" :src="url" :alt="name || '用户头像'" class="user-avatar-img" />
    <span v-else class="user-avatar-fallback">{{ fallbackChar }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useFileUrl } from '@cornerstone/core/composables/use-file-url'

/**
 * 用户头像（圆形）。
 *
 * <p>fileId 走 useFileUrl 换成 blob URL（带 token 取图）；没有头像、加载失败
 * 都回落成显示名称首字，不出现破图。尺寸由调用方给，配色可用
 * `--user-avatar-bg` / `--user-avatar-ink` 覆盖（横幅那颗金色胶囊就是这么接的）。</p>
 */
const props = withDefaults(
  defineProps<{
    fileId?: string | null
    name?: string
    size?: number
  }>(),
  { fileId: null, name: '', size: 32 }
)

const { url } = useFileUrl(() => props.fileId)

const fallbackChar = computed(() => (props.name || '').trim().slice(0, 1) || '?')

const sizeStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  fontSize: `${Math.max(11, Math.round(props.size * 0.42))}px`,
}))
</script>

<style scoped>
.user-avatar {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 50%;
  background: var(--user-avatar-bg, var(--el-fill-color-light));
  color: var(--user-avatar-ink, var(--el-text-color-secondary));
  font-weight: 700;
  line-height: 1;
}

.user-avatar-fallback {
  line-height: 1;
}

.user-avatar-img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
