<script setup lang="ts">
import { nextTick, onUnmounted, ref } from 'vue'

/**
 * 悬停「正在排序的那列表头」时给出的方向说明。
 *
 * 只挂在 AppLayout 上一处,靠事件委托认 Element Plus 的表头,视图和 useTableSort 都不用接线:
 *  - 为什么不常驻:分页行那枚回显胶囊也要占一行版面,用户判"常显太丑"。方向信息改成悬停才给,
 *    静置时表头只剩"生效方向那一支箭头"(反方向压到 opacity .2),够认出现在哪列。
 *  - 为什么自绘:表头在 .el-table 的横向滚动容器里,popper 会被 overflow 裁掉;
 *    定位口径照抄 .tab-context-menu(自绘 fixed + z-index 3000)。
 *  - 只对已生效的排序列出现(th.ascending / th.descending),没排序的表头不打扰。
 *  - aria-hidden:Element Plus 已往 th 上写 aria-sort,读屏端不缺这条信息。
 */
const SORTED_TH = 'th.el-table__cell.ascending, th.el-table__cell.descending'

const hint = ref<{ main: string; sub: string; x: number; y: number } | null>(null)
const hintEl = ref<HTMLElement | null>(null)
let anchor: HTMLElement | null = null

/** 列名取表头单元格里的文本节点:两支箭头是元素不是文字,所以文字就是这一列的标题 */
function labelOf(th: HTMLElement): string {
  const cell = th.querySelector('.cell') ?? th
  const parts: string[] = []
  const walker = document.createTreeWalker(cell, NodeFilter.SHOW_TEXT)
  let node = walker.nextNode()
  while (node) {
    const text = node.textContent?.trim()
    if (text) parts.push(text)
    node = walker.nextNode()
  }
  return parts.join(' ')
}

function place(th: HTMLElement) {
  if (!hint.value) return
  // 表头被重渲染换掉了(切页签、重载列配置):锚点没了就别把浮层钉在原点
  if (!th.isConnected) {
    hide()
    return
  }
  const rect = th.getBoundingClientRect()
  // 首帧还没量过浮层尺寸,先用估值定位(下一帧按真实宽高修正),否则会闪现在左上角
  const width = hintEl.value?.offsetWidth ?? 240
  const height = hintEl.value?.offsetHeight ?? 44
  // 默认贴在表头下沿;底部放不下(弹窗里的表、贴近视口的首屏表头)就翻到上沿
  let y = rect.bottom + 6
  if (y + height > window.innerHeight - 8) y = Math.max(8, rect.top - height - 6)
  const x = Math.min(Math.max(8, rect.left), window.innerWidth - width - 8)
  if (hint.value.x === x && hint.value.y === y) return
  hint.value = { ...hint.value, x, y }
}

function show(th: HTMLElement) {
  const desc = th.classList.contains('descending')
  const label = labelOf(th) || '该列'
  const main = `按「${label}」${desc ? '倒排（降序）' : '正排（升序）'}`
  const sub = desc
    ? '再点一次取消排序，回到列表默认顺序'
    : '再点一次切到倒排（降序）'
  hint.value = { main, sub, x: 0, y: 0 }
  anchor = th
  place(th)
  void nextTick(() => place(th))
}

function hide() {
  hint.value = null
  anchor = null
}

function handlePointerOver(event: PointerEvent) {
  const th = (event.target as Element | null)?.closest?.(SORTED_TH) as HTMLElement | null
  if (!th) {
    if (anchor) hide()
    return
  }
  if (th !== anchor) show(th)
}

/** 点击后 Element Plus 就地翻向(第三次点击清空),文案要跟着重算 */
function handleClick(event: MouseEvent) {
  // 点在这列表头上就等于"鼠标在它上面",所以不管浮层此前开没开都要同步一次:
  // 取消排序(第三击)后紧接第四击重新升序,鼠标没挪动就不会再触发 pointerover。
  const th = (event.target as Element | null)?.closest?.('th.el-table__cell') as HTMLElement | null
  if (!th) return
  // 不能用 nextTick:本监听器在捕获阶段,比 Element Plus 自己的点击处理更早,
  // nextTick 的回调会排在该组件的渲染任务前面,读到的是上一帧的排序态。rAF 在 DOM 更新之后。
  requestAnimationFrame(() => {
    if (th.classList.contains('ascending') || th.classList.contains('descending')) show(th)
    else hide()
  })
}

/** 该列被横向滚动裁出表格可视区时,气泡不该继续钉在看不见的位置 */
function isInTableView(th: HTMLElement): boolean {
  const box = th.closest('.el-table')
  if (!box) return true
  const cell = th.getBoundingClientRect()
  const table = box.getBoundingClientRect()
  return cell.right > table.left + 4 && cell.left < table.right - 4
}

/** 横向滚动、列宽拖拽、外层容器换尺寸都会把表头挪走 */
function reposition() {
  if (!hint.value || !anchor?.isConnected || !isInTableView(anchor)) {
    hide()
    return
  }
  if (anchor) place(anchor)
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') hide()
}

document.addEventListener('pointerover', handlePointerOver, true)
document.addEventListener('click', handleClick, true)
document.addEventListener('scroll', reposition, true)
document.addEventListener('keydown', handleKeydown)
window.addEventListener('resize', reposition)

onUnmounted(() => {
  document.removeEventListener('pointerover', handlePointerOver, true)
  document.removeEventListener('click', handleClick, true)
  document.removeEventListener('scroll', reposition, true)
  document.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('resize', reposition)
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="hint"
      ref="hintEl"
      class="sort-header-hint"
      aria-hidden="true"
      :style="{ left: `${hint.x}px`, top: `${hint.y}px` }"
    >
      <div class="sort-header-hint-main">{{ hint.main }}</div>
      <div class="sort-header-hint-sub">{{ hint.sub }}</div>
    </div>
  </Teleport>
</template>

<style scoped>
.sort-header-hint {
  position: fixed;
  z-index: 3000;
  max-width: 300px;
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--card-bg);
  box-shadow: var(--elev-shadow);
  font-size: 12px;
  line-height: 1.5;
  pointer-events: none;
}

.sort-header-hint-main {
  color: var(--text-primary);
  font-weight: 600;
}

.sort-header-hint-sub {
  margin-top: 2px;
  color: var(--text-muted);
}
</style>
