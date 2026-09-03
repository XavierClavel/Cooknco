/**
 * Image URLs for the backoffice.
 *
 * Rebuilt here rather than reused from the consumer app's `scripts/common`,
 * which drags in the router, the auth store and Capacitor — none of which
 * belong in the admin bundle.
 *
 * `version` is the entity's image version: 0 means nothing was ever uploaded,
 * so there is no file to ask for and the caller shows a placeholder instead.
 */
const IMG_URL = import.meta.env.VITE_IMG_URL

const url = (folder: string, id?: number, version?: number) =>
  id && version ? `${IMG_URL}/${folder}/${id}-v${version}.webp` : ''

/** 400×400 avatar. */
export const userIconUrl = (id?: number, version?: number) => url('users', id, version)

/** 480×360 crop — the right weight for a table row. */
export const recipeThumbnailUrl = (id?: number, version?: number) =>
  url('recipes-thumbnails', id, version)

/** 1600×1200 original, for the detail modal. */
export const recipeImageUrl = (id?: number, version?: number) => url('recipes', id, version)
