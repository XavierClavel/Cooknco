package com.xavierclavel.cooknco.data

import kotlin.math.max
import kotlin.math.roundToLong

/**
 * The part of a picture to keep, as fractions of its width and height.
 *
 * Sent beside the picture as `?crop=left,top,width,height`, and cut out by the backend
 * (`ImageCrop` there) — so the photograph goes up as it was picked and nothing here re-encodes
 * it. Fractions of the picture **as shown**, after its EXIF rotation: that is what Coil decodes
 * for the cropper to draw, and what the backend turns the upload into before it crops. And
 * fractions rather than pixels, because the cropper draws a downsampled copy and the frame it
 * reports has to mean the same thing on the original.
 */
data class ImageCrop(val left: Double, val top: Double, val width: Double, val height: Double) {

    /**
     * The query value the backend reads. Six decimals: a millionth of a 12-megapixel
     * photograph's width is well under a pixel, and the backend tolerates the rounding at the
     * edges.
     */
    fun toQueryValue(): String = listOf(left, top, width, height).joinToString(",") { it.sixDecimals() }
}

/** Common code has no `String.format`; every value here is a fraction, so never negative. */
private fun Double.sixDecimals(): String {
    val millionths = (coerceAtLeast(0.0) * 1_000_000).roundToLong()
    return "${millionths / 1_000_000}.${(millionths % 1_000_000).toString().padStart(6, '0')}"
}

/**
 * The frame a picture is stored in, which is the frame it is cropped under.
 *
 * Restates the ratios of the backend's `ImageBucket`, which this build cannot read: `shared` is
 * JVM-only. A ratio drifting from it costs a sliver, not a failure — the backend still centres
 * whatever it is sent into its own frame.
 */
enum class CropFrame(val aspectRatio: Double, val round: Boolean) {
    /** 1600x1200, and the 480x360 thumbnail cut from the same upload. */
    RECIPE(4.0 / 3.0, round = false),
    /** 1000x750. */
    RECIPE_STEP(4.0 / 3.0, round = false),
    /** 400x400, drawn in a circle everywhere it is shown — so it is framed in one. */
    USER(1.0, round = true),
    /** 500x500. */
    COOKBOOK(1.0, round = false),
}

/**
 * Where the frame is on the picture: how far it is zoomed in from the widest framing the picture
 * can fill, and the point of the picture under the frame's centre, as fractions.
 *
 * Nothing in it depends on how large the picture is drawn, so a rotation or a resize keeps the
 * framing. Zoomed all the way out, the frame spans the picture's shorter side, which is exactly
 * the centred crop the backend makes when it is sent none.
 *
 * The same arithmetic as the site's `scripts/crop.ts`, which frames the same buckets.
 */
data class CropView(val zoom: Double = 1.0, val centerX: Double = 0.5, val centerY: Double = 0.5) {

    /** This view brought back to a zoom it may have and a frame that stays on the picture. */
    fun clamped(width: Double, height: Double, aspectRatio: Double): CropView {
        val clampedZoom = zoom.coerceIn(1.0, MAX_ZOOM)
        val (baseWidth, baseHeight) = baseSize(width, height, aspectRatio)
        val halfWidth = baseWidth / clampedZoom / 2
        val halfHeight = baseHeight / clampedZoom / 2
        return CropView(
            zoom = clampedZoom,
            centerX = centerX.coerceIn(halfWidth, 1 - halfWidth),
            centerY = centerY.coerceIn(halfHeight, 1 - halfHeight),
        )
    }

    /** What the frame covers on a [width] x [height] picture. */
    fun cropOf(width: Double, height: Double, aspectRatio: Double): ImageCrop {
        val view = clamped(width, height, aspectRatio)
        val (baseWidth, baseHeight) = baseSize(width, height, aspectRatio)
        val cropWidth = baseWidth / view.zoom
        val cropHeight = baseHeight / view.zoom
        return ImageCrop(
            left = max(0.0, view.centerX - cropWidth / 2),
            top = max(0.0, view.centerY - cropHeight / 2),
            width = cropWidth,
            height = cropHeight,
        )
    }

    /** Screen pixels per picture pixel, for a frame drawn [frameWidth] pixels wide. */
    fun scale(width: Double, height: Double, aspectRatio: Double, frameWidth: Double): Double =
        frameWidth / (baseSize(width, height, aspectRatio).first / zoom * width)

    /** This view after a drag of ([dx], [dy]) screen pixels at [scale]: the picture follows the finger. */
    fun panned(dx: Double, dy: Double, scale: Double, width: Double, height: Double, aspectRatio: Double): CropView =
        copy(centerX = centerX - dx / (scale * width), centerY = centerY - dy / (scale * height))
            .clamped(width, height, aspectRatio)

    /**
     * This view zoomed by [factor], keeping the point of the picture at ([dx], [dy]) — a screen
     * offset from the frame's centre — where it is on screen: between the pinching fingers.
     */
    fun zoomed(
        factor: Double, dx: Double, dy: Double, scale: Double,
        width: Double, height: Double, aspectRatio: Double,
    ): CropView {
        val newZoom = (zoom * factor).coerceIn(1.0, MAX_ZOOM)
        val newScale = scale * newZoom / zoom
        val focusX = centerX * width + dx / scale
        val focusY = centerY * height + dy / scale
        return CropView(
            zoom = newZoom,
            centerX = (focusX - dx / newScale) / width,
            centerY = (focusY - dy / newScale) / height,
        ).clamped(width, height, aspectRatio)
    }

    companion object {
        /** A fifth of each side: past that, a phone photograph is down to a few hundred pixels. */
        const val MAX_ZOOM = 5.0

        /** The frame at zoom 1, as fractions of the picture: one side, whole. */
        private fun baseSize(width: Double, height: Double, aspectRatio: Double): Pair<Double, Double> {
            val pictureRatio = width / height
            return if (pictureRatio > aspectRatio) Pair(aspectRatio / pictureRatio, 1.0)
            else Pair(1.0, pictureRatio / aspectRatio)
        }
    }
}
