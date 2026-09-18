package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable

/**
 * One line of text read off a scanned page, and where it sat on that page.
 *
 * The geometry is carried rather than dropped because the text alone is not enough to read a
 * recipe back: a cookbook page that puts its ingredients in a column beside its method
 * produces lines that interleave the two the moment they are flattened into a string, and no
 * amount of wording analysis recovers the order afterwards. [RecipeScan] uses the boxes to
 * find the gutter first and the words second. The other thing it reads off them is the
 * title, which is not identifiable by wording at all — it is simply the biggest text at the
 * top.
 *
 * Coordinates are normalised to the page: `0..1` on both axes, origin top-left, `y` growing
 * downwards. Neither platform hands them over that way — ML Kit gives pixels of a bitmap
 * whose size depends on the capture, Vision gives a bottom-left origin — so each `actual`
 * converts, and nothing above this file has to know which platform it is reading.
 *
 * [page] is the page the line came from. Both scanners are multi-page, and a column is a
 * property of one page: clustering across a two-page scan would put page two's left column
 * in with page one's.
 */
data class OcrLine(
    val text: String,
    val page: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val height: Float get() = bottom - top
}

/** What a finished scan produced. Cancelling reports nothing at all, as the pickers do. */
sealed interface ScanResult {
    /**
     * The lines that were read, in no particular order — ordering them is [RecipeScan]'s job,
     * and it needs the boxes to do it. Empty when the scan worked and the page held no text
     * this reader could make out, which is a different thing from [Failed] and gets a
     * different sentence.
     */
    data class Read(val lines: List<OcrLine>) : ScanResult

    /** The scanner or the text reader could not be reached at all. */
    data object Failed : ScanResult
}

fun interface RecipeScannerLauncher {
    fun launch()
}

/**
 * Remembers a launcher that opens the platform's document scanner and reads the text off
 * what it captured.
 *
 * Both platforms ship one, and they do the same job: frame the page, find its edges, correct
 * the perspective, let the user retake or crop, and hand back a flat image per page. Neither
 * reads text, so each `actual` runs the platform's own reader over the pages afterwards —
 * ML Kit Text Recognition on Android, Vision on iOS. Both are on-device: nothing about a
 * scan leaves the phone, which is also why there is no size bound here and no network error
 * to report.
 *
 * Separate from [rememberCameraCapture] rather than a mode on it, for the reason the camera
 * is separate from the library: a document scanner is a different system component with its
 * own failure mode — on Android it is delivered by Google Play services and is simply absent
 * without them. There is no fallback to the plain camera, because a photo taken at an angle
 * is exactly what this cannot read; a scan that cannot be done is reported as [ScanResult.Failed]
 * and the cook types the recipe in, which is what they were about to do anyway.
 *
 * Neither platform needs a permission granted for this, for the same reason the capture
 * intent does not: the scanner is another process with its own. iOS uses the
 * `NSCameraUsageDescription` already in `iosApp/iosApp/Info.plist`.
 */
@Composable
expect fun rememberRecipeScanner(onScanned: (ScanResult) -> Unit): RecipeScannerLauncher
