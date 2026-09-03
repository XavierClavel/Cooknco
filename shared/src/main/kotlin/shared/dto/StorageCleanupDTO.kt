package shared.dto

import kotlinx.serialization.Serializable
import shared.enums.ImageBucket
import shared.enums.ImageStatus

/**
 * What a backoffice cleanup run should sweep.
 *
 * Both lists are opt-in and empty means "every bucket" / "the safe default set", so a
 * cleanup never widens silently when a new bucket or status is added.
 */
@Serializable
data class StorageCleanupDTO(
    val buckets: List<ImageBucket> = emptyList(),
    val statuses: List<ImageStatus> = listOf(ImageStatus.STALE, ImageStatus.ORPHAN),
    /** Counts what would be removed without touching the volume. */
    val dryRun: Boolean = false,
)
