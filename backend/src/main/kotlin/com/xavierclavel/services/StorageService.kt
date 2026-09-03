package com.xavierclavel.services

import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import io.ebean.DB
import io.ebean.Paging
import org.koin.core.component.KoinComponent
import shared.dto.StorageCleanupDTO
import shared.enums.ImageBucket
import shared.enums.ImageSort
import shared.enums.ImageStatus
import shared.infodto.AdminImageInfo
import shared.infodto.AdminStorageBucket
import shared.infodto.AdminStorageCleanupResult
import shared.infodto.AdminStorageOverview
import shared.utils.Filepath.DEFAULT_IMAGE
import shared.utils.Filepath.IMG_ROOT
import shared.utils.logger
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.Path

/**
 * The image volume, seen from the backoffice.
 *
 * Images are the only user data the app keeps outside the database, and nothing has ever
 * reconciled the two: deleting an account or a cookbook drops the rows and leaves the
 * files, and an upload that dies between writing the new version and deleting the old one
 * leaves both. This service reads the volume back against the `imageVersion` each entity
 * carries, so the drift is visible and can be swept.
 *
 * Everything here scans on demand rather than caching. A stale storage figure is worse
 * than a slow one — an operator deletes on the strength of it.
 */
class StorageService: KoinComponent {

    companion object {
        /** Every file the app writes is `{ownerId}-v{imageVersion}.webp`. */
        private val FILENAME = Regex("""^(\d+)-v(\d+)\.webp$""")

        /**
         * The backoffice-managed defaults. They have no owner row, so the ownership rules
         * below would read them as orphans and offer to delete the picture every missing
         * image on the site falls back to; resetting one is done from the defaults panel,
         * which puts the packaged picture back rather than leaving a hole.
         */
        private val RESERVED_FILENAMES = setOf(DEFAULT_IMAGE)
    }

    // ------------------------------------------------------------------ scan

    /** A file found on the volume, with the owner its name claims. [lastModified] is epoch seconds. */
    private data class ImageFile(
        val filename: String,
        val ownerId: Long?,
        val version: Long?,
        val bytes: Long,
        val lastModified: Long,
    )

    /** The owning row behind a bucket's files: its current version and something to call it. */
    private data class Owner(val version: Long, val label: String)

    /**
     * Owner rows are shared between buckets — recipe images and recipe thumbnails answer to
     * the same `recipes` rows — so they are loaded once per table for the length of one
     * request rather than once per bucket.
     */
    private inner class Scan {
        private val ownersByTable = HashMap<String, Map<Long, Owner>>()

        fun owners(bucket: ImageBucket): Map<Long, Owner> =
            ownersByTable.getOrPut(bucket.table) { loadOwners(bucket) }
    }

    private val ImageBucket.table: String get() = when (this) {
        ImageBucket.RECIPE, ImageBucket.RECIPE_THUMBNAIL -> "recipes"
        ImageBucket.USER -> "users"
        ImageBucket.COOKBOOK -> "cookbooks"
    }

    private val ImageBucket.labelColumn: String get() = when (this) {
        ImageBucket.USER -> "username"
        else -> "title"
    }

    private fun loadOwners(bucket: ImageBucket): Map<Long, Owner> =
        DB.sqlQuery("select id, image_version, ${bucket.labelColumn} as label from ${bucket.table}")
            .findList()
            .associate { it.getLong("id") to Owner(it.getLong("image_version"), it.getString("label") ?: "") }

    /**
     * Lists a bucket's regular files with their size and mtime.
     *
     * A bucket that was never written to has no directory, which is an empty bucket rather
     * than a failure; an unreadable one is logged and reported the same way, so a single bad
     * mount cannot take the whole storage page down.
     */
    private fun scanFiles(bucket: ImageBucket): List<ImageFile> {
        val dir = Path(bucket.path)
        if (!Files.isDirectory(dir)) return emptyList()
        return try {
            Files.newDirectoryStream(dir).use { stream -> stream.mapNotNull { readFile(it) } }
        } catch (e: IOException) {
            logger.error(e) { "Could not read image bucket ${bucket.path}" }
            emptyList()
        }
    }

    private fun readFile(path: Path): ImageFile? {
        val attributes = try {
            Files.readAttributes(path, BasicFileAttributes::class.java)
        } catch (e: IOException) {
            // Vanished between the listing and the stat, or not ours to read
            return null
        }
        if (!attributes.isRegularFile) return null
        val filename = path.fileName.toString()
        val match = FILENAME.matchEntire(filename)
        return ImageFile(
            filename = filename,
            ownerId = match?.groupValues?.get(1)?.toLongOrNull(),
            version = match?.groupValues?.get(2)?.toLongOrNull(),
            bytes = attributes.size(),
            lastModified = attributes.lastModifiedTime().toMillis() / 1000,
        )
    }

