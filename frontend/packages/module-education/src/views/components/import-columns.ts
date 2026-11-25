/** xlsx 批量导入弹窗的列配置：key 对应后端 XlsxImportRowView.cells 的键 */
export interface ImportColumn {
  key: string
  label: string
  /** 固定列宽（px）；不写则由表格均分 */
  width?: number
  /** 字典类型码：预览里按字典翻译成中文（与列表页 displayLabel 同口径） */
  dictType?: string
  /** 代码判定值等不走字典的列，用页面自己的中文映射格式化 */
  format?: (value: string | null | undefined) => string
}
