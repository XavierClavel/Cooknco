import apiClient from '@/plugins/axios.js';

export {
  getSettings,
  updateSettings,
  unsubscribeFromMails,
}

async function getSettings() {
  return await apiClient.get(`/user/settings`)
}

async function updateSettings(settings) {
  return await apiClient.put(`/user/settings`, settings)
}

/**
 * Turns the notification mails off from the link one of them carried.
 *
 * No session: the signed token in the link is what the backend goes on, which is the whole
 * point of an unsubscribe that works straight from an inbox. The token is URL-safe by
 * construction, so it goes in the query as it stands. It only ever switches these mails off
 * - turning them back on is `updateSettings`, signed in.
 */
async function unsubscribeFromMails(token) {
  return await apiClient.post(`/user/unsubscribe?token=${token}`)
}
