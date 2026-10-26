package com.xavierclavel.cooknco.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.xavierclavel.cooknco.data.CropFrame
import com.xavierclavel.cooknco.data.CropView
import com.xavierclavel.cooknco.data.ImageCrop
import com.xavierclavel.cooknco.platform.PickedImage
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoGold
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerIconButton
import kotlin.math.max

/**
 * Sends every picture through [ImageCropDialog] on its way to [onFramed], and shows the dialog
 * while there is one to frame. What it returns is what the pickers are handed instead of
 * [onFramed] — the photo library and the camera alike, so neither way in skips the frame:
 *
 * ```
 * val frameImage = rememberImageFraming(CropFrame.RECIPE, onFramed = onImagePicked)
 * val imagePicker = rememberImagePicker(onPicked = frameImage)
 * ```
 *
 * Cancelling the dialog drops the picture, and whatever was there before stays.
 */
@Composable
fun rememberImageFraming(frame: CropFrame, onFramed: (PickedImage) -> Unit): (PickedImage) -> Unit {
    var toFrame by remember { mutableStateOf<PickedImage?>(null) }
    val latestOnFramed by rememberUpdatedState(onFramed)
    toFrame?.let { picked ->
        ImageCropDialog(
            image = picked,
            frame = frame,
            onCropped = { crop ->
                toFrame = null
                latestOnFramed(picked.framed(crop))
            },
            onDismissRequest = { toFrame = null },
        )
    }
    return remember { { picked: PickedImage -> toFrame = picked } }
}

/** Large enough to zoom into, small enough not to hold a 12-megapixel bitmap for a dialog. */
private const val CROP_DECODE_PX = 2048

/** Room left around the frame, so the part of the picture being cut away stays in sight. */
private val FRAME_MARGIN = 20.dp

private const val ZOOM_STEP = 1.25

/**
 * The picture under the frame it is stored in, moved by dragging and zoomed by pinching or with
 * the slider, full screen.
 *
 * [onCropped] receives what the frame covers. Null if the picture could not be drawn here — a
 * format the platform's decoder does not know — in which case there was nothing to frame by, and
 * the backend's centring is the honest fallback; the dialog says so rather than failing the pick.
 */
