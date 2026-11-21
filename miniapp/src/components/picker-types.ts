/** 通用选择器选项：企业端各页共用（PcikerField 与调用方都从这里取） */
export interface PickerOption {
  value: string
  label: string
  sub?: string
  /** 额外业务字段(如班级的 termId),由调用方自行取用 */
  meta?: Record<string, unknown>
}
