<template>
  <v-app>
    <!-- The signed-in app: a floating sidebar beside the page, a bar of controls above it -->
    <div v-if="showSidebar" class="shell">
      <div v-if="drawer && compact" class="shell__scrim" @click="drawer = false"></div>

      <aside v-show="drawer" class="sidebar sticker">
        <router-link to="/home" class="sidebar__logo" :aria-label="$t('home')">
          <img src="@/assets/logo.png" alt="Cook'n'Co" />
        </router-link>

        <button class="sk-btn sk-btn--mustard sk-btn--lg sk-btn--rect sk-btn--block" @click="navigate(toCreateRecipe)">
          <v-icon>mdi-plus</v-icon>
          {{ $t("new_recipe") }}
        </button>

        <nav class="sidebar__nav">
          <a
            v-for="item in navItems"
            :key="item.key"
            href="#"
            class="sidebar__item"
            :class="{'is-active': item.active}"
            @click.prevent="navigate(item.go)"
          >
            <v-icon :icon="item.icon" size="20" />
            {{ $t(item.key) }}
          </a>
          <admin-only>
            <a href="/admin" class="sidebar__item">
              <v-icon icon="mdi-shield-outline" size="20" />
              {{ $t('admin') }}
            </a>
          </admin-only>
        </nav>

        <div class="sidebar__version">{{ version ? `Version ${version}` : '' }}</div>
      </aside>

      <div class="shell__main">
        <header class="topbar">
          <button class="sk-icon-btn" :aria-label="$t('menu')" @click="toggleDrawer">
            <v-icon>mdi-menu</v-icon>
          </button>

          <div class="topbar__search sticker sticker--sm">
            <v-icon size="20">mdi-magnify</v-icon>
            <input
              v-model="search"
              type="search"
              :placeholder="$t('search_recipe')"
              :aria-label="$t('search_recipe')"
              @input="toSearch(search)"
            />
          </div>

          <div class="topbar__end">
            <v-menu location="bottom end" offset="8">
              <template v-slot:activator="{ props }">
                <button v-bind="props" class="sk-icon-btn topbar__bell" :aria-label="$t('notifications')">
                  <v-icon>mdi-bell-outline</v-icon>
                  <!-- Lit by either half of the bell: a request to act on, or something unread -->
                  <span v-if="hasNotifications" class="notification-dot"></span>
                </button>
              </template>

              <v-list v-if="hasNotifications" max-width="380">
                <!--
                  Follow requests stay a list of their own rather than being folded in with the
                  notifications: they are a queue the user has to act on, not news.
                -->
                <template v-if="pollingStore.data.followersPending?.length">
                  <v-list-subheader>{{$t("follow_requests")}}</v-list-subheader>
                  <v-list-item
                    v-for="user in pollingStore.data.followersPending"
                    :key="user.username"
                    :title="user.username"
                    link
                    @click="toViewUser(user.id)"
                  >
                    <template v-slot:prepend>
                      <span class="sk-avatar mr-3"><img :src="getUserIconUrl(user.id)" alt="" /></span>
                    </template>
                  </v-list-item>
                </template>

                <template v-if="pollingStore.data.notifications?.length">
                  <v-divider v-if="pollingStore.data.followersPending?.length" class="my-1" />
                  <v-list-subheader>
                    {{$t("notifications")}}
                    <span v-if="pollingStore.data.unreadCount" class="ml-1">({{ pollingStore.data.unreadCount }})</span>
                  </v-list-subheader>
                  <v-list-item
                    v-for="notification in pollingStore.data.notifications"
                    :key="notification.id"
                    :title="notification.title"
                    :subtitle="notification.body"
                    link
                    :class="{'notification-unread': !notification.read}"
                    @click="openNotification(notification)"
                  >
                    <template v-slot:prepend>
                      <span v-if="notification.actor" class="sk-avatar mr-3">
                        <img :src="getUserIconUrl(notification.actor.id, notification.actor.version)" alt="" />
                      </span>
                      <v-icon v-else class="mr-3">mdi-bullhorn-outline</v-icon>
                    </template>

                    <!--
                      .stop does two jobs: it keeps the click off the row, which would open the
                      notification on the way to clearing it, and it keeps the menu open so the
                      next one can be cleared without reopening the bell.
                    -->
                    <template v-slot:append>
                      <v-btn
                        class="sk-plain notification-clear"
                        icon="mdi-close"
                        variant="text"
                        size="x-small"
                        density="comfortable"
                        :title="$t('clear_notification')"
                        :aria-label="$t('clear_notification')"
                        @click.stop="clearNotification(notification)"
                      ></v-btn>
                    </template>
                  </v-list-item>

                  <v-divider class="my-1" />
                  <v-list-item
                    v-if="pollingStore.data.unreadCount"
                    :title="$t('mark_all_read')"
                    prepend-icon="mdi-check-all"
                    link
                    @click="markAllNotificationsRead"
                  ></v-list-item>
                  <v-list-item
                    :title="$t('clear_all_notifications')"
                    prepend-icon="mdi-notification-clear-all"
                    link
                    @click="clearAllNotifications"
                  ></v-list-item>
                </template>
              </v-list>
            </v-menu>

            <v-menu location="bottom end" offset="8">
              <template v-slot:activator="{ props }">
                <button v-bind="props" class="sk-avatar topbar__avatar" :aria-label="$t('profile')">
                  <img :src="getUserIconUrl(userId, userIconVersion)" alt="" />
                </button>
              </template>

              <v-list>
                <v-list-item prepend-icon="mdi-account-circle-outline" link :title="`${$t('profile')}`" @click="toMyProfile"></v-list-item>
                <v-list-item prepend-icon="mdi-cog-outline" link :title="`${$t('settings')}`" @click="toSettings"></v-list-item>
                <v-list-item prepend-icon="mdi-information-outline" link :title="`${$t('about')}`" @click="toHome"></v-list-item>
                <v-list-item prepend-icon="mdi-logout" link :title="`${$t('log_out')}`" @click="logout"></v-list-item>
              </v-list>
            </v-menu>
          </div>
        </header>

        <main class="shell__content">
          <router-view />
        </main>
      </div>
    </div>

    <!-- Everything outside the app: the landing page, signing in, the legal pages -->
    <div v-else class="public">
      <router-link v-if="route.name !== '/'" to="/" class="public__logo" aria-label="Cook'n'Co">
        <img src="@/assets/logo.png" alt="Cook'n'Co" />
      </router-link>
      <main class="public__content">
        <router-view />
      </main>
    </div>
  </v-app>
