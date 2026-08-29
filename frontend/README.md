# Cook&Co frontend

A [Nuxt 4](https://nuxt.com) app (Vue 3 + [Vuetify 3](https://vuetifyjs.com)) that both
server-renders the shareable pages and proxies the backend.

## Rendering model

SSR is on globally, but only the pages people actually share are server-rendered
— see `routeRules` in `nuxt.config.ts`:

| Route | Rendering | Why |
| --- | --- | --- |
| `/recipe/:id`, `/user/:id`, `/cookbook/:id` | server-rendered | Crawlers do not run JavaScript, so link previews (`useShareMeta`) and search engines need real HTML |
| everything else | client-only (`ssr: false`) | Auth-gated or personalised; nothing a crawler should see |

Server renders run **anonymously** — no session cookie is forwarded. A crawler has no
session anyway, and it guarantees no per-visitor data can land in HTML. Anything
personalised (like state, private notes, cookbook membership, owner controls) is fetched
client-side in `onMounted`. A backend 403 renders a `noindex` stub rather than a 404, so
the owner of a private recipe still sees it once the client refetches with credentials.

## Link previews

`src/composables/useShareMeta.ts` emits the Open Graph and Twitter card tags. It must be
called at the top level of a page's `<script setup>` — never inside `<ClientOnly>`, and
never for a route that is `ssr: false`, since a crawler only reads the server's HTML.
Routes that are not server-rendered fall back to the site-wide card declared in
`app.head`.

Fallback cards for entities with no image live in `public/og/` (1200×630).

## Backend

`/api/**` and `/image/**` are proxied to the Ktor backend by `server/routes/`, replacing
the nginx config this app used to ship with. The target is `NUXT_BACKEND_URL`, read at
runtime, so one image serves every environment.

⚠️ nginx also did `/api` rate limiting (10 r/s) and capped request bodies at 20 MB.
Neither has an equivalent here — they belong at the ingress, which is the only layer that
sees the real client address.

## Environment

All runtime, none baked into the build:

| Variable | Default | Purpose |
| --- | --- | --- |
| `NUXT_BACKEND_URL` | `http://cooknco-backend:8080` | Proxy target and server-side fetch base |
| `NUXT_PUBLIC_SITE_URL` | `https://cooknco.eu` | Absolute base for `og:url` / `og:image` |
| `NUXT_PUBLIC_API_URL` | `/api/v1` | Browser-side API base (same-origin) |
| `NUXT_PUBLIC_IMG_URL` | `/image` | Browser-side image base |

## Development

```bash
npm install
npm run dev        # http://localhost:3000, proxying to NUXT_BACKEND_URL
npm run build      # -> .output
npm run preview
npm run typecheck
npm run lint
```

Run the backend alongside it (`docker compose up -d backend database redis` from the repo
root) and point `NUXT_BACKEND_URL` at `http://localhost:8080`.

To check a link preview the way a crawler sees it — without JavaScript:

```bash
curl -s -A "Twitterbot/1.0" http://localhost:3000/recipe/1 | grep -E 'og:|twitter:'
```

`npm ci` and `npm install` rely on `.npmrc`'s `legacy-peer-deps=true`; npm's strict
resolver crashes building this dependency tree.
