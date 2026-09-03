package shared.infodto

import kotlinx.serialization.Serializable
import shared.enums.ImageBucket
import shared.enums.ImageStatus

/** Storage figures for the whole image volume, one entry per bucket. */
@Serializable
data class AdminStorageOverview(
    val buckets: List<AdminStorageBucket>,
    val totalFiles: Int,
    val totalBytes: Long,
    val reclaimableFiles: Int,
    val reclaimableBytes: Long,
    val missingImages: Int,
    /** Capacity of the filesystem holding the volume; 0 when the root is not mounted. */
    val volumeTotalBytes: Long,
    val volumeFreeBytes: Long,
    val scanDurationMs: Long,
)

/** One directory of the image volume, with the state of the files it holds. */
@Serializable
data class AdminStorageBucket(
    val bucket: ImageBucket,
    val path: String,
    /** False when the directory has never been created — an empty bucket, not an error. */
    val exists: Boolean,
    val files: Int,
    val bytes: Long,
    /** File counts and sizes by status, so the table can show what is reclaimable per bucket. */
    val countByStatus: Map<ImageStatus, Int>,
    val bytesByStatus: Map<ImageStatus, Long>,
    val largestFileBytes: Long,
    /** Most recent write in the bucket, epoch seconds; null when the bucket is empty. */
    val lastModified: Long? = null,
)

/**
 * A row of the backoffice image table.
 *
 * Rows are usually files, but a [ImageStatus.MISSING] row is the opposite: an owner whose
 * image is not on disk. Those carry no [bytes] or [lastModified].
 */
@Serializable
data class AdminImageInfo(
    val bucket: ImageBucket,
    val filename: String,
    val status: ImageStatus,
    val bytes: Long,
    /** Epoch seconds, as everywhere else in the API. */
    val lastModified: Long? = null,
    /** Null when the filename does not parse as one of ours. */
    val ownerId: Long? = null,
    val version: Long? = null,
    /** The owner's current `imageVersion`; null when the owner is gone. */
    val ownerVersion: Long? = null,
    /** Recipe title, username or cookbook title — empty when there is no owner to name. */
    val ownerLabel: String = "",
)

/** What a cleanup run did, or would do when asked for a dry run. */
@Serializable
data class AdminStorageCleanupResult(
    val dryRun: Boolean,
    val files: Int,
    val bytes: Long,
    /** Files that matched but could not be removed; they stay in the listing. */
    val failed: Int,
)
