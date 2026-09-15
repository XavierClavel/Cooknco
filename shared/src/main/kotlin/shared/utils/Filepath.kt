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

    /**
     * Root of the database backup volume — the `database-backups` PVC the nightly
     * `pg_dump` CronJob writes to (`k8s/base/backup.yaml`), mounted read-only into the
     * backend so the backoffice can report on what is actually on it.
     *
     * Overridable like [IMG_ROOT], and for the same reason. Absent outside the cluster: a
     * developer running the stack locally has no backup job, and the backups tab says so
     * rather than reporting an empty volume as a run of failed nights.
     */
    val BACKUPS_ROOT: String = System.getenv("COOKNCO_BACKUPS_ROOT")?.takeIf { it.isNotBlank() } ?: "/backups"
}
