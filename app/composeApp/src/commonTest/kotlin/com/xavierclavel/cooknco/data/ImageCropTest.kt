package com.xavierclavel.cooknco.data

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The arithmetic under the crop dialog: what the frame covers as the picture is dragged and
 * pinched under it. The dialog itself cannot be driven on this machine; what it reports can.
 */
class ImageCropTest {

    private val recipe = CropFrame.RECIPE.aspectRatio

    private fun assertClose(expected: Double, actual: Double, message: String? = null) =
        assertTrue(abs(expected - actual) < 1e-9, "${message ?: ""} expected $expected, was $actual")

    private fun assertOnPicture(crop: ImageCrop) {
        assertTrue(crop.left >= 0 && crop.top >= 0, "$crop starts off the picture")
        assertTrue(crop.left + crop.width <= 1 + 1e-9 && crop.top + crop.height <= 1 + 1e-9, "$crop ends off it")
    }

    /** A picture confirmed untouched must be stored exactly as it was before there was a dialog. */
    @Test
    fun untouched_the_frame_is_the_centred_crop_the_backend_makes_on_its_own() {
        // Twice as wide as tall: the 4:3 middle, 800 of its 1200 pixels.
        val wide = CropView().cropOf(1200.0, 600.0, recipe)
        assertClose(1.0 / 6, wide.left)
        assertClose(0.0, wide.top)
        assertClose(2.0 / 3, wide.width)
        assertClose(1.0, wide.height)

        // Twice as tall as wide: all of its width, and the 450 pixels in the middle of its height.
        val tall = CropView().cropOf(600.0, 1200.0, recipe)
        assertClose(0.0, tall.left)
        assertClose(0.3125, tall.top)
        assertClose(1.0, tall.width)
        assertClose(0.375, tall.height)
    }

    @Test
    fun the_frame_keeps_the_ratio_it_is_stored_at_however_far_it_is_zoomed() {
        listOf(1.0, 1.7, 3.0, CropView.MAX_ZOOM).forEach { zoom ->
            val crop = CropView(zoom = zoom).cropOf(4000.0, 3000.0, recipe)
            assertClose(recipe, crop.width * 4000 / (crop.height * 3000), "at zoom $zoom")
            assertClose(1 / zoom, crop.width, "at zoom $zoom")
        }
        val avatar = CropView(zoom = 2.0).cropOf(3000.0, 4000.0, CropFrame.USER.aspectRatio)
        assertClose(1.0, avatar.width * 3000 / (avatar.height * 4000))
    }

    /** The backend refuses a crop that leaves the picture, so nothing a finger does may produce one. */
    @Test
    fun the_frame_never_leaves_the_picture() {
        val width = 4000.0
        val height = 3000.0
        val scale = CropView().scale(width, height, recipe, frameWidth = 360.0)

        val draggedFar = CropView(zoom = 2.0).panned(-10_000.0, 10_000.0, scale, width, height, recipe)
        assertOnPicture(draggedFar.cropOf(width, height, recipe))
        assertClose(0.5, draggedFar.cropOf(width, height, recipe).left, "pushed against the right edge")

        val pinchedOut = CropView().zoomed(0.2, 150.0, -80.0, scale, width, height, recipe)
        assertEquals(1.0, pinchedOut.zoom, "no further out than the picture fills")
        assertOnPicture(pinchedOut.cropOf(width, height, recipe))

        val pinchedIn = CropView().zoomed(40.0, 0.0, 0.0, scale, width, height, recipe)
        assertEquals(CropView.MAX_ZOOM, pinchedIn.zoom)
    }

    /** The zoom buttons multiply by a step and leave stopping at either end to the view. */
    @Test
    fun the_zoom_buttons_stop_at_either_end() {
        val width = 4000.0
        val height = 3000.0

        val pastTheEnd = CropView(zoom = CropView.MAX_ZOOM * 1.25).clamped(width, height, recipe)
        assertEquals(CropView.MAX_ZOOM, pastTheEnd.zoom)

        val pastTheStart = CropView(zoom = 0.8)
        assertEquals(1.0, pastTheStart.clamped(width, height, recipe).zoom)
        assertOnPicture(pastTheStart.cropOf(width, height, recipe))
    }

    @Test
    fun a_drag_moves_the_picture_with_the_finger() {
        val width = 4000.0
        val height = 3000.0
        val view = CropView(zoom = 2.0)
        val scale = view.scale(width, height, recipe, frameWidth = 300.0)

        // Dragged to the right, the picture moves right: the frame lands further left on it.
        val dragged = view.panned(60.0, 0.0, scale, width, height, recipe)
        assertClose(view.centerX - 60 / (scale * width), dragged.centerX)
        assertClose(view.centerY, dragged.centerY)
    }

    /** What makes a pinch feel held: the spot between the fingers stays under them as the picture grows. */
    @Test
    fun a_pinch_keeps_the_point_between_the_fingers_where_it_was() {
        val width = 4000.0
        val height = 3000.0
        val view = CropView(zoom = 2.0, centerX = 0.5, centerY = 0.5)
        val frameWidth = 300.0
        val scale = view.scale(width, height, recipe, frameWidth)
        val dx = 40.0
        val dy = -25.0

        val zoomed = view.zoomed(1.5, dx, dy, scale, width, height, recipe)
        val zoomedScale = zoomed.scale(width, height, recipe, frameWidth)

        assertClose(3.0, zoomed.zoom)
        assertClose(view.centerX * width + dx / scale, zoomed.centerX * width + dx / zoomedScale, "across")
        assertClose(view.centerY * height + dy / scale, zoomed.centerY * height + dy / zoomedScale, "down")
    }

    @Test
    fun the_query_value_is_four_fractions_at_six_decimals() {
        assertEquals("0.166667,0.000000,0.666667,1.000000", ImageCrop(1.0 / 6, 0.0, 2.0 / 3, 1.0).toQueryValue())
        assertEquals("0.000001,0.250000,0.500000,0.375000", ImageCrop(0.0000005, 0.25, 0.5, 0.375).toQueryValue())
    }
}
