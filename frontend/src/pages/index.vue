<script lang="ts" setup>
// The old router guard sent any unnamed route to /home; Nuxt needs a real
// index page for `/`.
definePageMeta({ redirect: '/home' })
</script>

<template>
  <div />
</template>
