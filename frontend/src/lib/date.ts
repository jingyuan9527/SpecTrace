/**
 * 日期文本与 Date 的互转工具：全站日期统一用 `yyyy-MM-dd`（年-月-日顺序）存储与展示，
 * 解析时兼容用户手输的常见写法，避免录入历史发布日期时被格式卡住。
 */

/**
 * 归一化日期文本。
 *
 * <p>支持 `2025-09-10`、`2025/9/10`、`2025.9.10`、`2025年9月10日`、`20250910`；
 * 空值返回空串（表示未填），无法识别或日期不存在时返回 null（由调用方提示）。</p>
 */
export function normalizeDateText(raw?: string | null): string | null {
  const text = (raw ?? '').trim()
  if (!text) return ''
  const separated = text.match(/^(\d{4})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})\s*日?$/)
  const compact = text.match(/^(\d{4})(\d{2})(\d{2})$/)
  const matched = separated ?? compact
  if (!matched) return null
  const year = Number(matched[1])
  const month = Number(matched[2])
  const day = Number(matched[3])
  // Date 会把 2 月 30 日顺延到 3 月，回读校验才能拦住不存在的日期
  const date = new Date(year, month - 1, day)
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
    return null
  }
  return `${year}-${pad2(month)}-${pad2(day)}`
}

/** ISO 文本转本地 Date；空值或非法文本返回 null。 */
export function parseIsoDate(value?: string | null): Date | null {
  const normalized = normalizeDateText(value)
  if (!normalized) return null
  const [year, month, day] = normalized.split('-').map(Number)
  return new Date(year, month - 1, day)
}

/** 本地 Date 转 `yyyy-MM-dd`；不能用 toISOString，它按 UTC 会跨天。 */
export function toIsoDate(date: Date): string {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

function pad2(value: number): string {
  return String(value).padStart(2, '0')
}
