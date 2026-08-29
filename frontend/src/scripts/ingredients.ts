import apiClient from '@/scripts/axios';
import {getLocale} from "@/scripts/localization";

export{
  getIngredient,
  searchIngredients,
  deleteIngredient,
  getCount,
  getIngredientRecipesCount,
  createIngredient,
  updateIngredient,
}

async function getIngredient(id) {
  return await apiClient.get(`/ingredient/${id}`)
}

async function searchIngredients(search, page, size) {
  const query = new URLSearchParams(search)
  if (page != undefined) query.append('page', page)
  if (size != undefined) query.append('size', size)
  query.append('locale', getLocale())
  return await apiClient.get(`/ingredient?${query.toString()}`)
}

async function getCount() {
  return await apiClient.get(`/ingredient/count`)
}

async function getIngredientRecipesCount(id) {
  return await apiClient.get(`/ingredient/count/recipes/${id}`)
}

async function deleteIngredient(username) {
  return await apiClient.delete(`/ingredient/${username}`)
}

async function createIngredient(ingredient) {
  return await apiClient.post(`/ingredient`, ingredient)
}

async function updateIngredient(id, ingredient) {
  return await apiClient.put(`/ingredient/${id}`, ingredient)
}
