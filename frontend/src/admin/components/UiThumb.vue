<template>
  <span class="thumb" :class="{round}" :style="box">
    <img v-if="src && !broken" :src="src" alt="" loading="lazy" decoding="async"
         @error="broken = true" />
    <span v-else-if="initials" class="ph initials" :style="{background: tint}">{{ initials }}</span>
    <span v-else class="ph"><ui-icon :name="icon" :size="Math.round(width * 0.45)" /></span>
  </span>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import UiIcon from './UiIcon.vue'
import type {ICONS} from '../lib/icons'

/**
 * Small picture for a table row, degrading to a placeholder when the entity has
 * no image (`src` empty) or the file has gone missing.
 *
 * Purely decorative: every use sits next to the label it illustrates, so the
 * image is hidden from screen readers rather than repeating that label.
 */
const props = withDefaults(defineProps<{
  src?: string
  width?: number
  /** Defaults to a square. */
  height?: number
  /** Circular crop, for avatars. */
  round?: boolean
  /** Placeholder text — takes precedence over `icon` when set. */
  initials?: string
  tint?: string
  icon?: keyof typeof ICONS
}>(), {src: '', width: 24, round: false, initials: '', icon: 'book'})

const box = computed(() => ({
  width: `${props.width}px`,
  height: `${props.height ?? props.width}px`,
}))

const broken = ref(false)
// Rows are recycled as the operator pages through the table, so a stale failure
// must not blank out the next entity's picture.
watch(() => props.src, () => { broken.value = false })
</script>

<style scoped>
.thumb {
  flex: none;
  display: block;
  overflow: hidden;
  border-radius: var(--radius-sm);
  background: var(--c-surface-alt);
  box-shadow: inset 0 0 0 1px var(--c-border);
}
.thumb.round { border-radius: 50%; }
.thumb img { width: 100%; height: 100%; object-fit: cover; display: block; }
.ph {
  width: 100%; height: 100%;
  display: grid; place-items: center;
  color: var(--c-text-subtle);
}
.initials {
  font-size: 10px;
  font-weight: 600;
  color: var(--c-text-muted);
}
</style>
