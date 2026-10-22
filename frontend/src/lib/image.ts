/** 上传前压缩：最长边 1600px、转 WebP，减少落盘体积与首屏加载时间。 */
const MAX_EDGE = 1600
const QUALITY = 0.85
/** 体积很小的图片重新编码收益有限，跳过以免画质受损 */
const SKIP_BELOW_BYTES = 200 * 1024

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

/**
 * 压缩图片；GIF 保留原图以免丢失动画，失败或压缩后反而更大时回退原文件。
 */
export async function compressImage(file: File, maxEdge = MAX_EDGE, quality = QUALITY): Promise<File> {
  if (!file.type.startsWith('image/') || file.type === 'image/gif') {
    return file
  }
  try {
    const image = await loadBitmap(file)
    const width = 'width' in image ? image.width : 0
    const height = 'height' in image ? image.height : 0
    if (!width || !height) return file

    const scale = Math.min(1, maxEdge / Math.max(width, height))
    if (scale === 1 && file.size <= SKIP_BELOW_BYTES) {
      return file
    }

    const canvas = document.createElement('canvas')
    canvas.width = Math.round(width * scale)
    canvas.height = Math.round(height * scale)
    const ctx = canvas.getContext('2d')
    if (!ctx) return file
    ctx.drawImage(image as CanvasImageSource, 0, 0, canvas.width, canvas.height)
    if ('close' in image && typeof image.close === 'function') {
      image.close()
    }

    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/webp', quality))
    if (!blob || blob.size >= file.size) {
      return file
    }
    return new File([blob], renameToWebp(file.name), { type: 'image/webp' })
  } catch {
    return file
  }
}

export function formatBytes(bytes: number) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
