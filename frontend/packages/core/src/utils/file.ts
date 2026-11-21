/** 文件展示相关的纯函数，供文件管理页与上传/图片组件共用。 */

const IMAGE_EXTENSIONS = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'])

/** 文件大小（字节）→ 人读文案；小数最多一位，KB 以下不拆小数 */
export function formatFileSize(sizeBytes?: number | null): string {
  if (sizeBytes === null || sizeBytes === undefined || Number.isNaN(sizeBytes)) {
    return '-'
  }
  if (sizeBytes < 1024) {
    return `${sizeBytes} B`
  }
  const units = ['KB', 'MB', 'GB']
  let value = sizeBytes / 1024
  let unitIndex = 0
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex += 1
  }
  return `${value.toFixed(1)} ${units[unitIndex]}`
}

/** 是否可作为 <img> 内联预览的图片类型（与后端 FileMediaTypes.inlineSafe 同口径，不含 svg） */
export function isImageFile(extension?: string | null, contentType?: string | null): boolean {
  if (contentType?.startsWith('image/')) {
    // svg 可携带脚本，后端一律按附件下发，这里也不当图片渲染
    return contentType !== 'image/svg+xml'
  }
  return extension ? IMAGE_EXTENSIONS.has(extension.toLowerCase()) : false
}
