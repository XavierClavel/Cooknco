/**
 * Framing a picture before it is uploaded.
 *
 * The cook moves and zooms the picture under a fixed frame, and what is sent is the part the
 * frame covers — as fractions of the picture, `?crop=left,top,width,height` — for the backend
 * to cut out (`ImageCrop` there). Fractions of the picture *as shown*: a browser turns an
 * `<img>` by its EXIF tag, and the backend applies the same tag before it crops.
 *
 * The frame never leaves the picture. Zoomed all the way out it spans the picture's shorter
 * side, which is exactly the centred crop the backend makes when it is sent none, so a
 * picture confirmed untouched is stored as it always was.
 */

/** Part of a picture, as fractions of its width and height. */
export interface ImageCrop {
  left: number
  top: number
  width: number
  height: number
}

/**
 * Where the frame is on the picture: how far it is zoomed in from the widest framing the
 * picture can fill, and the point of the picture under the frame's centre, as fractions.
 *
 * Nothing in it depends on how large the picture is drawn, so a stage that resizes, or a
 * dialog reopened at another width, keeps the framing.
 */
export interface CropView {
  zoom: number
  centerX: number
  centerY: number
}

/** A fifth of each side: past that, a phone photograph is down to a few hundred pixels. */
export const MAX_ZOOM = 5

export const initialView = (): CropView => ({zoom: 1, centerX: 0.5, centerY: 0.5})

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), max)

/** The frame at zoom 1, as fractions of a [width] x [height] picture: one side, whole. */
function baseSize(width: number, height: number, aspectRatio: number) {
  const pictureRatio = width / height
  return pictureRatio > aspectRatio
    ? {width: aspectRatio / pictureRatio, height: 1}
    : {width: 1, height: pictureRatio / aspectRatio}
}

/** [view] brought back to a zoom it may have and a frame that stays on the picture. */
export function clampView(view: CropView, width: number, height: number, aspectRatio: number): CropView {
  const zoom = clamp(view.zoom, 1, MAX_ZOOM)
  const base = baseSize(width, height, aspectRatio)
  const halfWidth = base.width / zoom / 2
  const halfHeight = base.height / zoom / 2
  return {
    zoom,
    centerX: clamp(view.centerX, halfWidth, 1 - halfWidth),
    centerY: clamp(view.centerY, halfHeight, 1 - halfHeight),
  }
}

/** What the frame covers. */
export function cropOf(view: CropView, width: number, height: number, aspectRatio: number): ImageCrop {
  const clamped = clampView(view, width, height, aspectRatio)
  const base = baseSize(width, height, aspectRatio)
  const cropWidth = base.width / clamped.zoom
  const cropHeight = base.height / clamped.zoom
  return {
    left: Math.max(0, clamped.centerX - cropWidth / 2),
    top: Math.max(0, clamped.centerY - cropHeight / 2),
    width: cropWidth,
    height: cropHeight,
  }
}

/** The view that frames [crop] again, to reopen the cropper where it was left. */
export function viewOf(crop: ImageCrop, width: number, height: number, aspectRatio: number): CropView {
  const base = baseSize(width, height, aspectRatio)
  return clampView({
    zoom: base.width / crop.width,
    centerX: crop.left + crop.width / 2,
    centerY: crop.top + crop.height / 2,
  }, width, height, aspectRatio)
}

/** Screen pixels per picture pixel, for a frame drawn [frameWidth] pixels wide. */
export function scaleOf(view: CropView, width: number, height: number, aspectRatio: number, frameWidth: number): number {
  return frameWidth / (baseSize(width, height, aspectRatio).width / view.zoom * width)
}

/** [view] after a drag of ([dx], [dy]) screen pixels: the picture follows the pointer. */
export function panned(
  view: CropView, dx: number, dy: number, scale: number,
  width: number, height: number, aspectRatio: number,
): CropView {
  return clampView({
    ...view,
    centerX: view.centerX - dx / (scale * width),
    centerY: view.centerY - dy / (scale * height),
  }, width, height, aspectRatio)
}

/**
 * [view] zoomed by [factor], keeping the point of the picture at ([dx], [dy]) — a screen offset
 * from the frame's centre — where it is on screen: under the wheel's pointer, or between the
 * pinching fingers.
 */
export function zoomed(
  view: CropView, factor: number, dx: number, dy: number, scale: number,
  width: number, height: number, aspectRatio: number,
): CropView {
  const zoom = clamp(view.zoom * factor, 1, MAX_ZOOM)
  const newScale = scale * zoom / view.zoom
  const focusX = view.centerX * width + dx / scale
  const focusY = view.centerY * height + dy / scale
  return clampView({
    zoom,
    centerX: (focusX - dx / newScale) / width,
    centerY: (focusY - dy / newScale) / height,
  }, width, height, aspectRatio)
}

/**
 * The query value the backend reads. Six decimals: a millionth of a 12-megapixel photograph's
 * width is well under a pixel, and the backend tolerates the rounding at the edges.
 */
export const cropParam = (crop: ImageCrop) =>
  [crop.left, crop.top, crop.width, crop.height].map(value => value.toFixed(6)).join(',')
