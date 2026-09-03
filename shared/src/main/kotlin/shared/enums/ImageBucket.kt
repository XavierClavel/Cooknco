package shared.enums

import shared.utils.Filepath.COOKBOOKS_IMG_PATH
import shared.utils.Filepath.RECIPES_IMG_PATH
import shared.utils.Filepath.RECIPES_THUMBNAIL_PATH
import shared.utils.Filepath.USERS_IMG_PATH

/**
 * One directory of the image volume.
 *
 * Every file in a bucket is named `{ownerId}-v{imageVersion}.webp`, so a bucket is only
 * meaningful next to the entity holding that version: [dir] is where the files live and
 * how they are served, and the owning table is resolved by the backend.
 */
enum class ImageBucket(val path: String, val dir: String) {
    RECIPE(RECIPES_IMG_PATH, "recipes"),
    RECIPE_THUMBNAIL(RECIPES_THUMBNAIL_PATH, "recipes-thumbnails"),
    USER(USERS_IMG_PATH, "users"),
    COOKBOOK(COOKBOOKS_IMG_PATH, "cookbooks"),
}
