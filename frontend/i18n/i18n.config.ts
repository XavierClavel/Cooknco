import en from '../src/locales/en'
import fr from '../src/locales/fr'

/**
 * Consumed by @nuxtjs/i18n. Unlike the old src/plugins/i18n.ts this is a
 * factory, not a module-scope createI18n() singleton: the module builds one
 * instance per request, so a server render can never leak one visitor's locale
 * into another's. Locale detection moved to detectBrowserLanguage in
 * nuxt.config.ts (it reads Accept-Language server-side and the `locale` cookie),
 * replacing the navigator.language read that used to run at import time.
 */
export default defineI18nConfig(() => ({
  legacy: false,
  fallbackLocale: 'en',
  messages: { en, fr },
}))
