/** 上传前压缩：最长边 1600px、转 WebP，尽量压到 500KB 以内，减少落盘体积与首屏加载时间。 */
const MAX_EDGE = 1600
const QUALITY = 0.85
/** 硬目标体积：超过后逐级降质 / 降尺寸重压 */
export const UPLOAD_TARGET_BYTES = 500 * 1024
/** 体积很小的图片重新编码收益有限，跳过以免画质受损 */
const SKIP_BELOW_BYTES = 200 * 1024
/** 逐级尝试的编码质量与尺寸系数，命中目标体积即停止 */
const QUALITY_STEPS = [QUALITY, 0.7, 0.55]
const EDGE_FACTORS = [1, 0.8]

export interface CompressedImage {
  file: File
  /** 是否重新编码过 */
  compressed: boolean
  /** 压缩后仍超出目标体积，需要提示用户裁剪 */
  exceeded: boolean
}

async function loadBitmap(file: File): Promise<ImageBitmap | HTMLImageElement> {
  if (typeof createImageBitmap === 'function') {
    return createImageBitmap(file)
  }
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const image = new Image()
    image.onload = () => {
      URL.revokeObjectURL(url)
      resolve(image)
    }
    image.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('图片解码失败'))
    }
    image.src = url
  })
}

function renameToWebp(name: string) {
  const base = name.includes('.') ? name.slice(0, name.lastIndexOf('.')) : name
  return `${base}.webp`
}

function releaseBitmap(image: ImageBitmap | HTMLImageElement) {
  if ('close' in image && typeof image.close === 'function') {
    image.close()
  }
}

/**
 * 压缩图片；GIF 保留原图以免丢失动画，失败或压缩后反而更大时回退原文件。
 * 超过目标体积时先降质量再降尺寸，仍超限则在结果里标记 exceeded，由调用方提示裁剪。
 */
export async function compressImage(
  file: File,
  options: { maxEdge?: number; maxBytes?: number } = {},
): Promise<CompressedImage> {
  const target = options.maxBytes ?? UPLOAD_TARGET_BYTES
  const maxEdge = options.maxEdge ?? MAX_EDGE
  const skipped = { file, compressed: false, exceeded: file.size > target }

  if (!file.type.startsWith('image/') || file.type === 'image/gif') {
    return skipped
  }

  try {
    const image = await loadBitmap(file)
    const width = 'width' in image ? image.width : 0
    const height = 'height' in image ? image.height : 0
    if (!width || !height) {
      releaseBitmap(image)
      return skipped
    }

    const needsScale = Math.max(width, height) > maxEdge
    // 既不需要缩放又已经足够小，直接原图上传
    if (!needsScale && file.size <= Math.min(SKIP_BELOW_BYTES, target)) {
      releaseBitmap(image)
      return { file, compressed: false, exceeded: false }
    }

    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d')
    if (!ctx) {
      releaseBitmap(image)
      return skipped
    }

    let best: Blob | null = null
    let reached = false
    for (const factor of EDGE_FACTORS) {
      const edge = Math.round(maxEdge * factor)
      const scale = Math.min(1, edge / Math.max(width, height))
      canvas.width = Math.round(width * scale)
      canvas.height = Math.round(height * scale)
      ctx.clearRect(0, 0, canvas.width, canvas.height)
      ctx.drawImage(image as CanvasImageSource, 0, 0, canvas.width, canvas.height)

      for (const quality of QUALITY_STEPS) {
        const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/webp', quality))
        if (!blob) continue
        if (!best || blob.size < best.size) best = blob
        if (blob.size <= target) {
          reached = true
          break
        }
      }
      if (reached) break
    }
    releaseBitmap(image)

    if (!best || best.size >= file.size) {
      return skipped
    }
    const result = new File([best], renameToWebp(file.name), { type: 'image/webp' })
    return { file: result, compressed: true, exceeded: result.size > target }
  } catch {
    return skipped
  }
}

export function formatBytes(bytes: number) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
