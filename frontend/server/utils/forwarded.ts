import type { H3Event } from 'h3'

/**
 * The X-Forwarded-* headers nginx used to set on its way to the backend.
 * Nitro's proxy does not add them, so without this the backend sees the
 * frontend container as the client for every request.
 */
export function forwardedHeaders(event: H3Event): Record<string, string> {
  const url = getRequestURL(event)
  const existing = getRequestHeader(event, 'x-forwarded-for')
  const client = getRequestIP(event, { xForwardedFor: true })

  const headers: Record<string, string> = {
    'x-forwarded-proto': getRequestHeader(event, 'x-forwarded-proto') || url.protocol.replace(':', ''),
    'x-forwarded-host': getRequestHeader(event, 'x-forwarded-host') || url.host,
  }

  const forwardedFor = existing || client
  if (forwardedFor) headers['x-forwarded-for'] = forwardedFor
  if (client) headers['x-real-ip'] = client

  return headers
}
