import apiClient from '@/plugins/axios.js';
import {getLocale} from "@/scripts/localization";
import {downloadPdf} from "@/scripts/download";

export {
  getRecipe,
  listRecipes,
  createRecipe,
  updateRecipe,
  deleteRecipe,
  downloadRecipe,
}

async function getRecipe(id) {
  return await apiClient.get(`/recipe/${id}?locale=${getLocale()}`)
}

async function listRecipes(search: string, page: number, size: number) {
  const query = new URLSearchParams(search)
  if(!query.has('user') &&
    !query.has('likedBy') &&
    !query.has('cookbookUser') &&
    !query.has('followedBy') &&
    !query.has('cookbook') &&
    !query.has('ingredient')
  ) {
    throw "no_recipe_source"
  }
  if (page != undefined) query.append('page', page)
  if (size != undefined) query.append('size', size)
  return await apiClient.get(`/recipe?${query.toString()}`)
}

async function createRecipe(recipe) {
  return await apiClient.post(`/recipe`, recipe)
}

async function updateRecipe(id,recipe) {
  return await apiClient.put(`/recipe/${id}`, recipe)
}

async function deleteRecipe(id) {
  return await apiClient.delete(`/recipe/${id}`)
}

/**
 * Saves a recipe as a PDF.
 *
 * Admin-only, and the backend enforces it: the button lives inside `<admin-only>`.
 */
async function downloadRecipe(id) {
  return await downloadPdf(`/export/recipe/${id}?locale=${getLocale()}`, `recipe-${id}.pdf`)
}
