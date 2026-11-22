/** 头像上传前的浏览器侧预处理，PC 端"个人信息"面板与旧头像弹窗共用同一套口径 */

/** 与后端 `user.avatar.max_size_mb` 默认值一致；服务端仍会按系统参数再校验一次 */
export const AVATAR_MAX_BYTES = 2 * 1024 * 1024

/** 输出边长；头像展示位最大 96px，512 足够清晰且体积稳定 */
const MAX_SIDE = 512

/**
 * 居中裁成正方形、缩到 512×512 并转 JPEG。
 *
 * <p>手机原图动辄几 MB，直接传会被 2MB 上限挡住；同时借 canvas 重绘修正 EXIF 方向。
 * 浏览器不支持 `createImageBitmap` 或解码失败时返回原文件，交给服务端做最终校验。</p>
 */
export async function normalizeAvatarFile(file: File): Promise<File> {
  if (typeof createImageBitmap !== 'function') {
    return file
  }
  const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
  try {
    const side = Math.min(bitmap.width, bitmap.height)
    const canvas = document.createElement('canvas')
    canvas.width = MAX_SIDE
    canvas.height = MAX_SIDE
    const context = canvas.getContext('2d')
    if (!context) {
      return file
    }
    // 先铺白底：转 JPEG 时透明区域会变黑，头像这种小图铺白更自然
    context.fillStyle = '#ffffff'
    context.fillRect(0, 0, MAX_SIDE, MAX_SIDE)
    context.drawImage(
      bitmap,
      (bitmap.width - side) / 2,
      (bitmap.height - side) / 2,
      side,
      side,
      0,
      0,
      MAX_SIDE,
      MAX_SIDE
    )
    const blob = await new Promise<Blob | null>((resolve) =>
      canvas.toBlob(resolve, 'image/jpeg', 0.9)
    )
    if (!blob) {
      return file
    }
    return new File([blob], 'avatar.jpg', { type: 'image/jpeg' })
  } finally {
    bitmap.close()
  }
}
