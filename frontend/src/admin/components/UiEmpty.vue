<template>
  <div class="empty">
    <ui-icon :name="icon" :size="26" stroke-width="1.4" />
    <h4>{{ title }}</h4>
    <p class="small" v-if="hint">{{ hint }}</p>
  </div>
</template>

<script setup lang="ts">
import UiIcon from './UiIcon.vue'
import {ICONS} from '../lib/icons'
withDefaults(defineProps<{title: string; hint?: string; icon?: keyof typeof ICONS}>(), {icon: 'search'})
</script>
