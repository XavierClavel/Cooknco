// Plugins
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import Fonts from 'unplugin-fonts/vite'
import Layouts from 'vite-plugin-vue-layouts'
import Vue from '@vitejs/plugin-vue'
import VueRouter from 'unplugin-vue-router/vite'
import Vuetify, { transformAssetUrls } from 'vite-plugin-vuetify'

// Utilities
import { defineConfig } from 'vite'
import { fileURLToPath, URL } from 'node:url'
import {aliases, mdi} from "vuetify/iconsets/mdi";

/**
 * The backoffice is a second entry point (admin.html) rather than a route of the
 * public app: it has its own design system and must not ship in the bundle every
 * visitor downloads. In production nginx maps /admin -> /admin.html; the dev
 * server needs the same rewrite.
 */
const adminEntry = () => ({
  name: 'admin-entry',
  configureServer(server) {
    server.middlewares.use((req, _res, next) => {
      const [path] = (req.url || '').split('?')
      if (path === '/admin' || path.startsWith('/admin/')) req.url = '/admin.html'
      next()
    })
  },
  // unplugin-fonts injects Roboto + Material Design Icons into every HTML entry.
  // The backoffice uses the system font stack and inline SVG icons, so those
  // preloads would pull down the public app's font assets for nothing.
  transformIndexHtml: {
    order: 'post' as const,
    handler(html: string, ctx: {path: string}) {
      if (!ctx.path.includes('admin.html')) return html
      return html.replace(
        /^[ \t]*<link[^>]*(fonts\.gstatic|fonts\.googleapis|materialdesignicons)[^>]*>\r?\n?/gm,
        '',
      )
    },
  },
})

// https://vitejs.dev/config/
export default defineConfig({
  base: '/',
  build: {
    outDir: 'dist',
    rollupOptions: {
      input: {
        main: fileURLToPath(new URL('./index.html', import.meta.url)),
        admin: fileURLToPath(new URL('./admin.html', import.meta.url)),
      },
    },
  },
  icons: {
    defaultSet: 'mdi',
    aliases,
    sets: {
      mdi,
    },
  },
  plugins: [
    adminEntry(),
    VueRouter({
      dts: 'src/typed-router.d.ts',
      // The backoffice has its own router; keep it out of the public app's routes
      exclude: ['**/admin/**'],
    }),
    Layouts(),
    AutoImport({
      imports: [
        'vue',
        {
          'vue-router/auto': ['useRoute', 'useRouter'],
        }
      ],
      dts: 'src/auto-imports.d.ts',
      eslintrc: {
        enabled: true,
      },
      vueTemplate: true,
    }),
    Components({
      dts: 'src/components.d.ts',
    }),
    Vue({
      template: { transformAssetUrls },
    }),
    // https://github.com/vuetifyjs/vuetify-loader/tree/master/packages/vite-plugin#readme
    Vuetify({
      autoImport: true,
      styles: {
        configFile: 'src/styles/settings.scss',
      },
    }),
    Fonts({
      google: {
        families: [ {
          name: 'Roboto',
          styles: 'wght@100;300;400;500;700;900',
        }],
      },
    }),
  ],
  define: { 'process.env': {} },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
    extensions: [
      '.js',
      '.json',
      '.jsx',
      '.mjs',
      '.ts',
      '.tsx',
      '.vue',
    ],
  },
  server: {
    host: "0.0.0.0",
    port: 3000,
  },
})

