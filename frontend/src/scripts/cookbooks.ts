import apiClient from '@/plugins/axios.js';
import {getLocale} from "@/scripts/localization";
import {downloadPdf} from "@/scripts/download";

export {
  getCookbook,
  listCookbooks,
  getStatusInCookbooks,
  createCookbook,
  editCookbook,
  searchCookbooks,
  leaveCookbook,

  addRecipeToCookbook,
  removeRecipeFromCookbook,

  getCookbookUsers,
  setCookbookUsers,
  isAdminOfCookbook,

  downloadCookbook,
}

async function getCookbook(id) {
  return await apiClient.get(`/cookbook/${id}`)
}

async function listCookbooks(search, page: number, size: number) {
  const query = new URLSearchParams(search)
  if (page != undefined) query.append('page', page)
  if (size != undefined) query.append('size', size)
  return await apiClient.get(`/cookbook?${query.toString()}`)
}

async function getStatusInCookbooks(recipe) {
  return await apiClient.get(`/cookbook/recipeStatus?recipe=${recipe}`)
}

async function createCookbook(dto) {
  return await apiClient.post(`/cookbook`, dto)
}

async function editCookbook(id, dto) {
  return await apiClient.put(`/cookbook/${id}`, dto)
}

async function addRecipeToCookbook(cookbookId, recipeId) {
  return await apiClient.post(`cookbook/${cookbookId}/recipe/${recipeId}`)
}

async function removeRecipeFromCookbook(cookbookId, recipeId) {
  return await apiClient.delete(`cookbook/${cookbookId}/recipe/${recipeId}`)
}

async function searchCookbooks(search) {
  return await apiClient.get(`cookbook?query=${search}`)
}

async function getCookbookUsers(cookbookId) {
  return await apiClient.get(`/cookbook/${cookbookId}/users`)
}

async function setCookbookUsers(cookbookId, input) {
  return await apiClient.put(`/cookbook/${cookbookId}/users`, input)
}

async function leaveCookbook(cookbookId) {
  return await apiClient.delete(`/cookbook/${cookbookId}/leave`)
}

async function isAdminOfCookbook(cookbookId) {
  return await apiClient.get(`/cookbook/${cookbookId}/userStatus`)
}

/**
 * Saves a whole cookbook as a PDF: a cover, then a page per recipe.
 *
 * Admin-only, and the backend enforces it: the button lives inside `<admin-only>`. A
 * cookbook past the backend's bound is refused rather than printed short, which reaches
 * the caller as a rejection like any other.
 */
async function downloadCookbook(id) {
  return await downloadPdf(`/export/cookbook/${id}?locale=${getLocale()}`, `cookbook-${id}.pdf`)
}
