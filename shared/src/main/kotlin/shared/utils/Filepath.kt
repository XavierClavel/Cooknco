package shared.utils

object Filepath {
    /**
     * Root of the image volume (a docker volume in production, see `compose.yaml`).
     *
     * Overridable through the environment so tests can point the whole tree at a
     * temporary directory instead of writing to an absolute path they may not own.
     */
    val IMG_ROOT: String = System.getenv("COOKNCO_IMG_ROOT")?.takeIf { it.isNotBlank() } ?: "/img"

    val RECIPES_IMG_PATH = "$IMG_ROOT/recipes"
    val RECIPES_THUMBNAIL_PATH = "$IMG_ROOT/recipes-thumbnails"
    val USERS_IMG_PATH = "$IMG_ROOT/users"
    val COOKBOOKS_IMG_PATH = "$IMG_ROOT/cookbooks"
}
