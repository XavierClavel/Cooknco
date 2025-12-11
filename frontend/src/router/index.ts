/**
 * router/index.ts
 *
 * Automatic routes for `./src/pages/*.vue`
 */

// Composables
import { createRouter, createWebHistory } from 'vue-router/auto'
import { setupLayouts } from 'virtual:generated-layouts'
import { routes } from 'vue-router/auto-routes'
import { useAuthStore } from '@/stores/auth';
import {
  adminOnly,
  allowNoLoginStartsWith,
  noLoginRedirect,
  noLoginRedirectStartsWith,
  toListRecipe
} from "@/scripts/common";
import {getCookie, setCookie} from "@/scripts/cookies";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: setupLayouts(routes),
})



router.beforeEach(async (to, from) => {
  if (!to.name) {
    return {name: '/home'}
  }
  if (noLoginRedirect.includes(to.name) ||
    noLoginRedirectStartsWith.some((it) => to.name.startsWith(it)) ||
    allowNoLoginStartsWith.some((it) => to.name.startsWith(it))
  ) {
    return
  }
  const token = localStorage.getItem("authToken")
  console.log(to)
  const authStore = useAuthStore();
  await authStore.checkAuth()
  console.log("auth status : ",authStore.isAuthenticated)
  if (!authStore.isAuthenticated && !token) {
    // redirect the user to the login page
    setCookie("redirectedFrom", to.fullPath)
    return { name: `/login` }
  }

  //admin only
  if (adminOnly.includes(to.name) && !authStore.isAdmin) {
    return {name: '/home'}
  }
  //Keep last query when listing recipes without query
  if (from.name != '/recipe/list' && to.name == '/recipe/list' && !Object.keys(to.query)?.length) {
    const lastQuery = getCookie('recipeQuery')
    // Only redirect if there's a stored query and it's not already applied
    if (lastQuery && Object.keys(lastQuery).length > 0) {
      return {
        name: '/recipe/list',
        query: lastQuery
      }
    }
  }
})

// Workaround for https://github.com/vitejs/vite/issues/11804
router.onError((err, to) => {
  if (err?.message?.includes?.('Failed to fetch dynamically imported module')) {
    if (!localStorage.getItem('vuetify:dynamic-reload')) {
      console.log('Reloading page to fix dynamic import error')
      localStorage.setItem('vuetify:dynamic-reload', 'true')
      location.assign(to.fullPath)
    } else {
      console.error('Dynamic import error, reloading page did not fix it', err)
    }
  } else {
    console.error(err)
  }
})

router.isReady().then(() => {
  localStorage.removeItem('vuetify:dynamic-reload')
})

export default router