</template>

<script lang="ts" setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router';
import {
  getHealth,
  getUserIconUrl,
  logout,
  toMealPlan,
  noLoginRedirect, noLoginRedirectStartsWith,
  toCreateRecipe,
  toHome,
  toListIngredient,
  toListRecipe, toMyCookbooks, toMyProfile, toSettings,
  toViewUser,
} from "@/scripts/common";
import {useAuthStore} from "@/stores/auth";
import { debounce } from 'lodash'
import {overrideLocaleFromCookie} from "@/scripts/localization";
import {useDisplay} from "vuetify";
import {usePollingStore} from "@/stores/pollingStore";
import {
  clearAllNotifications as clearAllOnServer,
  clearNotification as clearOnServer,
  markAllNotificationsRead as markAllRead,
  markNotificationRead,
} from "@/scripts/notifications";

const route = useRoute();
const router = useRouter()
const authStore = useAuthStore()

const userId = computed(() => authStore.id)
const userIconVersion = computed(() => authStore.iconVersion)
const version = ref(null)
const search = ref(route.query.search || '')
const { smAndDown } = useDisplay();

/** Below a tablet's width the sidebar floats over the page, so it starts closed and closes after a pick */
const compact = computed(() => smAndDown.value)

const drawer = ref(!compact.value)

const toggleDrawer = () => {
  drawer.value = !drawer.value
}

const navigate = (go: () => unknown) => {
  go()
  if (compact.value) drawer.value = false
}


