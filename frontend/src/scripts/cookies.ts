/**
 * Cookie helpers over Nuxt's useCookie.
 *
 * Replaces vue-cookies, which reached for a bare `$cookies` global that only
 * exists in the browser. useCookie JSON-encodes objects the same way, so the
 * existing `recipeQuery` object cookie keeps working, and it falls back to the
 * raw string for plain values like `redirectedFrom`.
 */
export {
  COOKIE_LOCALE,
  getCookie,
  setCookie,
  deleteCookie,
}

const COOKIE_LOCALE = 'locale'

const OPTS = {
  path: '/',
  maxAge: 60 * 60 * 24 * 365,
  sameSite: 'lax',
} as const

function getCookie<T = any>(key: string): T | null | undefined {
  return useCookie<T>(key, OPTS).value
}

function setCookie(key: string, value: any) {
  useCookie(key, OPTS).value = value
}

function deleteCookie(key: string) {
  useCookie(key, { path: '/' }).value = null
}
