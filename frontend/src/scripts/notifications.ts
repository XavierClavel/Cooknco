import apiClient from '@/plugins/axios.js';

export{
  getNotifications,
  markNotificationRead,
  markAllNotificationsRead,
  clearNotification,
  clearAllNotifications,
}

async function getNotifications() {
  return await apiClient.get(`/notification`)
}

/** Marks one notification read. A 404 means it is not this user's, or is already gone. */
async function markNotificationRead(id) {
  return await apiClient.post(`/notification/${id}/read`)
}

async function markAllNotificationsRead() {
  return await apiClient.post(`/notification/read`)
}

/**
 * Clears one notification, for good — the backend deletes the row rather than hiding it.
 *
 * A 404 means it is not this user's or is already gone, which is the outcome the caller
 * wanted either way.
 */
async function clearNotification(id) {
  return await apiClient.delete(`/notification/${id}`)
}

/** Clears every notification the user has, read or not. Follow requests are untouched. */
async function clearAllNotifications() {
  return await apiClient.delete(`/notification`)
}
