import {computed, ref} from 'vue'
import * as api from './api'

/**
 * Backoffice session state.
 *
 * The admin API is gated on `admin-session`, so a non-admin account signing in
 * here is treated as not signed in at all rather than shown a broken console.
 */
const user = ref<any>(null)
const checked = ref(false)

export const currentUser = computed(() => user.value)
export const isAdmin = computed(() => user.value?.role === 'ADMIN')
export const sessionChecked = computed(() => checked.value)

export async function refreshSession() {
  try {
    user.value = (await api.whoami()).data
  } catch {
    user.value = null
  } finally {
    checked.value = true
  }
}

export async function signIn(mail: string, password: string) {
  await api.login(mail, password)
  await refreshSession()
  return isAdmin.value
}

export async function signOut() {
  try { await api.logout() } finally { user.value = null }
}
