<template>
  <div class="pager">
    <span class="muted small tnum">
      {{ from }}–{{ to }} {{ $t('admin_of') }} {{ total.toLocaleString() }}
    </span>
    <span class="spacer"></span>
    <button class="btn icon" :disabled="page <= 1" :aria-label="$t('admin_previous')" @click="go(page - 1)">
      <ui-icon name="chevronL" />
    </button>
    <span class="small tnum muted">{{ page }} / {{ pages }}</span>
    <button class="btn icon" :disabled="page >= pages" :aria-label="$t('admin_next')" @click="go(page + 1)">
      <ui-icon name="chevronR" />
    </button>
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue'
import UiIcon from './UiIcon.vue'

const props = defineProps<{page: number; size: number; total: number}>()
const emit = defineEmits<{(e: 'update:page', v: number): void}>()

const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
const from = computed(() => (props.total === 0 ? 0 : (props.page - 1) * props.size + 1))
const to = computed(() => Math.min(props.total, props.page * props.size))

const go = (p: number) => {
  if (p >= 1 && p <= pages.value) emit('update:page', p)
}
</script>

<style scoped>
.pager { display: flex; align-items: center; gap: 8px; }
</style>
