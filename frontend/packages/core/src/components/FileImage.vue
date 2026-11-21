<template>
  <span class="file-image" :class="{ 'is-empty': !url }">
    <img v-if="url" :src="url" :alt="alt" class="file-image-content" :style="{ objectFit: fit }" />
    <span v-else class="file-image-placeholder" :title="error">
      <el-icon><Picture /></el-icon>
    </span>
  </span>
</template>

<script setup lang="ts">
import { Picture } from '@element-plus/icons-vue'
import { useFileUrl } from '@cornerstone/core/composables/use-file-url'

/**
 * 只读文件预览（列表缩略图、详情页）。
 *
 * 内部走 use-file-url：带 token 取 Blob 再转 blob URL，`<img>` 不会因为缺 Authorization 而裂开。
 * 尺寸由外层容器决定（宽高都是 100%），缩略图请套一个定宽定高的盒子。
 */
const props = withDefaults(
  defineProps<{
    fileId?: string | null
    alt?: string
    fit?: 'cover' | 'contain' | 'fill' | 'none' | 'scale-down'
  }>(),
  { fileId: null, alt: '文件预览', fit: 'cover' }
)

const { url, error } = useFileUrl(() => props.fileId)
</script>

<style scoped>
.file-image {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  overflow: hidden;
  border-radius: 4px;
  background: var(--el-fill-color-light);
}

.file-image-content {
  display: block;
  width: 100%;
  height: 100%;
}

.file-image-placeholder {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-placeholder);
  font-size: 20px;
}
</style>
