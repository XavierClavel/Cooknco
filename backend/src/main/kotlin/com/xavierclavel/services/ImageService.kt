package com.xavierclavel.services

import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifIFD0Directory
import com.xavierclavel.enums.ExifOrientation
import shared.utils.Filepath.RECIPES_IMG_PATH
import org.koin.core.component.KoinComponent
import java.awt.Image
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import net.coobird.thumbnailator.Thumbnails
import shared.utils.logger
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter

class ImageService: KoinComponent {


    fun saveImage(path: String, id: Long, version: Long, targetSize: Pair<Int, Int>, image: BufferedImage, metadata: Metadata) =
        saveImage("$path/$id-v$version.webp", targetSize, image, metadata)

    fun deleteImage(path: String, id: Long, version: Long) {
        if (version == 0L) return
        deleteImage("$path/$id-v$version.webp")
    }

    private fun deleteImage(path:String) {
        val result = Path(path).deleteIfExists()
        logger.info("Deleted image: $result at $path")
    }




    private fun saveImage(path: String, targetSize: Pair<Int, Int>, image: BufferedImage, metadata: Metadata) {
        val file = Path(path)
        file.createParentDirectories()
        if (!file.exists()) {
            file.createFile()
        }

        val directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
        val orientation = ExifOrientation.fromInt(directory?.getInt(ExifIFD0Directory.TAG_ORIENTATION) ?: 1)
        logger.info { "Processing image with orientation $orientation" }
        val orientedImage = applyOrientation(image, orientation)
        val cleanedImage = cropAndResizeImage(orientedImage, targetSize.first, targetSize.second)
        ImageIO.write(cleanedImage, "webp", file.toFile())
    }

    private fun applyOrientation(image: BufferedImage, orientation: ExifOrientation): BufferedImage {
        val transform = orientation.getTransform(image.width.toDouble(), image.height.toDouble())

        val newWidth = if (orientation.requiresSwap()) image.height else image.width
        val newHeight = if (orientation.requiresSwap()) image.width else image.height

        val result = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)

        val g = result.createGraphics()
        g.transform = transform
        g.drawImage(image, 0, 0, null)
        g.dispose()

        return result
    }

    private fun cropAndResizeImage(originalImage: BufferedImage, targetWidth: Int, targetHeight: Int): BufferedImage {
        val targetRatio = targetWidth.toDouble() / targetHeight.toDouble()
        val originalRatio = originalImage.width.toDouble() / originalImage.height.toDouble()

        var cropX = 0
        var cropY = 0
        var cropWidth = originalImage.width
        var cropHeight = originalImage.height

        // Crop the image to match the target aspect ratio
        if (originalRatio > targetRatio) {
            // Image is too wide, crop the sides
            cropWidth = (originalImage.height * targetRatio).toInt()
            cropX = (originalImage.width - cropWidth) / 2
        } else if (originalRatio < targetRatio) {
            // Image is too tall, crop the top and bottom
            cropHeight = (originalImage.width / targetRatio).toInt()
            cropY = (originalImage.height - cropHeight) / 2
        }

        val croppedImage = originalImage.getSubimage(cropX, cropY, cropWidth, cropHeight)

        // Resize the cropped image
        val resizedImage = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = resizedImage.createGraphics()

        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        graphics.drawImage(croppedImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH), 0, 0, null)
        graphics.dispose()

        return resizedImage
    }


    private fun convertWebPToJpeg(webpBytes: ByteArray): ByteArray {
        val image = ImageIO.read(ByteArrayInputStream(webpBytes))
        ByteArrayOutputStream().use { baos ->
            ImageIO.write(image, "jpg", baos)
            return baos.toByteArray()
        }
    }

    /**
     * Reads a webp image and returns it as a jpeg byte array
     */
    fun getRecipeImageAsJpegBytes(id: Long): ByteArray {
        // Specify the path to the WebP file
        val webpPath = "$RECIPES_IMG_PATH/$id.webp"

        // Read the WebP image from the filesystem
        val webpBytes = Files.readAllBytes(Paths.get(webpPath))

        // Convert WebP to JPEG format
        val jpegBytes = convertWebPToJpeg(webpBytes)

        return jpegBytes
    }

}