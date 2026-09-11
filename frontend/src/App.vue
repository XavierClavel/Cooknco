<template>
  <v-app class="d-flex" >

    <v-navigation-drawer
      v-model="drawer"
      floating
      v-if="showSidebar"
      class="custom-drawer"
      elevation="2"
      width="250"
      variant="elevated"
      color="#4f4193"
      style="border: 3.5px solid #0d1821 !important;"
    >
      <v-list>
        <v-img src="/src/assets/logo.png" class="mx-12 my-8"></v-img>
        <v-list-item class="d-flex justify-center">
          <v-btn
            prepend-icon="mdi-pencil"
            min-height="50px"
            min-width="200px"
            class="elevation-0 mb-6"
            color="primary"
            @click="toCreateRecipe"
          >
            {{$t("new_recipe")}}
          </v-btn>
        </v-list-item>
        <v-list-item :prepend-icon="ICON_HOME" link :title="`${$t('home')}`" @click="toHome"></v-list-item>
        <v-list-item :prepend-icon="ICON_RECIPE" link :title="`${$t('recipes')}`" @click="toListRecipe('')"></v-list-item>
        <v-list-item :prepend-icon="ICON_INGREDIENT" link :title="`${$t('ingredients')}`" @click="toListIngredient"></v-list-item>
        <v-list-item :prepend-icon="ICON_COOKBOOK" link :title="`${$t('cookbooks')}`" @click="toMyCookbooks"></v-list-item>
        <admin-only>
        <v-list-item :prepend-icon="ICON_ADMIN" link :title="`${$t('admin')}`" @click="toAdmin"></v-list-item>
        </admin-only>

      </v-list>
      <template v-slot:append>
        <v-card-text class="text-center">{{ `Version ${version}` }}</v-card-text>
      </template>
    </v-navigation-drawer>

    <v-main class="d-flex flex-grow-1">
    <v-container fluid class="d-flex">
    <v-card
      color="transparent"
      style="border: 0"
      class="custom-bar flex-grow-1 mt-n2  mb-n4"
      v-if="showSidebar"
      variant="flat"
    >
      <template v-slot:prepend>
        <v-app-bar-nav-icon @click="toggleDrawer" style="border: 3px solid #0d1821 !important;"></v-app-bar-nav-icon>
      </template>
      <template v-slot:append>
        <v-menu style="border: 0">
          <template v-slot:activator="{ props }">
            <div class="relative-btn" v-bind="props" v-if="!xs">
              <v-btn
                v-bind="props"
                :icon="ICON_NOTIFICATION"
                flat
                class="mr-4 text-black"
                color="#0476a3"
                style="border: 3px solid #0d1821 !important;"
              ></v-btn>

              <!-- Lit by either half of the bell: a request to act on, or something unread -->
              <div
                v-if="hasNotifications"
                class="notification-dot"
              ></div>
            </div>
          </template>

          <v-list v-if="hasNotifications" base-color="black" bg-color="menu" style="border: 3px solid #0d1821 !important;" max-width="380">
            <!--
              Follow requests stay a list of their own rather than being folded in with the
              notifications: they are a queue the user has to act on, not news.
            -->
            <template v-if="pollingStore.data.followersPending?.length">
              <v-list-subheader class="text-left ml-n14" inset>{{$t("follow_requests")}}</v-list-subheader>
              <v-list-item
                v-for="user in pollingStore.data.followersPending"
                :key="user.username"
                :title="user.username"
                link
                @click="toViewUser(user.id)"
              >
                <template v-slot:prepend>
                  <v-avatar color="black" class="mr-2" style="border:2px solid #0d1821;">
                    <v-img
                      :src="getUserIconUrl(user.id)"
                    ></v-img>
                  </v-avatar>
                </template>
              </v-list-item>
            </template>

            <template v-if="pollingStore.data.notifications?.length">
              <v-divider v-if="pollingStore.data.followersPending?.length" class="my-1" />
              <v-list-subheader class="text-left ml-n14" inset>
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
                  <v-avatar v-if="notification.actor" color="black" class="mr-2" style="border:2px solid #0d1821;">
                    <v-img :src="getUserIconUrl(notification.actor.id, notification.actor.version)"></v-img>
                  </v-avatar>
                  <v-icon v-else class="mr-2">mdi-bullhorn-outline</v-icon>
                </template>

                <!--
                  .stop does two jobs: it keeps the click off the row, which would open the
                  notification on the way to clearing it, and it keeps the menu open so the
                  next one can be cleared without reopening the bell.
                -->
                <template v-slot:append>
                  <v-btn
                    class="notification-clear"
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

        <v-menu style="border: 0" >
          <template v-slot:activator="{ props }">
            <v-avatar size="50" variant="elevated" style="border:3px solid #0d1821 !important;">
              <v-img
                color="surface-variant"
                :src="getUserIconUrl(userId, userIconVersion)"
                cover
                v-bind="props"
                class="clickable_image"
              ></v-img>
            </v-avatar>
          </template>

          <v-list base-color="black" bg-color="menu" style="border: 3px solid #0d1821 !important;">
            <v-list-item prepend-icon="mdi-account-circle" link :title="`${$t('profile')}`" @click="toMyProfile" ></v-list-item>
            <v-list-item prepend-icon="mdi-cog" link :title="`${$t('settings')}`" @click="toSettings" ></v-list-item>
            <v-list-item prepend-icon="mdi-information-slab-circle-outline" link :title="`${$t('about')}`" @click="toHome"></v-list-item>
            <v-list-item prepend-icon="mdi-logout" link :title="`${$t('log_out')}`" @click="logout" ></v-list-item>
          </v-list>
        </v-menu>
      </template>

      <div class="d-flex flex-grow-1 justify-center align-center position-absolute"
           style ="
            left: 50%;
            top: 50%;
            transform: translate(-50%, -45%);
            width: 50%;
            position: absolute;
            z-index: 1;"
           v-if="showSidebar"
      >
        <v-card
          width="100%"
          height="48px"
          class="d-flex align-center"
        >
          <v-text-field
            class="mx-auto no-border"
            prepend-inner-icon="mdi-magnify"
            single-line
            variant="solo"
            clearable
            flat
            :label="`${$t('search_recipe')}`"
            bg-color="primary"
            @update:modelValue="toSearch"
            v-model="search"
          ></v-text-field>
        </v-card>
      </div>

    </v-card>
    </v-container>
    </v-main>

    <v-main class="ma-2 ml-8 mr-4 mt-2">
      <router-view />
    </v-main>

  </v-app>
