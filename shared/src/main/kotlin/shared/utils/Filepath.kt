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

    /**
     * The picture every bucket serves in place of one it does not have.
     *
     * The name is fixed because it is what `staticFiles { default(...) }` falls back to;
     * the file itself is written by the backoffice, and is absent until an operator
     * uploads one, in which case the app serves the picture packaged in its own jar.
     */
    const val DEFAULT_IMAGE = "default.webp"
}
