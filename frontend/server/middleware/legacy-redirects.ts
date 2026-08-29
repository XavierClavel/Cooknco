/**
 * 301s the old query-string share URLs onto the new pretty routes:
 *
 *   /recipe/view?id=12        -> /recipe/12
 *   /user/view/?user=3        -> /user/3
 *   /cookbook/view?cookbook=5 -> /cookbook/5
 *
 * This is a Nitro server middleware rather than a redirect on a kept page,
 * because a crawler that does not run JavaScript has to be able to follow it:
 * a client-side redirect would leave it looking at an empty shell, which is the
 * exact problem the pretty URLs and Open Graph tags exist to solve.
 */
const LEGACY: Record<string, { param: string; prefix: string }> = {
  '/recipe/view': { param: 'id', prefix: '/recipe/' },
  '/user/view': { param: 'user', prefix: '/user/' },
  '/cookbook/view': { param: 'cookbook', prefix: '/cookbook/' },
}

export default defineEventHandler((event) => {
  const url = getRequestURL(event)

  // Leave proxied and asset traffic alone.
  if (url.pathname.startsWith('/api')
    || url.pathname.startsWith('/image')
    || url.pathname.startsWith('/_nuxt')) return

  // Links in the wild were written both ways — toViewUser used to emit
  // `/user/view/?user=3`, with the trailing slash.
  const path = url.pathname.replace(/\/+$/, '') || '/'

  const rule = LEGACY[path]
  if (!rule) return

  const id = url.searchParams.get(rule.param)
  if (!id || !/^\d+$/.test(id)) return sendRedirect(event, '/home', 301)

  return sendRedirect(event, `${rule.prefix}${id}`, 301)
})
