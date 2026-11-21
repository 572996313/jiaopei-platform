/**
 * 弹窗右下角拖拽改尺寸（全站统一，不改任何弹窗模板）。
 *
 * Element Plus 的 el-dialog 只有 draggable（拖标题移动），没有 resizable。
 * 这里在 document 上挂一个捕获阶段的 pointerdown：指针落在某个 .el-dialog
 * 右下角热区内时接管为缩放，拖动过程中改写该弹窗元素的内联 width/height。
 * 可视抓手与光标在 styles/index.css 的 `.el-dialog::after` 上。
 *
 * 只认「右下角」一个方向：左侧/顶部固定不动，符合“弹窗长在左上角”的直觉，
 * 也不会和 Element Plus 自己的标题拖拽抢事件（那条只绑在 header 上）。
 */

/** 右下角热区边长（px），与 CSS 里 ::after 抓手大致同尺寸 */
const HOT_ZONE = 16
/** 缩放下限，太窄的表单没法用 */
const MIN_WIDTH = 360
const MIN_HEIGHT = 200
/** 与视口边缘至少留出的空档 */
const VIEWPORT_GAP = 8
/** 与全局样式 `.el-dialog { max-height: calc(100vh - 48px) }` 对齐 */
const MAX_HEIGHT_RESERVE = 48

interface ResizeState {
  dialog: HTMLElement
  startX: number
  startY: number
  startWidth: number
  startHeight: number
  maxWidth: number
  maxHeight: number
}

let state: ResizeState | null = null
let installed = false

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

/** 从事件目标向上找最近的可缩放弹窗；全屏弹窗不参与 */
function findDialog(target: EventTarget | null): HTMLElement | null {
  if (!(target instanceof Element)) {
    return null
  }
  const dialog = target.closest('.el-dialog')
  if (!(dialog instanceof HTMLElement) || dialog.classList.contains('is-fullscreen')) {
    return null
  }
  return dialog
}

function stopResize() {
  if (!state) {
    return
  }
  state = null
  document.body.classList.remove('is-dialog-resizing')
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', stopResize)
  window.removeEventListener('pointercancel', stopResize)
}

function onPointerMove(event: PointerEvent) {
  if (!state) {
    return
  }
  const { dialog, startX, startY, startWidth, startHeight, maxWidth, maxHeight } = state
  const width = clamp(startWidth + event.clientX - startX, MIN_WIDTH, maxWidth)
  const height = clamp(startHeight + event.clientY - startY, MIN_HEIGHT, maxHeight)
  // 直接写内联尺寸，压过 `width: var(--el-dialog-width)`；体部是 flex + overflow-y:auto，内容自己滚
  dialog.style.width = `${width}px`
  dialog.style.height = `${height}px`
}

function onPointerDown(event: PointerEvent) {
  if (event.button !== 0) {
    return
  }
  const dialog = findDialog(event.target)
  if (!dialog) {
    return
  }
  const rect = dialog.getBoundingClientRect()
  // 只认右下角：两个方向都在热区内才算
  if (rect.right - event.clientX > HOT_ZONE || rect.bottom - event.clientY > HOT_ZONE) {
    return
  }

  event.preventDefault()
  event.stopPropagation()
  // 指针移到弹窗外也继续跟手；pointerup 时浏览器自动释放
  try {
    dialog.setPointerCapture(event.pointerId)
  } catch {
    // 个别环境拿不到指针捕获，退回 window 监听（下面已挂）
  }

  /*
   * 弹窗平时靠 `margin: 24px auto` 居中。宽度一变，auto 外边距会把它重新摆到中间，
   * 表现就是“拖右缘、左缘也跟着跑”。先把当前左缘钉成固定 margin-left，后面只往右长。
   */
  dialog.style.marginLeft = `${dialog.offsetLeft}px`
  dialog.style.marginRight = 'auto'
  dialog.style.width = `${rect.width}px`
  dialog.style.height = `${rect.height}px`

  state = {
    dialog,
    startX: event.clientX,
    startY: event.clientY,
    startWidth: rect.width,
    startHeight: rect.height,
    maxWidth: Math.max(MIN_WIDTH, window.innerWidth - Math.max(rect.left, 0) - VIEWPORT_GAP),
    maxHeight: Math.max(
      MIN_HEIGHT,
      Math.min(
        window.innerHeight - Math.max(rect.top, 0) - VIEWPORT_GAP,
        window.innerHeight - MAX_HEIGHT_RESERVE
      )
    ),
  }
  document.body.classList.add('is-dialog-resizing')
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', stopResize)
  window.addEventListener('pointercancel', stopResize)
}

/**
 * 安装全局弹窗缩放。由壳工程 main.ts 调用一次即可；重复调用无副作用。
 */
export function installDialogResize() {
  if (installed) {
    return
  }
  installed = true
  document.addEventListener('pointerdown', onPointerDown, true)
}
