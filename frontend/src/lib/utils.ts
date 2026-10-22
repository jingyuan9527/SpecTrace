import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

/** 合并 Tailwind 类名：后写的同类工具类覆盖先写的，避免样式拼装冲突。 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
