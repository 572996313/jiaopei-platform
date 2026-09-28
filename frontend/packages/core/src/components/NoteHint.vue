<script setup lang="ts">
import { QuestionFilled } from '@element-plus/icons-vue'

/**
 * 按钮行右侧的"问号说明":整段口径说明塞进按钮行会折行、把省下的那一行吃回去,
 * 所以短句仍用 .education-context-text,多句口径收成问号,悬停展开。
 */
withDefaults(
  defineProps<{
    /** 问号旁边的短语,提示这一枚图标点开是什么 */
    label?: string
    /** 浮层宽度(px):多句口径需要比默认宽 */
    width?: number
  }>(),
  { label: '说明', width: 460 }
)
</script>

<template>
  <el-popover placement="bottom-end" :width="width" trigger="hover" :hide-after="120">
    <template #default>
      <div class="note-hint-body">
        <slot />
      </div>
    </template>
    <template #reference>
      <span class="note-hint">
        <el-icon class="note-hint-icon"><QuestionFilled /></el-icon>
        {{ label }}
      </span>
    </template>
  </el-popover>
</template>

<style scoped>
.note-hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--text-secondary);
  font-size: 14px;
  white-space: nowrap;
  cursor: help;
}

.note-hint-icon {
  font-size: 15px;
}

.note-hint-body {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
}
</style>
