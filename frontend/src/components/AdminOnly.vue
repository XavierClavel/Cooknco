<script setup lang="ts">

import {useAuthStore} from "@/stores/auth";

const authStore = useAuthStore()
// Computed rather than a ref snapshot: checkAuth resolves after this component mounts,
// so a snapshot would hide the admin entry until the next full page load.
const isAdmin = computed(() => authStore.isAdmin)
</script>

<template>
  <div v-if="isAdmin">
    <slot/>
  </div>

</template>

<style scoped>

</style>
