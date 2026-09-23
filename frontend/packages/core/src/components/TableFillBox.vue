<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

/** 容器抢不到空间时的表体保底高度(px),与 .table-fill-box 的 min-height 同档 */
const MIN_TABLE_HEIGHT = 200

/**
 * 自量高的表格容器:把"父级给它的剩余空间"以 px 交给默认插槽,用法
 * `<TableFillBox v-slot="{ height }"><el-table :height="height" …></TableFillBox>`。
 *
 * 为什么非要量成 px 而不是直接写 :height="'100%'":Element Plus 会把表体内联高度算成
 * `bodyScrollHeight`(内容 scrollHeight 减表头,见 table/src/table/style-helper.mjs)。
 * 父链只有 min-height 时百分比解析为 auto,表体就塌成"数据实际高度"——外层容器被 flex
 * 拉到卡片底、表体没跟上,横向滚动条贴在最后一行下面,下面一片空白。
 * 传真实像素值后表体才等于可视高:表内滚动、表头吸顶、滚动条落在列表底边。
 *
 * 观测对象是本组件自己的容器(.table-fill-box 为 flex:1 1 0 + overflow:hidden,
 * 高度只由剩余空间决定、不随内容长),所以量到的是确定值,不会和表格高度互相追尾。
 */
const boxRef = ref<HTMLElement | null>(null)
const height = ref<number | undefined>(undefined)
let observer: ResizeObserver | null = null

function measure() {
  const available = Math.floor(boxRef.value?.clientHeight ?? 0)
  if (available > 0) {
    height.value = Math.max(MIN_TABLE_HEIGHT, available)
  }
}

onMounted(() => {
  measure()
  if (typeof ResizeObserver === 'undefined') {
    window.addEventListener('resize', measure)
    return
  }
  observer = new ResizeObserver(measure)
  if (boxRef.value) {
    observer.observe(boxRef.value)
  }
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
  window.removeEventListener('resize', measure)
})
</script>

<template>
  <div ref="boxRef" class="table-fill-box">
    <slot :height="height" />
  </div>
</template>
