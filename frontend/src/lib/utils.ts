import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

/** 合并 Tailwind 类名：后写的同类工具类覆盖先写的，避免样式拼装冲突。 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/**
 * 后端返回的 ISO 时间（如 2024-05-01T12:34:56.789）在前端统一裁剪为「YYYY-MM-DD HH:mm:ss」。
 * 不做时区换算：数据库里存的就是本地时间语义，用 Date 解析反而会引入偏移。
 */
export function formatTime(value?: string | null, fallback = '—') {
  if (!value) return fallback
  return value.replace('T', ' ').slice(0, 19)
}

/** 读设计令牌的 CSS 变量（存的是 HSL 分量），供 ECharts 等无法用 Tailwind 类的场合取色。 */
export function cssVar(name: string, fallback: string) {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value ? `hsl(${value})` : fallback
}
