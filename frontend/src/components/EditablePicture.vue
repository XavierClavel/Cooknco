<template>
  <input ref="fileInput" type="file" accept="image/*" hidden @change="onFilePicked" />

  <v-img
    :src="imageUrl"
    :width="width"
    :aspect-ratio="aspectRatio"
    cover
    color="surface-variant"
    class="preview-image my-6 mx-auto"
    :rounded="rounded"
    @click="triggerFileInput"
    @load="handleImageLoad"
    @error="handleImageError"
    style="border: 4px solid #0d1821 !important;"
  >
    <!-- The picture as the frame left it: the file is uploaded whole, with the crop beside it -->
    <div v-if="previewCrop" class="crop-preview">
      <img :src="imageUrl" alt="" :style="previewStyle" />
    </div>
    <v-container :class="`image-overlay d-flex flex-row ${buttonsSpacing}`"  >
      <v-btn
        :width="buttonsSize"
        :height="buttonsSize"
        :rounded="buttonsRounded"
        color="primary"
        icon="mdi-pencil"
        :class="`${buttonsIconSize} overlay-button`"
      ></v-btn>
      <v-btn
        :width="buttonsSize"
        :height="buttonsSize"
        :rounded="buttonsRounded"
        color="primary"
        icon="mdi-crop"
        :title="`${$t('crop_adjust')}`"
        :class="`${buttonsIconSize} overlay-button`"
        v-if="previewCrop"
        @click.stop="reframe"
      ></v-btn>
      <v-btn
        :width="buttonsSize"
        :height="buttonsSize"
        :rounded="buttonsRounded"
        color="primary"
        icon="mdi-arrow-u-left-top"
        :class="`${buttonsIconSize} overlay-button`"
        v-if="imageUpdated"
        @click.stop="undoImageChange"
      ></v-btn>
      <v-btn
        :width="buttonsSize"
        :height="buttonsSize"
        :rounded="buttonsRounded"
        color="primary"
        icon="mdi-close"
        :class="`${buttonsIconSize} overlay-button`"
        v-if="hasImage && !imageDeleted"
        @click.stop="deleteImage"
      ></v-btn>
    </v-container>

  </v-img>

  <v-dialog v-model="cropDialog" max-width="640">
    <v-card>
      <v-card-title class="text-h6">{{ $t("crop_title") }}</v-card-title>
      <v-card-text>
        <image-cropper
          v-if="cropSource"
          :src="cropSource"
          :aspect-ratio="Number(aspectRatio)"
          :round="rounded === 'circle'"
          :initial-crop="cropInitial"
          @update:crop="crop => draftCrop = crop"
        ></image-cropper>
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn @click="cropDialog = false">{{ $t("cancel") }}</v-btn>
        <v-btn color="primary" variant="elevated" :disabled="!draftCrop" @click="confirmCrop">
          {{ $t("crop_apply") }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">

const props = defineProps({
  id: {
    type: [Number],
    required: false,
  },
  version: {
    type: [Number],
    required: false,
  },
  path: {
    type: [String],
    required: true,
  },
  aspectRatio: {
    type: [Number],
    required: false,
    default: "1"
  },
  width: {
    type: [String],
    required: false,
    default: "1000px"
  },
  rounded: {
    type: [String],
    required: false,
    default: "lg"
  },
  buttonsSize: {
    type: [String],
    required: false,
    default: "100px"
  },
  buttonsIconSize: {
    type: [String],
    required: false,
    default: "text-h4"
  },
  buttonsSpacing: {
    type: [String],
    required: false,
    default: "ga-16"
  },
  buttonsRounded: {
    type: [String],
    required: false,
    default: "xl"
  },
})

import {doDeleteImage, getDefaultImageUrl, getImageUrl, uploadImage} from "@/scripts/common";
import type {ImageCrop} from "@/scripts/crop";
import ImageCropper from "@/components/ImageCropper.vue";
import {computed, ref, watch} from "vue";

const image = ref<File | null>(null)
/** The part of [image] to keep; the backend cuts it out when the picture is submitted. */
const imageCrop = ref<ImageCrop | null>(null)
const baseImageUrl = ref<string>(getImageUrl(props.path, props.id, props.version))
const defaultImageUrl = ref<string>(getDefaultImageUrl(props.path))
const imageUrl = ref<string>(baseImageUrl.value)

const fileInput = ref<HTMLInputElement | null>(null)
const imageUpdated = ref<Boolean>(false)
const imageDeleted = ref<Boolean>(false)
const hasImage = ref<Boolean>(false)

// ── Framing ─────────────────────────────────────────────────────────────────
//
// A picked file goes through the cropper before it replaces anything: cancelling there
// leaves whatever was showing — the saved picture, or the one framed before — untouched.

const cropDialog = ref(false)
/** The file in the cropper, and the URL it is drawn from. */
const cropFile = ref<File | null>(null)
const cropSource = ref<string | null>(null)
const cropInitial = ref<ImageCrop | null>(null)
const draftCrop = ref<ImageCrop | null>(null)

const previewCrop = computed(() => imageUpdated.value && !imageDeleted.value ? imageCrop.value : null)

/**
 * The whole picture, scaled and shifted so that the crop fills the frame — the browser draws
 * it from the same `<img>` orientation the cropper measured, so the preview is the crop.
 */
const previewStyle = computed(() => {
  const crop = previewCrop.value!
  return {
    width: `${100 / crop.width}%`,
    height: `${100 / crop.height}%`,
    left: `${-100 * crop.left / crop.width}%`,
    top: `${-100 * crop.top / crop.height}%`,
  }
})

function onFilePicked(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // Picking the same file twice fires no change event unless the input is cleared first
  input.value = ''
  // The picker also fires this when it is dismissed without picking a file.
  if (!file) return
  openCropper(file, URL.createObjectURL(file), null)
}

/** Back into the cropper with the picture already framed, where it was left. */
function reframe() {
  if (image.value) openCropper(image.value, imageUrl.value, imageCrop.value)
}

function openCropper(file: File, source: string, initial: ImageCrop | null) {
  cropFile.value = file
  cropSource.value = source
  cropInitial.value = initial
  draftCrop.value = null
  cropDialog.value = true
}

function confirmCrop() {
  if (imageUrl.value !== cropSource.value) releaseUrl(imageUrl.value)
  image.value = cropFile.value
  imageCrop.value = draftCrop.value
  imageUrl.value = cropSource.value!
  imageUpdated.value = true
  imageDeleted.value = false
  cropSource.value = null
  cropDialog.value = false
}

// Closed by cancelling, or by a click beside it: a picture that was never confirmed is let go.
watch(cropDialog, open => {
  if (open || !cropSource.value) return
  if (cropSource.value !== imageUrl.value) releaseUrl(cropSource.value)
  cropSource.value = null
})

function releaseUrl(url: string) {
  if (url.startsWith('blob:')) URL.revokeObjectURL(url)
}

function undoImageChange() {
  imageUpdated.value = false
  imageDeleted.value = false
  imageUrl.value = baseImageUrl.value
}

function deleteImage() {
  imageDeleted.value = hasImage.value
  imageUpdated.value = hasImage.value

  imageUrl.value = defaultImageUrl.value
}

function triggerFileInput() {
  fileInput.value?.click()
}

function handleImageLoad(url) {
  // Only consider the primary image for setting imageExists
  if (url === baseImageUrl.value) {
    console.log("image success")
    // A record with no image resolves to the placeholder, which loads fine but
    // is nothing to delete.
    hasImage.value = baseImageUrl.value !== defaultImageUrl.value
  }
}

function handleImageError(url) {
  console.log("load error")
  // If primary image fails, set imageExists to false and hide the button
  if (url === baseImageUrl.value) {
    console.log("image error")
    hasImage.value = false
  }
}

/**
 * Sends the pending image change, if any, and returns the image version to
 * display from now on.
 *
 * A record that was just created passes [id] in: its id reaches the `id` prop
 * only once the parent has re-rendered, and waiting for that render to upload
 * the image is what made the image silently vanish on creation.
 */
async function submitImage(id: number = props.id): Promise<number> {
  const currentVersion = props.version ?? 0
  if (imageDeleted.value) {
    await doDeleteImage(props.path, id)
    return 0
  } else if (imageUpdated.value) {
    // The record had no version yet, its first image is version 1.
    await uploadImage(id, image.value, props.path, imageCrop.value)
    return currentVersion + 1
  }
  return currentVersion
}

defineExpose({
  submitImage,
})

</script>

<style scoped>
.preview-image {
  cursor: pointer;
}
.image-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(255, 255, 255, 0);
  transition: background-color 0.3s ease;
}
.preview-image:hover .image-overlay {
  background-color: rgba(255, 255, 255, 0.3); /* Adjust opacity for desired whitening effect */
}

.crop-preview {
  position: absolute;
  inset: 0;
  overflow: hidden;
}
.crop-preview img {
  position: absolute;
  max-width: none;
}

.overlay-button {
  opacity: 0;
  transition: opacity 0.3s ease;
}

.preview-image:hover .overlay-button {
  opacity: 1; /* Button becomes visible on hover */
}
</style>
