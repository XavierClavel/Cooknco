/**
 * Thin wrapper over @nuxtjs/i18n, keeping the call signatures the rest of the
 * app already uses.
 *
 * Everything here resolves the i18n instance *lazily*, per call. The previous
 * version destructured a module-scope `createI18n()` singleton at import time
 * and read `navigator.language` while doing so — which crashes on the server
 * and, worse, would let one request's locale bleed into another's render.
 */
export {
  forceLocale,
  getLocale,
  t,
}

function i18n() {
  return useNuxtApp().$i18n
}

/** Current locale code, lowercase ('en' | 'fr'). */
function getLocale(): string {
  return unref(i18n().locale)
}

/**
 * Translate. Exported as a lazy function rather than a destructured `t` so it
 * binds to the current request's i18n instance instead of a shared singleton.
 */
function t(key: string, ...args: any[]): string {
  return (i18n().t as any)(key, ...args)
}

/** Switch locale and persist it to the `locale` cookie (the module writes it). */
function forceLocale(locale: string) {
  if (!i18n().availableLocales.includes(locale as never)) return
  if (locale === getLocale()) return
  return setLocale(locale as never)
}
