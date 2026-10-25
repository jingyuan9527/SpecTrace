/**
 * 品牌视觉映射：机型无图时的文字卡渐变色。
 *
 * <p>绝大多数机型没有真实产品图（历史机型官图难觅、外链有防盗链），产品决策是
 * 「无图为默认设计」：品牌色渐变 + 机型名文字卡作为一等公民，而非凑合的占位图。</p>
 *
 * <p>类名必须是完整静态字符串（Tailwind JIT 按源码扫描，运行时拼接不生效）。</p>
 */

export interface BrandTheme {
  from: string
  to: string
}

const BRAND_THEMES: Array<{ match: string[]; theme: BrandTheme }> = [
  { match: ['苹果', 'apple', 'iphone'], theme: { from: 'from-slate-400', to: 'to-slate-600' } },
  { match: ['华为', 'huawei'], theme: { from: 'from-red-500', to: 'to-rose-600' } },
  { match: ['荣耀', 'honor'], theme: { from: 'from-sky-400', to: 'to-blue-600' } },
  { match: ['小米', 'xiaomi'], theme: { from: 'from-orange-500', to: 'to-amber-600' } },
  { match: ['红米', 'redmi'], theme: { from: 'from-orange-600', to: 'to-red-600' } },
  { match: ['iqoo'], theme: { from: 'from-blue-500', to: 'to-indigo-600' } },
  { match: ['一加', 'oneplus'], theme: { from: 'from-red-500', to: 'to-red-700' } },
  { match: ['真我', 'realme'], theme: { from: 'from-amber-500', to: 'to-orange-600' } },
  { match: ['三星', 'samsung'], theme: { from: 'from-blue-600', to: 'to-indigo-700' } },
  { match: ['oppo'], theme: { from: 'from-green-500', to: 'to-emerald-600' } },
  { match: ['vivo'], theme: { from: 'from-blue-500', to: 'to-blue-700' } },
  { match: ['魅族', 'meizu'], theme: { from: 'from-blue-400', to: 'to-indigo-500' } },
  { match: ['努比亚', '红魔', 'nubia'], theme: { from: 'from-red-600', to: 'to-rose-700' } },
]

const DEFAULT_THEME: BrandTheme = { from: 'from-slate-500', to: 'to-slate-700' }

/** 按品牌名（中英文、含子品牌名）模糊匹配渐变主题，未识别走中性色。 */
export function brandGradient(brand?: string | null): BrandTheme {
  const name = (brand ?? '').toLowerCase()
  if (!name) {
    return DEFAULT_THEME
  }
  return BRAND_THEMES.find((item) => item.match.some((keyword) => name.includes(keyword)))?.theme ?? DEFAULT_THEME
}
