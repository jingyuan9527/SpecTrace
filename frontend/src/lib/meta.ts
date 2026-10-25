/**
 * 页面级 SEO 元信息：标题、描述与 Open Graph。
 * 详情页在数据加载后覆盖，路由切换时由 router.afterEach 复位为默认值。
 */
const DEFAULT_TITLE = 'SpecTrace · 手机参数对比平台'
const DEFAULT_DESCRIPTION = 'SpecTrace · 手机参数对比平台：全品牌机型参数、横向对比、跨代演变与一键分享'

type MetaKey = 'name' | 'property'

function upsertMeta(key: MetaKey, name: string, content?: string | null) {
  let element = document.head.querySelector<HTMLMetaElement>(`meta[${key}="${name}"]`)
  if (!content) {
    element?.remove()
    return
  }
  if (!element) {
    element = document.createElement('meta')
    element.setAttribute(key, name)
    document.head.appendChild(element)
  }
  element.setAttribute('content', content)
}

export function setPageMeta(options: {
  title: string
  description?: string | null
  image?: string | null
  url?: string | null
}) {
  document.title = options.title
  upsertMeta('name', 'description', options.description || DEFAULT_DESCRIPTION)
  upsertMeta('property', 'og:title', options.title)
  upsertMeta('property', 'og:description', options.description || DEFAULT_DESCRIPTION)
  upsertMeta('property', 'og:type', 'website')
  upsertMeta('property', 'og:url', options.url || location.href)
  upsertMeta('property', 'og:image', options.image || null)
}

export function resetPageMeta() {
  setPageMeta({ title: DEFAULT_TITLE, description: DEFAULT_DESCRIPTION, image: null })
  setJsonLd(null)
}

const JSON_LD_ID = 'spectrace-json-ld'

/**
 * 结构化数据（T89）：详情页注入 Product schema，供搜索引擎富结果使用。
 * 传 null 清除标签，路由切换时随 resetPageMeta 一起复位。
 */
export function setJsonLd(data: Record<string, unknown> | null) {
  document.getElementById(JSON_LD_ID)?.remove()
  if (!data) return
  const script = document.createElement('script')
  script.id = JSON_LD_ID
  script.type = 'application/ld+json'
  script.textContent = JSON.stringify(data)
  document.head.appendChild(script)
}

/** 相对图片地址转绝对 URL，社交平台抓取时要求绝对路径。 */
export function absoluteUrl(path?: string | null) {
  if (!path) return null
  try {
    return new URL(path, location.origin).href
  } catch {
    return null
  }
}
