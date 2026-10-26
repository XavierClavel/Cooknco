<template>
  <div class="cropper">
    <div
      ref="stage"
      class="stage"
      tabindex="0"
      role="application"
      :aria-label="`${$t('crop_hint')}`"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointercancel="onPointerUp"
      @wheel.prevent="onWheel"
      @keydown="onKeydown"
    >
      <img
        :src="src"
        alt=""
        draggable="false"
        class="picture"
        :style="pictureStyle"
        @load="onLoad"
      />
      <div v-if="natural" class="frame" :class="{round}" :style="frameStyle"></div>
    </div>

    <div class="zoom">
      <button type="button" class="zoom-button" :aria-label="`${$t('crop_zoom_out')}`" @click="zoomBy(1 / ZOOM_STEP)">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 11h14v2H5z" /></svg>
      </button>
      <input
        type="range"
        min="1"
        :max="MAX_ZOOM"
        step="0.01"
        :value="view.zoom"
        :aria-label="`${$t('crop_zoom')}`"
        @input="setZoom(Number(($event.target as HTMLInputElement).value))"
      />
      <button type="button" class="zoom-button" :aria-label="`${$t('crop_zoom_in')}`" @click="zoomBy(ZOOM_STEP)">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6z" /></svg>
      </button>
    </div>
    <p class="hint">{{ $t('crop_hint') }}</p>
  </div>
</template>

<script setup lang="ts">
/**
 * The picture under a fixed frame, to be moved and zoomed until the frame holds the part to
 * keep — dragged, pinched, wheeled, or from the slider and the keyboard.
 *
 * It frames and reports, and nothing else: what it reports is `update:crop`, as fractions of
 * the picture (see `scripts/crop.ts`), and the dialog around it decides what confirming means.
 * That is what lets the site's editors and the backoffice put it in dialogs of their own.
 */
import {computed, ref, watch} from "vue";
import {useElementSize} from "@vueuse/core";
import {
  clampView,
  cropOf,
  type CropView,
  type ImageCrop,
  initialView,
  MAX_ZOOM,
  panned,
  scaleOf,
  viewOf,
  zoomed,
} from "@/scripts/crop";

const props = withDefaults(defineProps<{
  src: string
  /** The frame's width over its height: the ratio the picture is stored at. */
  aspectRatio: number
  /** Draws the frame as the circle an avatar is shown in. What is kept is still its square. */
  round?: boolean
  /** Where to open the frame, when the picture was framed before. */
  initialCrop?: ImageCrop | null
}>(), {round: false, initialCrop: null})

const emit = defineEmits<{
  (e: "update:crop", crop: ImageCrop): void
}>()

/** Room left around the frame, so the part of the picture being cut away stays in sight. */
const FRAME_MARGIN = 24
const ZOOM_STEP = 1.25
const KEY_PAN_PIXELS = 12

const stage = ref<HTMLElement | null>(null)
const {width: stageWidth, height: stageHeight} = useElementSize(stage)

/** The picture's size as shown — a browser has already turned it by its EXIF tag. */
const natural = ref<{width: number, height: number} | null>(null)
const view = ref<CropView>(initialView())

const frame = computed(() => {
  const availableWidth = Math.max(stageWidth.value - 2 * FRAME_MARGIN, 1)
  const availableHeight = Math.max(stageHeight.value - 2 * FRAME_MARGIN, 1)
  const width = Math.min(availableWidth, availableHeight * props.aspectRatio)
  const height = width / props.aspectRatio
  return {width, height, left: (stageWidth.value - width) / 2, top: (stageHeight.value - height) / 2}
})

const scale = computed(() => natural.value
  ? scaleOf(view.value, natural.value.width, natural.value.height, props.aspectRatio, frame.value.width)
  : 1)

const frameStyle = computed(() => ({
  left: `${frame.value.left}px`,
  top: `${frame.value.top}px`,
  width: `${frame.value.width}px`,
  height: `${frame.value.height}px`,
}))

const pictureStyle = computed(() => {
  // Hidden until its size is known, rather than flashing up at full size in the corner.
  if (!natural.value) return {visibility: "hidden" as const}
  const width = natural.value.width * scale.value
  const height = natural.value.height * scale.value
  const left = frame.value.left + frame.value.width / 2 - view.value.centerX * width
  const top = frame.value.top + frame.value.height / 2 - view.value.centerY * height
  return {width: `${width}px`, height: `${height}px`, transform: `translate(${left}px, ${top}px)`}
})

function onLoad(event: Event) {
  const img = event.target as HTMLImageElement
  natural.value = {width: img.naturalWidth, height: img.naturalHeight}
  view.value = props.initialCrop
    ? viewOf(props.initialCrop, img.naturalWidth, img.naturalHeight, props.aspectRatio)
    : initialView()
}

watch(() => props.src, () => { natural.value = null })

watch([view, natural, () => props.aspectRatio], () => {
  if (!natural.value) return
  emit("update:crop", cropOf(view.value, natural.value.width, natural.value.height, props.aspectRatio))
}, {immediate: true})

