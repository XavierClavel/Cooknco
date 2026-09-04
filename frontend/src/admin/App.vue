<template>
  <div v-if="!sessionChecked" class="boot">
    <div class="progress" style="width:180px;border-radius:2px"><i></i></div>
  </div>

  <sign-in v-else-if="!isAdmin" @signed-in="onSignedIn" />

  <div v-else class="shell">
    <nav class="nav">
      <div class="brand">
        <span class="mark"><ui-icon name="shield" :size="15" /></span>
        <span class="brand-text">
          <b>Cook&amp;Co</b>
          <small>{{ $t('admin_backoffice') }}</small>
        </span>
      </div>

      <ul>
        <li v-for="item in items" :key="item.to">
          <router-link :to="item.to" :class="{active: isActive(item.to)}">
            <ui-icon :name="item.icon" />
            <span>{{ $t(item.label) }}</span>
            <em v-if="item.badge" class="count tnum">{{ item.badge }}</em>
          </router-link>
        </li>
      </ul>

      <div class="nav-foot">
        <a :href="publicSiteUrl">
          <ui-icon name="external" :size="14" />
          <span>{{ $t('admin_open_public_site') }}</span>
        </a>
      </div>
    </nav>

    <div class="main">
      <header class="topbar">
        <h1>{{ $t(pageTitle) }}</h1>
        <span class="spacer"></span>
        <span class="who small">
          <span class="muted">{{ $t('admin_signed_in_as') }}</span>
          <b>{{ currentUser?.username }}</b>
        </span>
        <button class="btn sm ghost" @click="doSignOut">
          <ui-icon name="logout" :size="14" />
          {{ $t('log_out') }}
        </button>
      </header>

      <main class="content">
        <router-view v-slot="{Component}">
          <component :is="Component" @pending-reports="pendingReports = $event" />
        </router-view>
      </main>
    </div>
  </div>

  <ui-toasts />
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {currentUser, isAdmin, refreshSession, sessionChecked, signOut} from './lib/session'
import {listReports} from './lib/api'
import UiIcon from './components/UiIcon.vue'
import UiToasts from './components/UiToasts.vue'
import SignIn from './views/SignIn.vue'

const route = useRoute()
const router = useRouter()
const pendingReports = ref<number>(0)

const items = computed(() => [
  {to: '/', label: 'admin_overview', icon: 'gauge' as const},
  {to: '/users', label: 'users', icon: 'users' as const},
  {to: '/recipes', label: 'recipes', icon: 'book' as const},
  {to: '/ingredients', label: 'ingredients', icon: 'leaf' as const},
  {to: '/moderation', label: 'admin_moderation', icon: 'gavel' as const, badge: pendingReports.value || null},
  {to: '/storage', label: 'admin_storage', icon: 'disk' as const},
  {to: '/mails', label: 'admin_mails', icon: 'mail' as const},
  {to: '/logs', label: 'admin_logs', icon: 'terminal' as const},
])

const isActive = (to: string) => (to === '/' ? route.path === '/' : route.path.startsWith(to))
const pageTitle = computed(() => items.value.find(i => isActive(i.to))?.label ?? 'admin_backoffice')
const publicSiteUrl = computed(() => import.meta.env.BASE_URL.replace(/\/admin\/?$/, '/') || '/')

/** Kept fresh app-wide so the queue badge is right on every screen, not just the queue. */
async function refreshPending() {
  if (!isAdmin.value) return
  try {
    pendingReports.value = (await listReports({status: 'PENDING'}, 0, 1)).data.count
  } catch { /* a failed badge refresh must not break navigation */ }
}

const onSignedIn = async () => {
  await refreshSession()
  await refreshPending()
}

const doSignOut = async () => {
  await signOut()
  router.replace('/')
}

onMounted(async () => {
  if (!sessionChecked.value) await refreshSession()
  await refreshPending()
})
</script>

<style scoped>
.boot { height: 100%; display: flex; align-items: center; justify-content: center; }

.shell { display: flex; height: 100%; }

/* ------------------------------------------------------------------ sidebar */
.nav {
  width: var(--nav-w);
  flex: none;
  background: var(--c-nav-bg);
  border-right: 1px solid var(--c-nav-border);
  display: flex;
  flex-direction: column;
  padding: 12px 10px;
  gap: 14px;
}
.brand { display: flex; align-items: center; gap: 9px; padding: 5px 6px 0; }
.mark {
  width: 26px; height: 26px;
  display: grid; place-items: center;
  border-radius: var(--radius);
  background: var(--c-accent);
  color: #fff;
  flex: none;
}
.brand-text { display: flex; flex-direction: column; line-height: 1.2; }
.brand-text b { color: #fff; font-size: 13.5px; }
.brand-text small { color: var(--c-nav-text); font-size: 11px; }

.nav ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 1px; }
.nav a {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 7px 9px;
  border-radius: var(--radius);
  color: var(--c-nav-text);
  font-size: 13px;
  font-weight: 500;
  text-decoration: none;
}
.nav a:hover { background: var(--c-nav-bg-hover); color: #fff; text-decoration: none; }
.nav a.active { background: var(--c-nav-bg-active); color: var(--c-nav-text-active); }
.nav a span { flex: 1; }
.count {
  font-style: normal;
  font-size: 11px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--c-accent);
  color: #fff;
}
.nav-foot { margin-top: auto; border-top: 1px solid var(--c-nav-border); padding-top: 8px; }
.nav-foot a { font-size: 12px; font-weight: 400; }

/* ------------------------------------------------------------------- main */
.main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.topbar {
  height: var(--topbar-h);
  flex: none;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
  background: var(--c-surface);
  border-bottom: 1px solid var(--c-border);
}
.topbar h1 { font-size: 15px; }
.who { display: flex; align-items: center; gap: 5px; }

.content { flex: 1; min-height: 0; overflow: auto; padding: 20px; }
</style>
