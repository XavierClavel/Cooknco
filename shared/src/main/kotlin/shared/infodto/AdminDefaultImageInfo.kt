package shared.infodto

import kotlinx.serialization.Serializable
import shared.enums.DefaultImage
import shared.enums.ImageBucket

/**
 * One backoffice-managed default, as the storage tab shows it.
 *
 * [custom] is what an operator acts on: false means the app is still serving the picture
 * packaged in its own jar, and there is nothing to reset.
 */
@Serializable
data class AdminDefaultImageInfo(
    val image: DefaultImage,
    val custom: Boolean,
    /** One entry per bucket this default is written to; a recipe carries a thumbnail too. */
    val files: List<AdminDefaultImageFile>,
)

/** The default as served for one bucket, at that bucket's dimensions. */
@Serializable
data class AdminDefaultImageFile(
    val bucket: ImageBucket,
    /** Path under the image server, e.g. `recipes/default.webp`. */
    val path: String,
    val width: Int,
    val height: Int,
    val bytes: Long,
    /** Epoch seconds. Null while the packaged picture is the one being served. */
    val lastModified: Long? = null,
)
