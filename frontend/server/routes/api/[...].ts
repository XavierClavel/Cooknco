/**
 * Proxies /api/** to the Ktor backend, replacing nginx's `location /api/`.
 *
 * A route handler rather than a routeRules `proxy` entry because routeRules
 * bake their target in at build time: this way the same image runs against a
 * local backend, staging or production by setting NUXT_BACKEND_URL.
 *
 * Nitro's proxy does not forward the client address the way nginx's
 * proxy_set_header did, so it is passed on explicitly — the backend logs and
 * any IP-based limiting would otherwise see the container.
 */
export default defineEventHandler((event) => {
  const { backendUrl } = useRuntimeConfig(event)
  return proxyRequest(event, `${backendUrl}${event.path}`, {
    headers: forwardedHeaders(event),
  })
})
