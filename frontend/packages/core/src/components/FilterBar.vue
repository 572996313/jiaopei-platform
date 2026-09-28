<template>
  <div class="filter-bar">
    <div class="filter-bar-filters" :class="{ 'is-collapsed': collapsible && !expanded }">
      <slot />
    </div>
    <div class="filter-bar-tail">
      <el-button
        v-if="collapsible"
        class="filter-bar-toggle"
        link
        type="primary"
        @click="expanded = !expanded"
      >
        {{ expanded ? '收起' : `展开（${itemCount - collapseAt}）` }}
        <el-icon class="filter-bar-toggle-icon" :class="{ 'is-up': expanded }"
          ><ArrowDown
        /></el-icon>
      </el-button>
      <slot name="extra" />
      <el-button type="primary" :loading="loading" @click="emit('query')">查询</el-button>
      <el-button :disabled="loading" @click="emit('reset')">重置</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Comment, Fragment, Text, computed, ref, useSlots, type VNode } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'

/**
 * 全站统一筛选栏:一行内左侧筛选控件、右侧固定「查询」「重置」两颗按钮。
 * 触发口径:只有点按钮才发事件,控件本身不许 @change 自动查询(输入框回车除外)。
 * 控件超过 collapse-at 个时默认收起(限一行),点「展开(N)」铺开。
 *
 * 页面主操作按钮不在这条线上:它们属于上方的 .table-toolbar(按钮靠左、说明靠右),
 * 本栏只有 .education-context-text 这类短句说明在整屏没有按钮时才用 #extra 挂右侧。
 *
 * 筛选控件直接写在默认插槽里,一个控件 = 一个子节点;控件数即折叠判据。
 * 需要"标题 + 控件"并排时(统计区间、配置化的字段名),两者必须包在同一个元素里
 * (如 <span class="filter-bar-group">),否则标题会被当成一个控件多算一次。
 */
const props = withDefaults(
  defineProps<{
    /** 查询进行中:查询按钮转圈、重置按钮禁用 */
    loading?: boolean
    /** 收起阈值,控件数超过它才出现「展开」;传 0 关闭折叠 */
    collapseAt?: number
  }>(),
  { loading: false, collapseAt: 4 }
)

const emit = defineEmits<{
  query: []
  reset: []
}>()

const slots = useSlots()
const expanded = ref(false)

/** v-for 渲染出来的子节点是 Fragment,要摊开逐个计;空白与注释不算控件 */
function countNodes(nodes: VNode[]): number {
  return nodes.reduce((total, node) => {
    if (!node || node.type === Comment || node.type === Text) return total
    if (node.type === Fragment && Array.isArray(node.children)) {
      return total + countNodes(node.children as VNode[])
    }
    return total + 1
  }, 0)
}

const itemCount = computed(() => countNodes(slots.default?.() ?? []))
const collapsible = computed(() => props.collapseAt > 0 && itemCount.value > props.collapseAt)
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: flex-start;
  flex-wrap: nowrap;
  gap: 10px;
  min-width: 0;
}

.filter-bar-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  flex: 1 1 auto;
  min-width: 0;
}

/* 收起态:只显示第一行,展开后恢复自然高度 */
.filter-bar-filters.is-collapsed {
  max-height: 32px;
  overflow: hidden;
}

.filter-bar-tail {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 0 1 auto;
  min-width: 0;
  margin-left: auto;
}

/* 与 el-button 默认高度对齐,保证「展开」与「查询/重置」在同一基线 */
.filter-bar-toggle {
  height: 32px;
}

.filter-bar-toggle-icon {
  margin-left: 2px;
  transition: transform 0.2s;
}

.filter-bar-toggle-icon.is-up {
  transform: rotate(180deg);
}
</style>
