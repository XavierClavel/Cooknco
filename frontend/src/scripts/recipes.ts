import apiClient from '@/plugins/axios.js';
import {getLocale} from "@/scripts/localization";

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
 * Rejects on failure rather than logging, so the caller can tell the user the download
 * did not happen.
 */
async function downloadRecipe(id) {
  const response = await apiClient.get(`/export/recipe/${id}?locale=${getLocale()}`, {
    responseType: 'blob',
    headers: {
      'Accept': 'application/pdf'
    }
  })
  const url = window.URL.createObjectURL(new Blob([response.data], {type: 'application/pdf'}));
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', filenameOf(response) ?? `recipe-${id}.pdf`);
  document.body.appendChild(link);
  link.click();
  link.remove();
  // Deferred, not immediate: some browsers only start reading the blob after the click
  // returns, and revoking it first cancels the download. Without revoking at all it would
  // sit in memory for as long as the page lives.
  setTimeout(() => window.URL.revokeObjectURL(url), 0);
}

/** The name the backend chose for the file, from `Content-Disposition`. */
function filenameOf(response) {
  const disposition = response.headers['content-disposition']
  if (!disposition) return null
  return disposition.match(/filename="([^"]+)"/)?.[1] ?? null
}