    private fun statusOf(file: ImageFile, owners: Map<Long, Owner>): ImageStatus = when {
        file.filename in RESERVED_FILENAMES -> ImageStatus.CURRENT
        file.ownerId == null || file.version == null -> ImageStatus.UNKNOWN
        else -> when (owners[file.ownerId]?.version) {
            null -> ImageStatus.ORPHAN
            file.version -> ImageStatus.CURRENT
            else -> ImageStatus.STALE
        }
    }

    // -------------------------------------------------------------- overview

    fun buildOverview(): AdminStorageOverview {
        val startedAt = System.currentTimeMillis()
        val scan = Scan()
        val buckets = ImageBucket.entries.map { summarise(it, scan) }
        val root = File(IMG_ROOT)
        val mounted = root.isDirectory

        return AdminStorageOverview(
            buckets = buckets,
            totalFiles = buckets.sumOf { it.files },
            totalBytes = buckets.sumOf { it.bytes },
            reclaimableFiles = buckets.sumOf { b -> b.countByStatus.reclaimable().sumOf { it.value } },
            reclaimableBytes = buckets.sumOf { b -> b.bytesByStatus.reclaimable().sumOf { it.value } },
            missingImages = buckets.sumOf { it.countByStatus[ImageStatus.MISSING] ?: 0 },
            volumeTotalBytes = if (mounted) root.totalSpace else 0,
            volumeFreeBytes = if (mounted) root.usableSpace else 0,
            scanDurationMs = System.currentTimeMillis() - startedAt,
        )
    }

    private fun <T> Map<ImageStatus, T>.reclaimable() = entries.filter { it.key.isReclaimable() }

    private fun summarise(bucket: ImageBucket, scan: Scan): AdminStorageBucket {
        val owners = scan.owners(bucket)
        val files = scanFiles(bucket)
        val byStatus = files.groupBy { statusOf(it, owners) }
        val missing = missingOwners(files, owners).size

        return AdminStorageBucket(
            bucket = bucket,
            path = bucket.path,
            exists = Files.isDirectory(Path(bucket.path)),
            files = files.size,
            bytes = files.sumOf { it.bytes },
            countByStatus = byStatus.mapValues { it.value.size }.withMissing(missing),
            // A missing image occupies nothing, so it deliberately has no byte entry
            bytesByStatus = byStatus.mapValues { entry -> entry.value.sumOf { it.bytes } },
            largestFileBytes = files.maxOfOrNull { it.bytes } ?: 0,
            lastModified = files.maxOfOrNull { it.lastModified },
        )
    }

    private fun Map<ImageStatus, Int>.withMissing(missing: Int): Map<ImageStatus, Int> =
        if (missing == 0) this else this + (ImageStatus.MISSING to missing)

    /** Owners pointing at a version that is not on the volume — the broken images. */
    private fun missingOwners(files: List<ImageFile>, owners: Map<Long, Owner>): List<Map.Entry<Long, Owner>> {
        val present = files.mapTo(HashSet()) { it.filename }
        return owners.entries.filter { (id, owner) -> owner.version > 0 && expectedName(id, owner.version) !in present }
    }

    private fun expectedName(ownerId: Long, version: Long) = "$ownerId-v$version.webp"

    // --------------------------------------------------------------- listing

    /**
     * The image table.
     *
     * Filtering, sorting and paging happen in memory: the rows come from a directory
     * listing crossed with the owner rows, and there is no query that can express that.
     *
     * @param bucket restricts to one directory; null scans them all
     * @param query matched against the filename and the owner's name
     */
    fun searchImages(
        bucket: ImageBucket?,
        status: ImageStatus?,
        query: String?,
        sort: ImageSort,
        paging: Paging,
    ): Pair<Int, List<AdminImageInfo>> {
        val scan = Scan()
        val rows = (bucket?.let { listOf(it) } ?: ImageBucket.entries)
            .flatMap { rowsOf(it, scan) }
            .filter { status == null || it.status == status }
            .filter { matches(it, query) }

        val page = rows.sortedWith(comparatorFor(sort))
            .drop(paging.pageIndex() * paging.pageSize())
            .take(paging.pageSize())

        return Pair(rows.size, page)
    }

