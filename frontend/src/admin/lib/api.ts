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

/**
 * The catalogue row is too thin to edit, so the form loads the full ingredient. Reads are
 * public; every write below is gated on admin-session, same as the rest of this client.
 */
export const getIngredient = (id: number) => api.get(`/ingredient/${id}`)
export const createIngredient = (body: Record<string, unknown>) => api.post('/ingredient', body)
export const updateIngredient = (id: number, body: Record<string, unknown>) =>
  api.put(`/ingredient/${id}`, body)
export const deleteIngredient = (id: number) => api.delete(`/ingredient/${id}`)

/** Unit metadata: which family a unit belongs to, so the form need not restate it. */
export const listUnits = () => api.get('/unit')

/** The free-text ingredient names users type most, i.e. what to add to the catalogue next. */
export const getCustomIngredientUsage = (page: number, size: number) =>
  api.get(`/ingredient/custom-usage?${qs({}, page, size)}`)

/** Re-points the recipe rows using a free-text name at a real ingredient. */
export const absorbCustomIngredient = (id: number, name: string) =>
  api.post(`/ingredient/${id}/absorb-custom`, {name})

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

// --------------------------------------------------------- default images
export const getDefaultImages = () => api.get('/admin/storage/defaults')

export const uploadDefaultImage = (image: string, file: File) => {
  const body = new FormData()
  body.append('file', file)
  // Overrides the client's JSON default; axios drops it again so the browser can add the
  // boundary, which is the same dance the consumer app does for its own uploads.
  return api.post(`/admin/storage/defaults/${image}`, body, {
    headers: {'Content-Type': 'multipart/form-data'},
  })
}

/** Puts the picture packaged with the app back in service. */
export const resetDefaultImage = (image: string) => api.delete(`/admin/storage/defaults/${image}`)

/**
 * A default keeps the same URL when it is replaced, so the preview asks for the version
 * the server just reported rather than whatever the browser still holds.
 */
export const defaultImageUrl = (path: string, lastModified?: number | null) =>
  `${import.meta.env.VITE_IMG_URL}/${path}?v=${lastModified ?? 0}`

// -------------------------------------------------------------------- mails
export const listMailTemplates = () => api.get('/admin/mails/templates')

/** A kind of the operator's own. It sends nothing until backend code names its key. */
export const addMailTemplate = (key: string) => api.post('/admin/mails/templates', {key})
export const deleteMailTemplate = (key: string) => api.delete(`/admin/mails/templates/${key}`)

export const saveMailTemplate = (key: string, locale: string, subject: string, body: string) =>
  api.put(`/admin/mails/templates/${key}/${locale}`, {subject, body})

/** Drops the saved wording, putting the one packaged with the app back in service. */
export const restoreMailTemplate = (key: string, locale: string) =>
  api.delete(`/admin/mails/templates/${key}/${locale}`)

/** Rendered by the server, from the draft in the editor, with the renderer that sends it. */
export const previewMailTemplate = (key: string, subject: string, body: string) =>
  api.post(`/admin/mails/templates/${key}/preview`, {subject, body})

/** Queues one mail through mail-service. What comes back means handed over, not delivered. */
export const sendTestMail = (key: string, recipient: string, locale: string) =>
  api.post(`/admin/mails/templates/${key}/test`, {recipient, locale})

// ------------------------------------------------------------ notifications

/**
 * How many users and devices a send would reach.
 *
 * Read before sending rather than after, so a broadcast is a decision taken with its size
 * in view. `users` empty means everybody, exactly as it does on the send itself.
 */
export const getPushAudience = (userIds: number[], locale: string | null) =>
  api.get(`/admin/notifications/audience?${qs({users: userIds.join(',') || '', locale: locale ?? ''})}`)

/**
 * Sends an announcement. An empty `userIds` is what asks for a broadcast.
 *
 * Answers 202: the notifications are stored, and the pushes are on their way. Only a device
 * can say one arrived.
 */
export const sendAnnouncement = (
  title: string, body: string, link: string, userIds: number[], locale: string | null,
) => api.post('/admin/notifications/announce', {title, body, link, userIds, locale})

/** One notification to the signed-in operator's own devices. Waited on, unlike the above. */
export const sendTestNotification = (title: string, body: string, link: string) =>
  api.post('/admin/notifications/test', {title, body, link})

// ---------------------------------------------------------------- documents
export const listPdfTemplates = () => api.get('/admin/documents/templates')

export const savePdfTemplate = (key: string, locale: string, body: string) =>
  api.put(`/admin/documents/templates/${key}/${locale}`, {body})

/** Drops the saved layout, putting the one packaged with the app back in service. */
export const restorePdfTemplate = (key: string, locale: string) =>
  api.delete(`/admin/documents/templates/${key}/${locale}`)

/**
 * Comes back as the printed PDF, not as the HTML behind it.
 *
 * The point of printing through a browser is that the print is not the screen — page
 * boxes, print media queries, where the pages break — so the preview has to be the
 * document itself for any of that to be checkable.
 */
export const previewPdfTemplate = (key: string, locale: string, body: string, recipeId: number | null) =>
  api.post(
    `/admin/documents/templates/${key}/${locale}/preview`,
    {body, recipeId},
    {responseType: 'blob'},
  )

// ----------------------------------------------------------------- releases

/** Every mobile platform, gated or not. The web app has no row: see AppPlatform. */
export const listAppVersions = () => api.get('/admin/app-versions')

export const saveAppVersion = (
  platform: string, minimumVersion: string, latestVersion: string, storeUrl: string,
) => api.put(`/admin/app-versions/${platform}`, {minimumVersion, latestVersion, storeUrl})

/** Removes the gate, which lets every build of that platform run again. */
export const clearAppVersion = (platform: string) => api.delete(`/admin/app-versions/${platform}`)

/**
 * What a floor would cost, measured on the installs that are actually in use.
 *
 * Read against the draft rather than against what is saved, and read *before* the save,
 * for the same reason the notifications tab reads its audience first: the apps a version
 * gate applies to have stopped asking anything else by then. Omitting `minimum` measures
 * the gate already in force.
 */
export const getAppVersionReach = (platform: string, minimum?: string) =>
  api.get(`/admin/app-versions/${platform}/reach?${qs({minimum: minimum ?? ''})}`)

/**
 * The verdict a device on `version` would get, from the endpoint a device actually calls.
 *
 * Deliberately the real thing rather than the same comparison rewritten here: what an
 * operator needs to trust before raising a floor is what phones will be told, and a second
 * implementation of "is this version older" is a second chance to get it wrong. It reflects
 * what is *saved*, so the tab only offers it once there is nothing unsaved in the form.
 */
export const checkAppVersion = (platform: string, version: string) =>
  api.get(`/app-version?${qs({platform, version})}`)

// --------------------------------------------------------------------- logs
export const getLogs = (f: Record<string, unknown>) => api.get(`/admin/logs?${qs(f)}`)
export const clearLogs = () => api.delete('/admin/logs')

/** EventSource opens its own connection, so the stream URL is built by hand. */
export const logStreamUrl = (f: Record<string, unknown>) =>
  `${import.meta.env.VITE_API_URL}/admin/logs/stream?${qs(f)}`
