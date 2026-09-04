import apiClient from '@/plugins/axios.js';
import {getLocale} from "@/scripts/localization";

// Ingredient writes and the custom-ingredient funnel are backoffice-only and go through
// src/admin/lib/api.ts, which carries the admin session cookie.
export{
  getIngredient,
  searchIngredients,
  getCount,
  getIngredientRecipesCount,
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