/** The sidebar's entries, each lit while the page it leads to (or one beneath it) is open */
const navItems = computed(() => {
  const name = String(route.name ?? '')
  return [
    {key: 'home', icon: 'mdi-home-outline', go: toHome, active: name === '/home'},
    {key: 'recipes', icon: 'mdi-silverware-fork-knife', go: () => toListRecipe(''), active: name.startsWith('/recipe/')},
    {key: 'ingredients', icon: 'mdi-food-apple-outline', go: toListIngredient, active: name.startsWith('/ingredient')},
    {key: 'cookbooks', icon: 'mdi-book-open-outline', go: toMyCookbooks, active: name.startsWith('/cookbook')},
    {key: 'meal_plan', icon: 'mdi-calendar-blank-outline', go: toMealPlan, active: name.startsWith('/meal-plan')},
  ]
})

/**
 * Whether this route wears the app's chrome — the sidebar, the search bar, the bell and the
 * avatar menu.
 *
 * `/` is named here rather than being left to the session check below, because the session
 * check is the wrong question to ask about it. The landing page is the face of the site to
 * somebody who has no account, and it carries nothing but the page whoever is reading it —
 * exactly as `/login` does, and for the same reason: neither is a page of the app.
 *
 * It cannot be left to the router either. That guard sends a member on to their feed only if
 * `authToken` is in localStorage, and nothing in this app has ever written that key — auth is
 * the session cookie `checkAuth()` reads. So a signed-in visitor stays on `/`, and without
 * this line they read the public page from inside a signed-in shell.
 */
const showSidebar = computed(() =>
  route.name &&
  route.name !== '/' &&
  !noLoginRedirect.includes(route.name) &&
  !noLoginRedirectStartsWith.some((it) => route.name.startsWith(it)) &&
  authStore.isAuthenticated
);

const toSearch = debounce((query) => {
  if (!query) return; // Avoid empty redirects
  router.push({ name: '/search', query: { search: query, filter: route.query.filter || "everything" } })
}, 500) // Buffer input for 500ms

overrideLocaleFromCookie()

if (!version.value) {
  getHealth().then((response) => {
    version.value = response.data.version
  })
  authStore.checkAuth()
}

const pollingStore = usePollingStore()
onMounted(() => {
  pollingStore.startPolling()
})

/**
 * Whether the bell has anything behind it.
 *
 * Either half counts: a follow request is something to act on, and an unread notification
 * is something to read. The dot and the menu are driven off the same answer so the bell
 * never lights up on an empty menu.
 */
const hasNotifications = computed(() =>
  !!pollingStore.data.followersPending?.length || !!pollingStore.data.notifications?.length)

/**
 * Opens what a notification points at, and marks it read on the way.
 *
 * The link is an app-relative path the backend built, so it is pushed as it stands — the
 * same value the mobile app maps onto its own routes.
 */
async function openNotification(notification) {
  if (!notification.read) {
    // Marked locally as well as server-side: the badge should drop on the click, not on
    // the next poll five minutes later. A failed call is not worth blocking the navigation.
    await markNotificationRead(notification.id).catch(() => {})
    notification.read = true
    pollingStore.data.unreadCount = Math.max(0, (pollingStore.data.unreadCount ?? 1) - 1)
  }
  if (notification.link) await router.push(notification.link)
}

async function markAllNotificationsRead() {
  await markAllRead().catch(() => {})
  pollingStore.data.notifications?.forEach((it) => { it.read = true })
  pollingStore.data.unreadCount = 0
}

/**
 * Takes one notification out of the list, for good.
 *
 * Removed here as well as server-side so the menu answers the click rather than the next
 * poll five minutes later — but only once the backend has agreed, because a clear that
 * silently failed would otherwise reappear on that poll. A 404 is agreement: it means the
 * notification was already gone.
 */
async function clearNotification(notification) {
  const cleared = await clearOnServer(notification.id)
    .then(() => true)
    .catch((error) => error.response?.status === 404)
  if (!cleared) return

  pollingStore.data.notifications =
    (pollingStore.data.notifications ?? []).filter((it) => it.id !== notification.id)
  if (!notification.read) {
    pollingStore.data.unreadCount = Math.max(0, (pollingStore.data.unreadCount ?? 1) - 1)
  }
}