function pan(dx: number, dy: number) {
  if (!natural.value) return
  view.value = panned(view.value, dx, dy, scale.value, natural.value.width, natural.value.height, props.aspectRatio)
}

/** Zooms around a point given in client coordinates, or around the frame's centre. */
function zoomBy(factor: number, clientX?: number, clientY?: number) {
  if (!natural.value || !stage.value) return
  let dx = 0
  let dy = 0
  if (clientX !== undefined && clientY !== undefined) {
    const rect = stage.value.getBoundingClientRect()
    dx = clientX - (rect.left + frame.value.left + frame.value.width / 2)
    dy = clientY - (rect.top + frame.value.top + frame.value.height / 2)
  }
  view.value = zoomed(view.value, factor, dx, dy, scale.value, natural.value.width, natural.value.height, props.aspectRatio)
}

function setZoom(zoom: number) {
  if (!natural.value) return
  view.value = clampView({...view.value, zoom}, natural.value.width, natural.value.height, props.aspectRatio)
}

// ── Pointers: one drags, two pinch ─────────────────────────────────────────────

const pointers = new Map<number, {x: number, y: number}>()

function onPointerDown(event: PointerEvent) {
  stage.value?.setPointerCapture(event.pointerId)
  pointers.set(event.pointerId, {x: event.clientX, y: event.clientY})
}

function onPointerMove(event: PointerEvent) {
  const previous = pointers.get(event.pointerId)
  if (!previous) return
  const current = {x: event.clientX, y: event.clientY}
  if (pointers.size === 1) {
    pan(current.x - previous.x, current.y - previous.y)
  } else if (pointers.size === 2) {
    const other = [...pointers.entries()].find(([id]) => id !== event.pointerId)![1]
    const before = Math.hypot(previous.x - other.x, previous.y - other.y)
    const after = Math.hypot(current.x - other.x, current.y - other.y)
    // Zoom around where the fingers were, then follow where their midpoint went.
    if (before > 0) zoomBy(after / before, (previous.x + other.x) / 2, (previous.y + other.y) / 2)
    pan((current.x - previous.x) / 2, (current.y - previous.y) / 2)
  }
  pointers.set(event.pointerId, current)
}

function onPointerUp(event: PointerEvent) {
  pointers.delete(event.pointerId)
}

function onWheel(event: WheelEvent) {
  // A mouse wheel reports lines, a trackpad pixels.
  const delta = event.deltaMode === WheelEvent.DOM_DELTA_LINE ? event.deltaY * 16 : event.deltaY
  zoomBy(Math.exp(-delta * 0.002), event.clientX, event.clientY)
}

function onKeydown(event: KeyboardEvent) {
  const moves: Record<string, [number, number]> = {
    ArrowLeft: [KEY_PAN_PIXELS, 0],
    ArrowRight: [-KEY_PAN_PIXELS, 0],
    ArrowUp: [0, KEY_PAN_PIXELS],
    ArrowDown: [0, -KEY_PAN_PIXELS],
  }
  if (moves[event.key]) pan(...moves[event.key])
  else if (event.key === "+" || event.key === "=") zoomBy(ZOOM_STEP)
  else if (event.key === "-") zoomBy(1 / ZOOM_STEP)
  else return
  event.preventDefault()
}
</script>

<style scoped>
.stage {
  position: relative;
  height: min(60vh, 440px);
  overflow: hidden;
  border-radius: 8px;
  background: #0d1821;
  cursor: grab;
  touch-action: none;
  user-select: none;
  outline: none;
}
.stage:active {
  cursor: grabbing;
}
.stage:focus-visible {
  box-shadow: 0 0 0 3px #f4b942;
}
.picture {
  position: absolute;
  left: 0;
  top: 0;
  max-width: none;
  pointer-events: none;
  user-select: none;
}
/* Everything outside the frame is dimmed by the frame's own shadow, which follows its shape. */
.frame {
  position: absolute;
  pointer-events: none;
  border: 2px solid #ffffff;
  box-shadow: 0 0 0 100vmax rgba(13, 24, 33, 0.65);
}
/* The thirds, to place what matters on them. */
.frame::before,
.frame::after {
  content: "";
  position: absolute;
  border: 0 solid rgba(255, 255, 255, 0.35);
}
.frame::before {
  inset: 0 33.333%;
  border-left-width: 1px;
  border-right-width: 1px;
}
.frame::after {
  inset: 33.333% 0;
  border-top-width: 1px;
  border-bottom-width: 1px;
}
.frame.round {
  border-radius: 50%;
}
.frame.round::before,
.frame.round::after {
  display: none;
}
.zoom {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
}
.zoom input {
  flex: 1;
  accent-color: #0d1821;
}
.zoom-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 2px solid #0d1821;
  border-radius: 50%;
  background: #ffffff;
  color: #0d1821;
  cursor: pointer;
}
.zoom-button svg {
  width: 18px;
  height: 18px;
  fill: currentColor;
}
.hint {
  margin: 8px 0 0;
  font-size: 0.85rem;
  opacity: 0.7;
}
</style>
