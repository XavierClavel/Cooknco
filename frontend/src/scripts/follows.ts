import apiClient from '@/scripts/axios';
import {useAuthStore} from "@/stores/auth";

export{
  follow,
  unfollow,
  removeFollower,
  getFollows,
  getFollowers,
  isFollowingUser,

  acceptFollowRequest,
  declineFollowRequest,
}

async function follow(id) {
  return await apiClient.post(`/follow/${id}`)
}

async function unfollow(id) {
  return await apiClient.delete(`/follow/${id}`)
}

async function acceptFollowRequest(id) {
  return await apiClient.post(`/follow/${id}/request`)
}

async function declineFollowRequest(id) {
  return await apiClient.delete(`/follow/${id}/request`)
}

async function removeFollower(id) {
  return await apiClient.delete(`/follow/${id}/request`)
}

async function getFollows(id) {
  return await apiClient.get(`/follow/${id}/follows`)
}

async function getFollowers(id) {
  return await apiClient.get(`/follow/${id}/followers`)
}

async function isFollowingUser(id) {
  const authStore = useAuthStore()
  const userId = authStore.id
  return await apiClient.get(`/follow/${userId}/follows/${id}`)
}
