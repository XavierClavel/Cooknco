import {adminOnly, isPublicPath} from "@/scripts/common";
import {getCookie, setCookie} from "@/scripts/cookies";
import {useAuthStore} from "@/stores/auth";

/**
 * Replaces the router.beforeEach guard that lived in src/router/index.ts.
 *
 * Two deliberate changes from that version:
 *  - it matches `to.path`, not `to.name` (Nuxt names routes differently);
 *  - the `localStorage.authToken` escape hatch is gone. Only the Capacitor
 *    shell ever wrote that token, and it has been removed, so on the web it
 *    was always null — but it did let a stale value bypass this redirect.
 */
export default defineNuxtRouteMiddleware(async (to, from) => {
  // Server renders the anonymous view; there is no session to check there.
  if (import.meta.server) return

  if (isPublicPath(to.path)) return

  const authStore = useAuthStore()
  await authStore.checkAuth()

  if (!authStore.isAuthenticated) {
    setCookie("redirectedFrom", to.fullPath)
    return navigateTo('/login')
  }

  if (adminOnly.includes(to.path) && !authStore.isAdmin) {
    return navigateTo('/home')
  }

  // Keep the last recipe-list query when navigating there without one.
  if (from.path !== '/recipe/list' && to.path === '/recipe/list' && !Object.keys(to.query).length) {
    const lastQuery = getCookie<Record<string, string>>('recipeQuery')
    if (lastQuery && Object.keys(lastQuery).length) {
      return navigateTo({ path: '/recipe/list', query: lastQuery })
    }
  }
})
