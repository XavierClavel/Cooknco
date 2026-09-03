import axios from 'axios'

/**
 * Backoffice API client.
 *
 * Separate from the consumer app's client on purpose: the admin endpoints are
 * gated on `admin-session`, which is cookie-only, so this never attaches the
 * bearer token the public app falls back to.
 */
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
  withCredentials: true,
  headers: {'Content-Type': 'application/json'},
})

/** Server errors come back as a bare cause key, e.g. "not_allowed_to_moderate_admin". */
export function errorKey(error: any): string {
  const body = error?.response?.data
  return typeof body === 'string' && body ? body : ''
}

function qs(filters: Record<string, unknown>, page?: number, size?: number) {
  const q = new URLSearchParams()
  Object.entries(filters).forEach(([k, v]) => {
    if (v === null || v === undefined || v === '') return
    q.append(k, String(v))
  })
  if (page !== undefined) q.append('page', String(page))
  if (size !== undefined) q.append('size', String(size))
  return q.toString()
}

// ------------------------------------------------------------------ session
export const whoami = () => api.get('/auth/me')
export const login = (mail: string, password: string) =>
  api.post('/auth/login', {}, {auth: {username: mail, password}})
export const logout = () => api.post('/auth/logout')

// ----------------------------------------------------------------- overview
export const getOverview = () => api.get('/admin/overview')
export const getTrends = (granularity: string, buckets: number) =>
  api.get(`/admin/trends?granularity=${granularity}&buckets=${buckets}`)

// -------------------------------------------------------------------- users
export const listUsers = (f: Record<string, unknown>, page: number, size: number) =>
  api.get(`/admin/users?${qs(f, page, size)}`)
export const getUser = (id: number) => api.get(`/admin/users/${id}`)
export const setRole = (id: number, role: string) => api.put(`/admin/users/${id}/role/${role}`)
export const suspendUser = (id: number, days: number, reason: string) =>
  api.post(`/admin/users/${id}/suspend`, {days, reason})
export const banUser = (id: number, reason: string) => api.post(`/admin/users/${id}/ban`, {reason})
export const reinstateUser = (id: number) => api.post(`/admin/users/${id}/reinstate`)
export const verifyUser = (id: number) => api.post(`/admin/users/${id}/verify`)
export const deleteUser = (id: number) => api.delete(`/admin/users/${id}`)

// ------------------------------------------------------------------ recipes
export const listRecipes = (f: Record<string, unknown>, page: number, size: number) =>
  api.get(`/admin/recipes?${qs(f, page, size)}`)
export const getRecipeDetail = (id: number, locale: string) =>
  api.get(`/admin/recipes/${id}/detail?locale=${locale}`)
export const hideRecipe = (id: number, reason: string) => api.post(`/admin/recipes/${id}/hide`, {reason})
export const unhideRecipe = (id: number) => api.post(`/admin/recipes/${id}/unhide`)
export const deleteRecipe = (id: number) => api.delete(`/admin/recipes/${id}`)

// -------------------------------------------------------------- ingredients
export const listIngredients = (f: Record<string, unknown>, page: number, size: number) =>
  api.get(`/admin/ingredients?${qs(f, page, size)}`)
export const deleteIngredient = (id: number) => api.delete(`/ingredient/${id}`)

// --------------------------------------------------------------- moderation
export const listReports = (f: Record<string, unknown>, page: number, size: number) =>
  api.get(`/admin/reports?${qs(f, page, size)}`)
export const resolveReport = (id: number, action: string, note: string, suspensionDays: number) =>
  api.post(`/admin/reports/${id}/resolve`, {action, note, suspensionDays})

// ------------------------------------------------------------------ storage
export const getStorageOverview = () => api.get('/admin/storage')
export const listImages = (f: Record<string, unknown>, page: number, size: number) =>
  api.get(`/admin/storage/images?${qs(f, page, size)}`)
export const deleteImage = (bucket: string, file: string) =>
  api.delete(`/admin/storage/images?${qs({bucket, file})}`)
export const cleanupStorage = (buckets: string[], statuses: string[], dryRun: boolean) =>
  api.post('/admin/storage/cleanup', {buckets, statuses, dryRun})

/** Images are served straight off the volume, not through the admin API. */
export const imageUrl = (dir: string, filename: string) =>
  `${import.meta.env.VITE_IMG_URL}/${dir}/${filename}`

// --------------------------------------------------------------------- logs
export const getLogs = (f: Record<string, unknown>) => api.get(`/admin/logs?${qs(f)}`)
export const clearLogs = () => api.delete('/admin/logs')

/** EventSource opens its own connection, so the stream URL is built by hand. */
export const logStreamUrl = (f: Record<string, unknown>) =>
  `${import.meta.env.VITE_API_URL}/admin/logs/stream?${qs(f)}`
