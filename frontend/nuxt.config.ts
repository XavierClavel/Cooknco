import { fileURLToPath, URL } from 'node:url'

// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-01-01',
  srcDir: 'src',

  // SSR is turned on in a later stage, once every client-only assumption in
  // src/ has been flushed out. Until then this is the same SPA as before.
  ssr: false,

  modules: [
    'vuetify-nuxt-module',
    '@pinia/nuxt',
    '@nuxtjs/i18n',
    '@nuxt/eslint',
  ],

  css: ['@/styles/global.scss'],

  // Keeps the ambient feel the old unplugin-auto-import config provided, so
  // helpers in src/scripts and stores stay importable without ceremony.
  imports: {
    dirs: ['stores'],
  },

  runtimeConfig: {
    // Server-only. The public hostname is not resolvable from inside the
    // cluster network, so server-side fetches must address the backend directly.
    apiInternalUrl: 'http://cooknco-backend:8080/api/v1',
    public: {
      // Relative by default: same-origin through the Nitro proxy, which also
      // means `npm run dev` no longer talks to the production API the way the
      // old .env.development did.
      apiUrl: '/api/v1',
      imgUrl: '/image',
      // Absolute, and required: og:url and og:image must be absolute URLs.
      siteUrl: 'https://cooknco.eu',
    },
  },

  vuetify: {
    moduleOptions: {
      styles: { configFile: 'src/styles/settings.scss' },
      // Feeds the real viewport to the server via Sec-CH-Viewport-Width so the
      // navigation drawer's initial open/closed state does not flip on
      // hydration. Only meaningful once ssr is on, harmless before.
      ssrClientHints: { viewportSize: true },
    },
    vuetifyOptions: './vuetify.config.ts',
  },

  i18n: {
    // no_prefix is load-bearing: the default strategy would prepend /en/ and
    // /fr/ to every route and break every link already shared.
    strategy: 'no_prefix',
    defaultLocale: 'en',
    locales: [
      { code: 'en', language: 'en-US' },
      { code: 'fr', language: 'fr-FR' },
    ],
    vueI18n: './i18n.config.ts',
    detectBrowserLanguage: {
      useCookie: true,
      // Same key the app already writes, so nobody loses their preference.
      cookieKey: 'locale',
      redirectOn: 'root',
      fallbackLocale: 'en',
    },
  },

  app: {
    head: {
      // Carried over from the deleted index.html.
      title: 'Cook&Co',
      htmlAttrs: { lang: 'en' },
      meta: [
        { name: 'viewport', content: 'width=device-width, initial-scale=1, viewport-fit=cover' },
      ],
      link: [
        { rel: 'icon', href: '/favicon.ico' },
        { rel: 'preconnect', href: 'https://fonts.googleapis.com' },
        { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: '' },
        {
          rel: 'stylesheet',
          href: 'https://fonts.googleapis.com/css2?family=Geologica:wght@100;300;400;500;700&display=swap',
        },
      ],
    },
  },

  build: {
    transpile: ['vuetify', 'vuedraggable'],
  },

  alias: {
    '@': fileURLToPath(new URL('./src', import.meta.url)),
  },

  nitro: {
    compressPublicAssets: { gzip: true, brotli: true },
  },

  devtools: { enabled: true },
})