/**
 * Clears everything, including whatever the menu had no room to show.
 *
 * Follow requests are left where they are: they are a queue the user has to answer, and
 * clearing one from here would be answering it on their behalf.
 */
async function clearAllNotifications() {
  const cleared = await clearAllOnServer().then(() => true).catch(() => false)
  if (!cleared) return

  pollingStore.data.notifications = []
  pollingStore.data.unreadCount = 0
}

const removeAfterEach = router.afterEach((to) => {
  if (to.name != "/search") {
    search.value = null
  }
})

onUnmounted(() => {
  removeAfterEach()
})

watch(
  () => route.query.search,
  (newVal) => {
    search.value = newVal || ''
  },
  { immediate: true }
)

</script>

<style scoped>
/* ---------- Signed-in shell ---------- */

.shell {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  min-height: 100vh;
  padding: 16px 20px 16px 16px;
}

.shell__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.shell__content {
  display: flex;
  flex-direction: column;
  gap: 28px;
  min-width: 0;
}

.shell__scrim {
  position: fixed;
  inset: 0;
  z-index: 1005;
  background: rgba(13, 24, 33, .35);
}

.sidebar {
  position: sticky;
  top: 16px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 18px;
  width: 196px;
  padding: 18px 12px 16px;
  background: var(--plum);
  border-radius: 20px;
}

.sidebar__logo {
  display: block;
  margin: 0 auto;
  width: 112px;
}

.sidebar__logo img {
  display: block;
  width: 100%;
}

.sidebar__nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sidebar__item {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 40px;
  padding: 0 14px;
  border-radius: 999px;
  color: var(--white);
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
  transition: background .1s ease;
}

.sidebar__item:hover {
  background: rgba(255, 255, 255, .12);
}

.sidebar__item.is-active {
  background: var(--coral);
}

.sidebar__item:focus-visible {
  outline: 3px solid var(--mustard);
  outline-offset: 1px;
}

.sidebar__version {
  text-align: center;
  color: var(--white);
  font-size: 12px;
  opacity: .9;
}

/* Below a tablet's width the sidebar floats over the page instead of beside it */
@media (max-width: 959px) {
  .shell { padding: 12px; }

  .sidebar {
    position: fixed;
    top: 12px;
    left: 12px;
    z-index: 1006;
    max-height: calc(100vh - 24px);
    overflow-y: auto;
  }
}

.topbar {
  display: flex;
  align-items: center;
  gap: 16px;
}

.topbar__search {
  flex: 1;
  max-width: 560px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 44px;
  padding: 0 16px;
}

.topbar__search input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  font-family: var(--font);
  font-size: 15px;
  color: var(--ink);
}

.topbar__search input::placeholder {
  color: #6b736e;
}

.topbar__search:focus-within {
  box-shadow: 0 0 0 3px var(--mustard), var(--shadow-sm);
}

.topbar__end {
  display: flex;
  align-items: center;
  gap: 12px;
}

.topbar__bell {
  position: relative;
}

.topbar__avatar {
  width: 44px;
  height: 44px;
  padding: 0;
  cursor: pointer;
}

.topbar__avatar:focus-visible {
  outline: 3px solid var(--mustard);
  outline-offset: 2px;
}

.notification-dot {
  position: absolute;
  top: -5px;
  right: -5px;
  width: 12px;
  height: 12px;
  border: 2px solid var(--ink);
  border-radius: 50%;
  background: var(--coral);
}

/* An unread row, marked with the same accent as the dot on the bell */
.notification-unread {
  border-left: 3px solid var(--coral);
}

.notification-clear {
  opacity: 0.6;
}

.notification-clear:hover,
.notification-clear:focus-visible {
  opacity: 1;
}

/* ---------- Public shell ---------- */

.public {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 28px;
  min-height: 100vh;
  padding: 40px 16px 24px;
}

.public__logo {
  display: block;
  width: min(260px, 60vw);
}

.public__logo img {
  display: block;
  width: 100%;
}

.public__content {
  flex: 1;
  width: 100%;
  max-width: 1040px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 28px;
}
</style>
