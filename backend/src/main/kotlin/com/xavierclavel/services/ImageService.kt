package com.xavierclavel.services

import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifIFD0Directory
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
        val affineTransform = getEFIXTransform(metadata, image)
        val cleanedImage = cropAndResizeImage(image, targetSize.first, targetSize.second, affineTransform)
        ImageIO.write(cleanedImage, "webp", file.toFile())
    }

    fun saveWebP(image: BufferedImage, outputFile: File, quality: Float = 0.9f) {
        val writers = ImageIO.getImageWritersByFormatName("webp")
        if (!writers.hasNext()) {
            throw IllegalStateException("No WebP ImageWriter available")
        }

        val imageWriter: ImageWriter = writers.next()
        val outputStream = ImageIO.createImageOutputStream(outputFile)
        imageWriter.output = outputStream

        val writeParam = imageWriter.defaultWriteParam
        if (writeParam.canWriteCompressed()) {
            writeParam.compressionMode = ImageWriteParam.MODE_EXPLICIT

            val compressionTypes = writeParam.compressionTypes // Get available compression types
            if (compressionTypes != null && compressionTypes.isNotEmpty()) {
                writeParam.compressionType = compressionTypes[0] // Use the first available type
            }

            writeParam.compressionQuality = quality // Set quality (0.0 - 1.0)
        }

        imageWriter.write(null, IIOImage(image, null, null), writeParam)
        outputStream.close()
        imageWriter.dispose()
    }

    fun getEFIXTransform(metadata: Metadata, originalImage: BufferedImage): AffineTransform {
        val directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
        val orientation = directory?.getInt(ExifIFD0Directory.TAG_ORIENTATION) ?: 1

        val transform = AffineTransform()

        when (orientation) {
            1 -> { //Top left -> Normal

            }
            2 -> { //Top right -> Mirror horizontally
                transform.scale(-1.0, 1.0)
                transform.translate(-originalImage.width.toDouble(), 0.0)
            }
            3 -> { //Bottom right -> Rotate 180,
                transform.translate(originalImage.width.toDouble(), originalImage.height.toDouble())
                transform.rotate(Math.toRadians(180.0))
            }
            4 -> { //Bottom left -> Mirror vertically
                transform.scale(1.0, -1.0)
                transform.translate(0.0, -originalImage.height.toDouble())
            }
            5 -> { //Left top -> Mirror horizontally and rotate 90 CW
                transform.scale(-1.0, 1.0)
                transform.translate(-originalImage.height.toDouble(), 0.0)
                transform.rotate(Math.toRadians(90.0))

            }
            6 -> { //Right top -> Rotate 90 CW
                transform.translate(originalImage.height.toDouble(), 0.0)
                transform.rotate(Math.toRadians(90.0))
            }
            7 -> { //Right bottom -> Mirror horizontally and rotate 90 CW
                transform.scale(-1.0, 1.0)
                transform.translate(-originalImage.height.toDouble(), -originalImage.width.toDouble())
                transform.rotate(Math.toRadians(270.0))
            }
            8 -> { //Left bottom -> Rotate 270 CW
                transform.translate(0.0, originalImage.width.toDouble())
                transform.rotate(Math.toRadians(270.0))
            }
        }

        return transform
    }


    private fun cropAndResizeImage(originalImage: BufferedImage, targetWidth: Int, targetHeight: Int, transform: AffineTransform): BufferedImage {
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
        graphics.transform = transform

        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        graphics.drawImage(croppedImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH), 0, 0, null)
        graphics.dispose()

        return resizedImage
    }

    fun resizeImageThumbnailator(inputImage: BufferedImage, targetWidth: Int, targetHeight: Int): BufferedImage {
        return Thumbnails.of(inputImage)
            .size(targetWidth, targetHeight)
            .outputQuality(1.0) // 100% quality
            .asBufferedImage()
    }




    private fun removeMetadata(image: BufferedImage): BufferedImage {
        val cleanedImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val graphics = cleanedImage.createGraphics()
        graphics.drawImage(image, 0, 0, null)
        graphics.dispose()
        return cleanedImage
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