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
        <v-img src="/logo.png" class="mx-12 my-8"></v-img>
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
        <v-list-item :prepend-icon="ICON_ADMIN" link :title="`${$t('admin')}`" @click="toUsers"></v-list-item>
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

              <div
                v-if="pollingStore.data.followersPending?.length"
                class="notification-dot"
              ></div>
            </div>
          </template>

          <v-list v-if="pollingStore.data.followersPending?.length" base-color="black" bg-color="menu" style="border: 3px solid #0d1821 !important;">
            <v-list-subheader class="text-left ml-n14" inset>{{$t("follow_requests")}}</v-list-subheader>
            <v-list-item
              v-for="user in pollingStore.data.followersPending"
              :key="user.username"
              :title="user.username"
            >
              <template v-slot:prepend>
                <v-avatar color="black" class="mr-2" style="border:2px solid #0d1821;">
                  <v-img
                    :src="getUserIconUrl(user.id)"
                  ></v-img>
                </v-avatar>
              </template>
            </v-list-item>
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
      <slot />
    </v-main>

    <AppFooter />

  </v-app>
</template>

<script lang="ts" setup>
import {
  getHealth,
  getUserIconUrl,
  isChromelessPath,
  logout,
  toCreateRecipe,
  toHome,
  toListIngredient,
  toListRecipe, toMyCookbooks, toMyProfile, toSettings,
  toUsers,
} from "@/scripts/common";
import {useAuthStore} from "@/stores/auth";
import { debounce } from 'lodash'
import {ICON_ADMIN, ICON_COOKBOOK, ICON_HOME, ICON_INGREDIENT, ICON_NOTIFICATION, ICON_RECIPE} from "@/scripts/icons";
import {useDisplay} from "vuetify";
import {usePollingStore} from "@/stores/pollingStore";

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

// Matched on path, not route name: Nuxt names routes differently from
// unplugin-vue-router, whose names happened to be the paths.
const showSidebar = computed(() =>
  !isChromelessPath(route.path) && authStore.isAuthenticated
);

const toSearch = debounce((query) => {
  if (!query) return; // Avoid empty redirects
  router.push({ path: '/search', query: { search: query, filter: route.query.filter || "everything" } })
}, 500) // Buffer input for 500ms

const pollingStore = usePollingStore()

// All three are authenticated calls against the browser's session, so they run
// after mount rather than during setup, where they would also fire on every
// server render.
onMounted(async () => {
  await authStore.checkAuth()
  getHealth().then((response) => {
    version.value = response.data.version
  })
  pollingStore.startPolling()
})

onBeforeUnmount(() => {
  pollingStore.stopPolling()
})

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
  height: 65px; /* Optional: Limit width */
}

.relative-btn {
  position: relative;
  display: inline-block;
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
