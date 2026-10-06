package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable

/** One photographed page, ready to post: a JPEG already scaled down. */
class CapturedPage(val bytes: ByteArray, val mimeType: String = "image/jpeg")

/** What a finished capture produced. Cancelling reports nothing, as the scanner does. */
sealed interface PhotoCaptureResult {
    data class Captured(val pages: List<CapturedPage>) : PhotoCaptureResult

    /** The scanner could not be reached, or a page could not be read off it. */
    data object Failed : PhotoCaptureResult
}

/**
 * The longest edge a page is sent at, in pixels.
 *
 * Every provider resizes a picture to about 1.3 megapixels before the model sees it, so a
 * 12-megapixel page is several megabytes uploaded over a phone connection for nothing. This
 * keeps body text legible — a printed page at this size reads fine — at a few hundred
 * kilobytes a page.
 */
const val RECIPE_SCAN_MAX_EDGE = 1600

/** JPEG quality of a sent page: text survives it, and the size halves against 95. */
const val RECIPE_SCAN_JPEG_QUALITY = 85

/**
 * Remembers a launcher that opens the same document scanner as [rememberRecipeScanner] and
 * hands back the pages as pictures instead of reading them on the phone.
 *
 * It is the premium import's camera: the pages are posted to the backend, which has a vision
 * model read them (`POST /recipe/scan`). So unlike the scan, **this one leaves the
 * phone** — which is why it is a separate launcher rather than a flag on the scanner, whose
 * whole promise is that nothing does. A screen that offers both offers them as two choices.
 *
 * The scanner rather than the plain camera for the reason the scan uses it: it finds the
 * page's edges and flattens the perspective, and a flat, cropped page is what a model reads
 * best and what costs the fewest tokens.
 */
@Composable
expect fun rememberRecipePhotoCapture(onCaptured: (PhotoCaptureResult) -> Unit): RecipeScannerLauncher
