import { buildImageUrl } from '@/scripts/common'

/**
 * Emits the Open Graph / Twitter card tags that make a shared Cook&Co link
 * unfurl with a picture, a title and a description.
 *
 * Must be called at the top level of a page's <script setup>, never inside
 * <ClientOnly>: tags added after hydration are invisible to a crawler, which
 * only ever reads the server's HTML.
 */

type Kind = 'recipe' | 'user' | 'cookbook' | 'site'

/** From the backend's ImageController SIZE_* constants. */
const DIMENSIONS: Record<Kind, [number, number]> = {
  recipe: [1600, 1200],
  user: [400, 400],
  cookbook: [500, 500],
  site: [1200, 630],
}

/**
 * Purpose-built 1200x630 cards. The in-app defaults are unusable here:
 * default_cookbook.png is 250x373 (portrait, below every crawler's minimum)
 * and default_user.jpg is actually a WebP despite the extension.
 */
const FALLBACK_IMAGE: Record<Kind, string> = {
  recipe: '/og/recipe.png',
  user: '/og/user.png',
  cookbook: '/og/cookbook.png',
  site: '/og/site.png',
}

/** Which directory the backend serves each kind's images from. */
const IMAGE_DIR = {
  recipe: 'recipes',
  user: 'users',
  cookbook: 'cookbooks',
} as const

const SITE_NAME = 'Cook&Co'
const SITE_DESCRIPTION = 'Write cooking recipes and share them with friends.'

export interface ShareMeta {
  kind: Kind
  title?: string | null
  description?: string | null
  imageId?: number | string | null
  imageVersion?: number | string | null
  /** Set for entities the visitor is not allowed to see. */
  noindex?: boolean
}

/** Crawlers reject a relative og:image, and the in-app builders return one for defaults. */
function absolute(url: string, siteUrl: string): string {
  if (!url) return ''
  if (/^https?:\/\//.test(url)) return url
  return `${siteUrl}${url.startsWith('/') ? '' : '/'}${url}`
}

function truncate(text: string | null | undefined, max = 200): string {
  const s = (text ?? '').replace(/\s+/g, ' ').trim()
  if (s.length <= max) return s
  const cut = s.lastIndexOf(' ', max - 1)
  return `${s.slice(0, cut > 0 ? cut : max - 1).trimEnd()}…`
}

export function useShareMeta(meta: MaybeRefOrGetter<ShareMeta>) {
  // Captured here, during setup. unhead evaluates the getters below lazily,
  // outside the Nuxt async context, where useRuntimeConfig() would throw.
  const { siteUrl, imgUrl } = useRuntimeConfig().public
  const route = useRoute()

  const resolved = computed(() => {
    const m = toValue(meta)
    const hasImage = !!m.imageId && !!m.imageVersion
    const [width, height] = hasImage ? DIMENSIONS[m.kind] : DIMENSIONS.site
    const image = hasImage && m.kind !== 'site'
      ? buildImageUrl(imgUrl, IMAGE_DIR[m.kind], m.imageId, m.imageVersion)
      : FALLBACK_IMAGE[m.kind]

    return {
      title: m.title ? `${m.title} | ${SITE_NAME}` : SITE_NAME,
      bareTitle: m.title || SITE_NAME,
      description: truncate(m.description) || SITE_DESCRIPTION,
      image: absolute(image, siteUrl),
      // Uploads are WebP; the fallback cards are PNG.
      imageType: hasImage ? 'image/webp' : 'image/png',
      width,
      height,
      url: `${siteUrl}${route.path}`,
      ogType: m.kind === 'user' ? 'profile' : m.kind === 'site' ? 'website' : 'article',
      robots: m.noindex ? 'noindex, nofollow' : 'index, follow',
    }
  })

  useSeoMeta({
    title: () => resolved.value.title,
    description: () => resolved.value.description,

    ogSiteName: SITE_NAME,
    ogType: () => resolved.value.ogType,
    ogUrl: () => resolved.value.url,
    ogTitle: () => resolved.value.bareTitle,
    ogDescription: () => resolved.value.description,
    ogImage: () => resolved.value.image,
    ogImageSecureUrl: () => resolved.value.image,
    ogImageType: () => resolved.value.imageType,
    ogImageWidth: () => resolved.value.width,
    ogImageHeight: () => resolved.value.height,
    ogImageAlt: () => resolved.value.bareTitle,

    twitterCard: 'summary_large_image',
    twitterTitle: () => resolved.value.bareTitle,
    twitterDescription: () => resolved.value.description,
    twitterImage: () => resolved.value.image,
    twitterImageAlt: () => resolved.value.bareTitle,

    robots: () => resolved.value.robots,
  })

  useHead({
    link: [{ rel: 'canonical', href: () => resolved.value.url }],
  })
}
