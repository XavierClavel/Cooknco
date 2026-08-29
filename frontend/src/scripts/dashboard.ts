import apiClient from '@/scripts/axios';

export{
  getReport,
}

async function getReport() {
  return await apiClient.get(`/dashboard`)
}
