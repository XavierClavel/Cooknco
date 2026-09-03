<template>
  <svg
    xmlns="http://www.w3.org/2000/svg"
    :width="size" :height="size" viewBox="0 0 24 24"
    fill="none" stroke="currentColor" :stroke-width="strokeWidth"
    stroke-linecap="round" stroke-linejoin="round"
    aria-hidden="true"
  >
    <path v-for="(d, i) in paths" :key="i" :d="d" />
    <circle v-for="(c, i) in circles" :key="`c${i}`" :cx="c[0]" :cy="c[1]" :r="c[2]" />
  </svg>
</template>

<script setup lang="ts">
import {computed} from 'vue'
import {ICONS} from '../lib/icons'

const props = withDefaults(defineProps<{
  name: keyof typeof ICONS
  size?: number
  strokeWidth?: number
}>(), {size: 16, strokeWidth: 1.75})

const def = computed(() => ICONS[props.name] ?? ICONS.dot)
const paths = computed(() => def.value.p ?? [])
const circles = computed(() => def.value.c ?? [])
</script>
