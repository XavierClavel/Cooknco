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
 *
 * [width] and [height] are what everything written into the bucket is cropped and resized
 * to — uploads and the backoffice-managed default alike, so the two sit in the same frames.
 */
enum class ImageBucket(val path: String, val dir: String, val width: Int, val height: Int) {
    /* Displayed at 800x600, 240x180 for the icon, 200x200 for a user, 250x250 for a cookbook. */
    RECIPE(RECIPES_IMG_PATH, "recipes", 1600, 1200),
    RECIPE_THUMBNAIL(RECIPES_THUMBNAIL_PATH, "recipes-thumbnails", 480, 360),
    USER(USERS_IMG_PATH, "users", 400, 400),
    COOKBOOK(COOKBOOKS_IMG_PATH, "cookbooks", 500, 500),
    ;

    val size: Pair<Int, Int> get() = Pair(width, height)
}