    private fun rowsOf(bucket: ImageBucket, scan: Scan): List<AdminImageInfo> {
        val owners = scan.owners(bucket)
        val files = scanFiles(bucket)

        val onDisk = files.map { file ->
            val owner = file.ownerId?.let { owners[it] }
            AdminImageInfo(
                bucket = bucket,
                filename = file.filename,
                status = statusOf(file, owners),
                bytes = file.bytes,
                lastModified = file.lastModified,
                ownerId = file.ownerId,
                version = file.version,
                ownerVersion = owner?.version,
                ownerLabel = owner?.label ?: "",
            )
        }

        val missing = missingOwners(files, owners)
            .map { (id, owner) ->
                AdminImageInfo(
                    bucket = bucket,
                    filename = expectedName(id, owner.version),
                    status = ImageStatus.MISSING,
                    bytes = 0,
                    ownerId = id,
                    version = owner.version,
                    ownerVersion = owner.version,
                    ownerLabel = owner.label,
                )
            }

        return onDisk + missing
    }

    private fun matches(row: AdminImageInfo, query: String?): Boolean {
        if (query.isNullOrBlank()) return true
        return row.filename.contains(query, ignoreCase = true) ||
            row.ownerLabel.contains(query, ignoreCase = true)
    }

    private fun comparatorFor(sort: ImageSort): Comparator<AdminImageInfo> = when (sort) {
        ImageSort.SIZE_DESCENDING -> compareByDescending<AdminImageInfo> { it.bytes }
        ImageSort.SIZE_ASCENDING -> compareBy<AdminImageInfo> { it.bytes }
        // A missing row has no mtime; it sorts last either way rather than as the epoch
        ImageSort.DATE_DESCENDING -> compareByDescending<AdminImageInfo> { it.lastModified ?: Long.MIN_VALUE }
        ImageSort.DATE_ASCENDING -> compareBy<AdminImageInfo> { it.lastModified ?: Long.MAX_VALUE }
        ImageSort.NAME_ASCENDING ->
            compareBy<AdminImageInfo>({ it.bucket }, { it.ownerId ?: Long.MAX_VALUE }, { it.filename })
    }

    // -------------------------------------------------------------- deletion

    /**
     * Removes a single file.
     *
     * @return false when it was already gone
     * @throws BadRequestException when the name is not a plain file inside the bucket, or
     *   names one of the shipped fallbacks
     */
    fun deleteFile(bucket: ImageBucket, filename: String): Boolean {
        val target = resolveInBucket(bucket, filename)
        if (!Files.exists(target)) return false
        return try {
            Files.deleteIfExists(target)
        } catch (e: IOException) {
            logger.error(e) { "Could not delete $target" }
            false
        }
    }

    /**
     * Turns a client-supplied name into a path, refusing anything that would escape the
     * bucket. The name is compared after normalisation rather than screened for `..`, so
     * encodings of a traversal are caught as well as the literal one.
     */
    private fun resolveInBucket(bucket: ImageBucket, filename: String): Path {
        if (filename.isBlank() || filename in RESERVED_FILENAMES) {
            throw BadRequestException(BadRequestCause.INVALID_REQUEST)
        }
        val dir = Path(bucket.path).toAbsolutePath().normalize()
        val target = dir.resolve(filename).toAbsolutePath().normalize()
        if (target.parent != dir || target.fileName.toString() != filename) {
            throw BadRequestException(BadRequestCause.INVALID_REQUEST)
        }
        return target
    }

    /**
     * Sweeps every file matching the requested statuses.
     *
     * Only statuses that describe a file the app has finished with can be swept — see
     * [ImageStatus.isReclaimable]. Asking for anything else is a bad request rather than a
     * silently narrowed run, because the caller's idea of what it deleted would be wrong.
     */
    fun cleanup(request: StorageCleanupDTO): AdminStorageCleanupResult {
        val statuses = request.statuses.ifEmpty { listOf(ImageStatus.STALE, ImageStatus.ORPHAN) }
        if (statuses.any { !it.isReclaimable() }) throw BadRequestException(BadRequestCause.INVALID_REQUEST)
        val buckets = request.buckets.ifEmpty { ImageBucket.entries }

        val scan = Scan()
        var files = 0
        var bytes = 0L
        var failed = 0

        buckets.distinct().forEach { bucket ->
            val owners = scan.owners(bucket)
            scanFiles(bucket)
                .filter { statusOf(it, owners) in statuses }
                .forEach { file ->
                    if (request.dryRun || deleteFile(bucket, file.filename)) {
                        files++
                        bytes += file.bytes
                    } else {
                        failed++
                    }
                }
        }

        if (!request.dryRun) {
            logger.info { "Storage cleanup removed $files file(s), $bytes byte(s), $failed failure(s)" }
        }
        return AdminStorageCleanupResult(request.dryRun, files, bytes, failed)
    }
}
