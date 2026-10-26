package com.xavierclavel.utils

import com.drew.metadata.Metadata
import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

/**
 * A picture as it was posted: its pixels, what its metadata says about how to hold them up,
 * and the part of it the uploader chose to keep.
 *
 * One value rather than three so that whatever saves a picture is handed the crop along with
 * it — a route that unpacked the pair it used to get and dropped the third would store the
 * centred frame instead, which looks like a picture and is not the one anybody chose.
 */
data class UploadedImage(val image: BufferedImage, val metadata: Metadata, val crop: ImageCrop?)

/**
 * The part of a picture to keep, as fractions of the picture **as it is shown** — after its
 * EXIF orientation is applied, not as its pixels are stored. That is the picture both clients
 * put under the crop frame (a browser rotates an `<img>` the same way), so a rectangle read off
 * the stored pixels would land somewhere else on every phone photograph taken upright.
 *
 * Fractions rather than pixels because neither client holds the original: the app decodes a
 * downsampled copy to draw, and the frame it reports is only the same frame if it does not
 * depend on how many pixels that copy had.
 *
 * The crop does not have to carry the bucket's ratio exactly — rounding alone would see to
 * that — because the picture is still centred into its frame afterwards; it only has to be
 * close enough that what that trims is a pixel, not a part of the picture.
 */
data class ImageCrop(val left: Double, val top: Double, val width: Double, val height: Double) {

    /** The same region in [image]'s pixels, never empty and never past its edges. */
    fun applyTo(image: BufferedImage): BufferedImage {
        val x = (left * image.width).roundToInt().coerceIn(0, image.width - 1)
        val y = (top * image.height).roundToInt().coerceIn(0, image.height - 1)
        val right = ((left + width) * image.width).roundToInt().coerceIn(x + 1, image.width)
        val bottom = ((top + height) * image.height).roundToInt().coerceIn(y + 1, image.height)
        return image.getSubimage(x, y, right - x, bottom - y)
    }

    companion object {
        /**
         * What a client may overshoot the picture by.
         *
         * A frame pushed against an edge comes back as `0.25 + 0.75000001` once it has been
         * through a float and a decimal string, and that is still a frame against the edge.
         */
        private const val TOLERANCE = 1e-3

        /**
         * `left,top,width,height`, or a refusal.
         *
         * A crop that leaves the picture is refused rather than clamped into it: it is a
         * client measuring against some other picture, and what it would keep is not what
         * the person in front of it saw.
         */
        fun parse(value: String): ImageCrop {
            val parts = value.split(",").map { it.trim().toDoubleOrNull() }
            if (parts.size != 4 || parts.any { it == null || !it.isFinite() }) throw invalid()
            val (left, top, width, height) = parts.map { it!! }
            if (left < 0 || top < 0 || width <= 0 || height <= 0) throw invalid()
            if (left + width > 1 + TOLERANCE || top + height > 1 + TOLERANCE) throw invalid()
            return ImageCrop(left, top, width, height)
        }

        private fun invalid() = BadRequestException(BadRequestCause.INVALID_IMAGE_CROP)
    }
}
