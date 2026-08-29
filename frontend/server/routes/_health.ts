/**
 * Container healthcheck target.
 *
 * Deliberately not under /api — that whole namespace is proxied to the Ktor
 * backend, so a probe there would report the backend's health, not this
 * container's.
 */
export default defineEventHandler(() => ({ ok: true }))
