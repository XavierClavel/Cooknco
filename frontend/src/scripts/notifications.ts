import apiClient from '@/plugins/axios.js';

export{
  getNotifications,
  markNotificationRead,
  markAllNotificationsRead,
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
