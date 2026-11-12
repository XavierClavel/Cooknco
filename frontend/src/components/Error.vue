<script setup lang="ts">

defineProps({
  error: {
    type: String,
    required: true,
  },
})
</script>

<template>
  <div v-if="error" class="error-note sticker sticker--mustard" role="alert">
    <v-icon size="32">mdi-alert-outline</v-icon>
    <span>{{ $t(error) }}</span>
  </div>
</template>

<style scoped>
.error-note {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 22px;
  font-size: 17px;
  font-weight: 700;
  line-height: 1.35;
  white-space: pre-line;
}
</style>
