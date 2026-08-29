import apiClient from '@/scripts/axios';

export{
  getNotifications,
}

async function getNotifications() {
  return await apiClient.get(`/notification`)
}
