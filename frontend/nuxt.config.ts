import { fileURLToPath, URL } from 'node:url'

const SITE_NAME = 'Cook&Co'
const SITE_DESCRIPTION = 'Write cooking recipes and share them with friends.'
// Baked into the site-wide og:image at build time, because app.head cannot read
// runtimeConfig. Per-page tags resolve the URL at request time instead.
const SITE_URL = process.env.NUXT_PUBLIC_SITE_URL || 'https://cooknco.eu'

// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-01-01',
  srcDir: 'src',

  // On globally, but only the shareable public routes actually server-render
  // (see routeRules). Everything auth-gated stays a client-only SPA.
  ssr: true,

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
    // Server-only, and the single knob for where the backend lives: both the
    // /api and /image proxies and the server-side data fetches go through it.
    // NUXT_BACKEND_URL.
    //
    // The public hostname is not resolvable from inside the cluster network, so
    // server-side fetches address the backend service directly.
    backendUrl: 'http://cooknco-backend:8080',
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

        // Site-wide link preview. These sit in the SPA shell as well as in
        // server-rendered pages, so even a client-only route (/home, /login,
        // /search) unfurls with a branded card instead of a bare URL. The
        // server-rendered entity pages override them with their own content
        // via useShareMeta — unhead dedupes by property/name.
        { name: 'description', content: SITE_DESCRIPTION },
        { property: 'og:site_name', content: SITE_NAME },
        { property: 'og:type', content: 'website' },
        { property: 'og:title', content: SITE_NAME },
        { property: 'og:description', content: SITE_DESCRIPTION },
        { property: 'og:image', content: `${SITE_URL}/og/site.png` },
        { property: 'og:image:width', content: '1200' },
        { property: 'og:image:height', content: '630' },
        { property: 'og:image:type', content: 'image/png' },
        { property: 'og:image:alt', content: SITE_NAME },
        { name: 'twitter:card', content: 'summary_large_image' },
        { name: 'twitter:title', content: SITE_NAME },
        { name: 'twitter:description', content: SITE_DESCRIPTION },
        { name: 'twitter:image', content: `${SITE_URL}/og/site.png` },
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

  // The /api and /image proxies that replace nginx's location blocks are
  // server routes, not routeRules, so their target stays runtime-configurable
  // (see server/routes/api/[...].ts).
  routeRules: {
    // Server-rendered: the pages people share. Rendered anonymously, so the
    // HTML never contains per-visitor data.
    //
    // If cookie forwarding is ever added to those renders, response caching
    // (swr) must not be turned on in the same breath — it would hand one
    // visitor's render to the next.
    '/recipe/*': { ssr: true },
    '/user/*': { ssr: true },
    '/cookbook/*': { ssr: true },

    '/': { redirect: { to: '/home', statusCode: 301 } },

    // Client-only: auth-gated or personalised. Listed after the wildcards
    // above because Nitro resolves the more specific path first.
    '/home': { ssr: false },
    '/search': { ssr: false },
    '/dashboard': { ssr: false },
    '/admin': { ssr: false },
    '/login': { ssr: false },
    '/logout': { ssr: false },
    '/signup': { ssr: false },
    '/maintenance': { ssr: false },
    '/verification-email-sent': { ssr: false },
    '/recipe/list': { ssr: false },
    '/recipe/edit': { ssr: false },
    '/cookbook/list': { ssr: false },
    '/cookbook/edit': { ssr: false },
    '/user/list': { ssr: false },
    '/user/edit': { ssr: false },
    '/user/settings': { ssr: false },
    '/user/verify': { ssr: false },
    '/ingredient/**': { ssr: false },
    '/password/**': { ssr: false },
  },

  devtools: { enabled: true },
})
