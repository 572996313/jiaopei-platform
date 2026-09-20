/**
 * 表格视口统一规范:
 * - 主列表:表格传 height="100%" 占满所在卡片,卡片/页面(见 styles/index.css 视口填充段)
 *   负责给出确定高度;数据超出表体内部滚动,表头吸顶,整页尽量一屏放下。
 * - 次要表格(Tab 叠放、弹窗内):按行数上限(px)封顶(见 styles/index.css 的 .resource-grid
 *   段,数据少时的保底高度也在那边以 CSS 实现)。
 * 行高常量与 styles/index.css 里 .el-table 的紧凑密度覆盖保持同一档位。
 */

/** 主列表表格:占满父容器剩余高度(el-table :height) */
export const TABLE_FILL_HEIGHT = '100%'

/** 表头高度(px),与 .el-table 紧凑表头覆盖一致 */
const TABLE_HEADER_HEIGHT = 38

/** 数据行平均高度(px):常规行约 33px,含按钮/标签的行约 42px,取均值档位 */
const TABLE_ROW_HEIGHT = 36

/** 按行数计算表格高度上限,返回可直接传给 el-table 的 CSS 值 */
export function tableViewportHeight(rows: number): string {
  return `${TABLE_HEADER_HEIGHT + TABLE_ROW_HEIGHT * rows}px`
}

/** 6 行档位上限:弹窗内、详情卡内的次要表格(el-table :max-height) */
export const TABLE_VIEWPORT_MAX_HEIGHT = tableViewportHeight(6)

/** 10 行档位上限:同屏叠放的次级长列表(日课表、点名名单)(el-table :max-height) */
export const TABLE_STACK_MAX_HEIGHT = tableViewportHeight(10)
