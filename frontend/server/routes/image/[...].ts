/**
 * Proxies /image/** to the backend's static image routes, replacing nginx's
 * `location /image/`. See server/routes/api/[...].ts for why this is a handler
 * rather than a routeRules proxy.
 *
 * Image URLs carry the entity's version (42-v3.webp) and a new upload bumps it,
 * so the response is safely immutable — same six-month policy nginx had.
 */
export default defineEventHandler(async (event) => {
  const { backendUrl } = useRuntimeConfig(event)

  setResponseHeader(event, 'cache-control', 'public, max-age=15552000, immutable')

  return proxyRequest(event, `${backendUrl}${event.path}`, {
    headers: forwardedHeaders(event),
  })
})