@Composable
fun ImageCropDialog(
    image: PickedImage,
    frame: CropFrame,
    onCropped: (ImageCrop?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val s = strings()
    val context = LocalPlatformContext.current
    val painter = rememberAsyncImagePainter(
        model = remember(image) { ImageRequest.Builder(context).data(image.bytes).size(CROP_DECODE_PX).build() },
    )
    val painterState by painter.state.collectAsState()
    // As decoded, which is upright: Coil turns a photograph by its EXIF tag on both platforms,
    // and that is the picture the backend crops.
    val natural = (painterState as? AsyncImagePainter.State.Success)?.painter?.intrinsicSize
        ?.takeIf { it.isSpecified && it.width > 0f && it.height > 0f }
    val failed = painterState is AsyncImagePainter.State.Error
    var view by remember(image) { mutableStateOf(CropView()) }

    fun setZoom(zoom: Double) {
        val size = natural ?: return
        view = view.copy(zoom = zoom).clamped(size.width.toDouble(), size.height.toDouble(), frame.aspectRatio)
    }

    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier.fillMaxSize().background(CookncoNavy).safeDrawingPadding(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp)) {
                Text(s.cropTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CookncoWhite)
                Text(
                    text = if (failed) s.cropUnavailable else s.cropHint,
                    fontSize = 13.5.sp,
                    color = CookncoWhite.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CropStage(
                    painter = painter,
                    natural = natural,
                    frame = frame,
                    view = view,
                    onViewChange = { view = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StickerIconButton(
                    onClick = { setZoom(view.zoom / ZOOM_STEP) },
                    size = 40.dp,
                    shape = CircleShape,
                    fillColor = CookncoWhite,
                    shadowColor = CookncoGold,
                ) { Icon(Icons.Outlined.Remove, contentDescription = s.cropZoomOut) }
                Slider(
                    value = view.zoom.toFloat(),
                    onValueChange = { setZoom(it.toDouble()) },
                    valueRange = 1f..CropView.MAX_ZOOM.toFloat(),
                    enabled = natural != null,
                    colors = SliderDefaults.colors(
                        thumbColor = CookncoGold,
                        activeTrackColor = CookncoGold,
                        inactiveTrackColor = CookncoWhite.copy(alpha = 0.3f),
                    ),
                    modifier = Modifier.weight(1f),
                )
                StickerIconButton(
                    onClick = { setZoom(view.zoom * ZOOM_STEP) },
                    size = 40.dp,
                    shape = CircleShape,
                    fillColor = CookncoWhite,
                    shadowColor = CookncoGold,
                ) { Icon(Icons.Outlined.Add, contentDescription = s.cropZoomIn) }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StickerCard(
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    fillColor = CookncoWhite,
                    shadowColor = CookncoGold,
                    shadowOffset = 4.dp,
                    onClick = onDismissRequest,
                ) {
                    Text(
                        text = s.cancel,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                val ready = natural != null || failed
                StickerCard(
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    fillColor = if (ready) CookncoGold else CookncoGold.copy(alpha = 0.5f),
                    shadowColor = CookncoWhite,
                    shadowOffset = 4.dp,
                    onClick = if (ready) {
                        { onCropped(natural?.let { view.cropOf(it.width.toDouble(), it.height.toDouble(), frame.aspectRatio) }) }
                    } else null,
                ) {
                    Text(
                        text = s.cropConfirm,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * The picture drawn at [view], the frame over it, and everything outside the frame dimmed.
 * One drag moves it and two fingers zoom it, around the point between them.
 */
@Composable
private fun CropStage(
    painter: Painter,
    natural: Size?,
    frame: CropFrame,
    view: CropView,
    onViewChange: (CropView) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Read at gesture time: the detector is started once per picture, not once per frame.
    val currentView by rememberUpdatedState(view)
    val currentOnViewChange by rememberUpdatedState(onViewChange)
    val ratio = frame.aspectRatio

    Canvas(
        modifier = modifier
            .clipToBounds()
            .pointerInput(natural) {
                val picture = natural ?: return@pointerInput
                val width = picture.width.toDouble()
                val height = picture.height.toDouble()
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val rect = frameRect(Size(size.width.toFloat(), size.height.toFloat()), FRAME_MARGIN.toPx(), ratio)
                    val scale = currentView.scale(width, height, ratio, rect.width.toDouble())
                    val zoomed = currentView.zoomed(
                        zoom.toDouble(),
                        (centroid.x - rect.center.x).toDouble(),
                        (centroid.y - rect.center.y).toDouble(),
                        scale, width, height, ratio,
                    )
                    val zoomedScale = zoomed.scale(width, height, ratio, rect.width.toDouble())
                    currentOnViewChange(zoomed.panned(pan.x.toDouble(), pan.y.toDouble(), zoomedScale, width, height, ratio))
                }
            },
    ) {
        val rect = frameRect(size, FRAME_MARGIN.toPx(), ratio)
        if (natural != null) {
            val scale = view.scale(natural.width.toDouble(), natural.height.toDouble(), ratio, rect.width.toDouble()).toFloat()
            val drawn = Size(natural.width * scale, natural.height * scale)
            translate(
                left = rect.center.x - view.centerX.toFloat() * drawn.width,
                top = rect.center.y - view.centerY.toFloat() * drawn.height,
            ) {
                with(painter) { draw(drawn) }
            }
        }
        drawFrame(rect, frame.round)
    }
}

/** The largest [ratio] rectangle that fits in [stage] with [margin] around it, centred. */
private fun frameRect(stage: Size, margin: Float, ratio: Double): Rect {
    val availableWidth = max(stage.width - 2 * margin, 1f)
    val availableHeight = max(stage.height - 2 * margin, 1f)
    val width = minOf(availableWidth, availableHeight * ratio.toFloat())
    val height = width / ratio.toFloat()
    return Rect(Offset((stage.width - width) / 2, (stage.height - height) / 2), Size(width, height))
}

private fun DrawScope.drawFrame(rect: Rect, round: Boolean) {
    val hole = Path().apply { if (round) addOval(rect) else addRect(rect) }
    val stage = Path().apply { addRect(Rect(Offset.Zero, size)) }
    drawPath(Path.combine(PathOperation.Difference, stage, hole), CookncoNavy.copy(alpha = 0.65f))

    val stroke = Stroke(width = 2.dp.toPx())
    if (round) {
        drawOval(CookncoWhite, rect.topLeft, rect.size, style = stroke)
        return
    }
    // The thirds, to place what matters on them.
    val guide = CookncoWhite.copy(alpha = 0.35f)
    val hairline = 1.dp.toPx()
    for (i in 1..2) {
        val x = rect.left + rect.width * i / 3
        val y = rect.top + rect.height * i / 3
        drawLine(guide, Offset(x, rect.top), Offset(x, rect.bottom), hairline)
        drawLine(guide, Offset(rect.left, y), Offset(rect.right, y), hairline)
    }
    drawRect(CookncoWhite, rect.topLeft, rect.size, style = stroke)
}

/** Small enough for a form's preview; the dialog decodes its own, larger copy. */
private const val PREVIEW_DECODE_PX = 1024

/**
 * A picked picture as its crop left it, filling the box — the preview an editor shows until
 * the picture is saved.
 *
 * Drawn from the original with the crop applied rather than from a cropped copy, so nothing is
 * re-encoded for a preview either. A box of another ratio than the crop's is covered the way
 * [ContentScale.Crop] covers it, around the crop's centre.
 */
@Composable
fun FramedImage(image: PickedImage, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalPlatformContext.current
    val painter = rememberAsyncImagePainter(
        model = remember(image) { ImageRequest.Builder(context).data(image.bytes).size(PREVIEW_DECODE_PX).build() },
    )
    val crop = image.crop
    if (crop == null) {
        Image(painter, contentDescription, contentScale = ContentScale.Crop, modifier = modifier)
        return
    }
    Canvas(
        modifier = modifier
            .clipToBounds()
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
    ) {
        val natural = painter.intrinsicSize
        if (!natural.isSpecified || natural.width <= 0f || natural.height <= 0f) return@Canvas
        val scale = max(
            size.width / (crop.width.toFloat() * natural.width),
            size.height / (crop.height.toFloat() * natural.height),
        )
        val drawn = Size(natural.width * scale, natural.height * scale)
        translate(
            left = size.width / 2 - (crop.left + crop.width / 2).toFloat() * drawn.width,
            top = size.height / 2 - (crop.top + crop.height / 2).toFloat() * drawn.height,
        ) {
            with(painter) { draw(drawn) }
        }
    }
}
