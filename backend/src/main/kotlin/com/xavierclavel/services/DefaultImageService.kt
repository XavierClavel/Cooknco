package com.xavierclavel.services

import com.drew.metadata.Metadata
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import shared.enums.DefaultImage
import shared.enums.ImageBucket
import shared.infodto.AdminDefaultImageFile
import shared.infodto.AdminDefaultImageInfo
import shared.utils.Filepath.DEFAULT_IMAGE
import shared.utils.logger
import java.awt.image.BufferedImage
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.readBytes

/**
 * The pictures shown in place of the ones users never uploaded.
 *
 * Every default has two possible sources, in order: the file an operator uploaded from the
 * backoffice, and the one packaged in the jar. The packaged picture is a floor rather than
 * a seed — it is never written to the volume — so a fresh install has something to show
 * without the storage tab reporting files nobody put there, and resetting a default is
 * just deleting what was uploaded over it.
 */
class DefaultImageService: KoinComponent {
    private val imageService: ImageService by inject()

    companion object {
        /** Packaged under `src/main/resources`; the extension is only what the file really is. */
        private val RESOURCES = mapOf(
            DefaultImage.USER to "/images/default-user.webp",
            DefaultImage.RECIPE to "/images/default-recipe.png",
            DefaultImage.COOKBOOK to "/images/default-cookbook.png",
        )
    }

    /**
     * Encoding the packaged picture costs a crop and a webp pass, and it cannot change
     * under us, so each bucket pays for it once per process rather than once per request.
     */
    private val packaged = ConcurrentHashMap<ImageBucket, ByteArray>()

    private fun uploadedFile(bucket: ImageBucket) = Path("${bucket.path}/$DEFAULT_IMAGE")

    // ------------------------------------------------------------------ serve

    /** The bytes to serve for a bucket, with the upload's mtime when there is one. */
    fun read(bucket: ImageBucket): Pair<ByteArray, Long?> {
        val file = uploadedFile(bucket)
        if (file.exists()) {
            try {
                return file.readBytes() to file.getLastModifiedTime().toMillis() / 1000
            } catch (e: IOException) {
                // Deleted between the check and the read, or not ours to read: the packaged
                // picture is still a better answer than a broken image on every page.
                logger.error(e) { "Could not read the default image of ${bucket.dir}" }
            }
        }
        return packagedBytes(bucket) to null
    }

    private fun packagedBytes(bucket: ImageBucket): ByteArray = packaged.getOrPut(bucket) {
        val resource = RESOURCES.getValue(DefaultImage.of(bucket))
        val source = javaClass.getResourceAsStream(resource)?.use { ImageIO.read(it) }
            ?: error("Default image $resource is missing from the jar")
        imageService.encodeWebp(source, bucket)
    }

    // ------------------------------------------------------------ backoffice

    fun describe(): List<AdminDefaultImageInfo> = DefaultImage.entries.map { image ->
        AdminDefaultImageInfo(
            image = image,
            custom = image.buckets.any { uploadedFile(it).exists() },
            files = image.buckets.map { describeFile(it) },
        )
    }

    private fun describeFile(bucket: ImageBucket): AdminDefaultImageFile {
        val file = uploadedFile(bucket)
        val uploaded = file.exists()
        return AdminDefaultImageFile(
            bucket = bucket,
            path = "${bucket.dir}/$DEFAULT_IMAGE",
            width = bucket.width,
            height = bucket.height,
            bytes = if (uploaded) file.fileSize() else packagedBytes(bucket).size.toLong(),
            lastModified = if (uploaded) file.getLastModifiedTime().toMillis() / 1000 else null,
        )
    }

    /**
     * Replaces a default with what was uploaded, in every bucket that shows it — a recipe
     * default is written full size and as a thumbnail, from the one source.
     */
    fun replace(image: DefaultImage, source: BufferedImage, metadata: Metadata) {
        image.buckets.forEach { imageService.saveDefaultImage(it, source, metadata) }
        logger.info { "Default image ${image.name} replaced" }
    }

    /**
     * Drops the uploaded file, which puts the packaged picture back in service.
     *
     * @return false when there was nothing uploaded to remove
     */
    fun reset(image: DefaultImage): Boolean {
        val removed = image.buckets.count { uploadedFile(it).deleteIfExists() } > 0
        if (removed) logger.info { "Default image ${image.name} reset to the packaged one" }
        return removed
    }
}
