import apiClient from '@/scripts/axios';

export {
  addLike,
  removeLike,
  isLiked,
}

async function addLike(id) {
  return await apiClient.post(`/like/${id}`)
}

async function isLiked(id) {
  const response = await apiClient.get(`/like/${id}`)
  return response.data
}

async function removeLike(id) {
  return await apiClient.delete(`/like/${id}`)
}