</template>

<script lang="ts" setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router';
import {
  allowNoLoginStartsWith,
  getHealth,
  getUserIconUrl,
  logout,
  noLoginRedirect, noLoginRedirectStartsWith,
  toCreateRecipe,
  toHome,
  toListIngredient,
  toListRecipe, toMyCookbooks, toMyProfile, toSettings,
  toAdmin, toViewUser,
} from "@/scripts/common";
import {useAuthStore} from "@/stores/auth";
import { debounce } from 'lodash'
import {ICON_ADMIN, ICON_COOKBOOK, ICON_HOME, ICON_INGREDIENT, ICON_NOTIFICATION, ICON_RECIPE} from "@/scripts/icons";
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
const { xs, sm, md } = useDisplay();


// Create a ref to control the visibility of the drawer
const drawer = ref(!xs.value)

// Function to toggle the drawer
const toggleDrawer = () => {
  drawer.value = !drawer.value
}

const showSidebar = computed(() =>
  route.name &&
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

const removeAfterEach = router.afterEach((to, from) => {
  if (to.name != "/search") {
    search.value = null
    return
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
.clickable_image {
  cursor: pointer
}
.custom-drawer {
  margin: 16px;
  max-height: calc(100% - 32px);
  overflow: hidden;
}

.custom-bar {
  //margin: 8px; /* Adjust margin to prevent overflow */
  //margin-left: 0px;
  //margin-right: 16px;
  //max-width: calc(100% - 296px); /* Reduce width to account for margin */
  height: 65px; /* Optional: Limit width */
  //overflow: hidden; /* Ensures child elements respect border-radius */
}

.relative-btn {
  position: relative;
  display: inline-block;
}

/* An unread row, marked with the same accent as the dot on the bell */
.notification-unread {
  border-left: 3px solid #ff6f59;
}

/*
  The app frames every button in 3px of black (global.scss). That is the house style for a
  button you press on its own, and far too much for an x sitting inside a list row, which
  should read as part of the row rather than as a control stacked on top of it. Both the
  rule being undone and this one are !important, so the class is named alongside .v-btn to
  win on specificity rather than on load order.
*/
.notification-clear.v-btn {
  border: 0 !important;
  opacity: 0.6;
}

.notification-clear.v-btn:hover,
.notification-clear.v-btn:focus-visible {
  opacity: 1;
}

.notification-dot {
  position: absolute;
  bottom: 36px;
  right: 15px;
  width: 10px;
  height: 10px;
  background-color: #ff6f59;
  border-radius: 50%;
  z-index: 1;
  box-shadow: 0 0 0 2px white; /* Optional: border contrast */
}
</style>
