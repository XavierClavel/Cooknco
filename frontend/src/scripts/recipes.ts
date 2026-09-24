import apiClient from '@/plugins/axios.js';
import {getLocale} from "@/scripts/localization";
import {downloadFile, downloadPdf} from "@/scripts/download";

export {
  getRecipe,
  listRecipes,
  listPublicRecipes,
  createRecipe,
  updateRecipe,
  deleteRecipe,
  downloadRecipe,
  downloadRecipeAsCooklang,
  importCooklang,
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

/**
 * The newest recipes anyone may read, for the landing page.
 *
 * Deliberately not [listRecipes], which refuses a query naming no source: every one of those
 * sources is a person — my recipes, the ones I liked, the ones people I follow wrote — and a
 * signed-out visitor is nobody. The endpoint itself has always allowed it and applies its own
 * anonymous visibility rule (`filterByVisibility` with no requestor), so what comes back is
 * exactly the public set.
 */
async function listPublicRecipes(size: number) {
  return await apiClient.get(`/recipe?sort=DATE_DESCENDING&page=0&size=${size}`)
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
 * Premium, and the backend enforces it: the button lives inside `<premium-only>`.
 */
async function downloadRecipe(id) {
  return await downloadPdf(`/export/recipe/${id}?locale=${getLocale()}`, `recipe-${id}.pdf`)
}

/**
 * Saves a recipe as a Cooklang file.
 *
 * Premium like the PDF, and enforced the same way: the button lives inside `<premium-only>`
 * and the route refuses anyone else. No `unitSystem` — a `.cook` file is loaded by another
 * app rather than read by a person, so it goes out in the units it was written in and
 * whatever opens it converts for whoever is looking.
 */
async function downloadRecipeAsCooklang(id) {
  return await downloadFile(
    `/export/recipe/${id}/cooklang?locale=${getLocale()}`,
    `recipe-${id}.cook`,
    'text/x-cooklang',
  )
}

/**
 * Reads a Cooklang file and answers with the recipe to fill the editor in with.
 *
 * Saves nothing — see `CooklangService` — so what comes back is what the editor should show,
 * not a recipe that now exists. Open to any signed-in account, unlike the export: getting a
 * collection *into* the product is not what a subscription is for.
 *
 * Posted as text rather than as a form: the body is the file, and there is nothing else to
 * send with it.
 */
async function importCooklang(source: string) {
  return await apiClient.post(`/recipe/import/cooklang?locale=${getLocale()}`, source, {
    headers: {'Content-Type': 'text/plain'},
  })
}
