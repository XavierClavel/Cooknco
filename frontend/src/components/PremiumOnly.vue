<script setup lang="ts">

import {useAuthStore} from "@/stores/auth";

const authStore = useAuthStore()
// Computed rather than a ref snapshot, for the reason AdminOnly's is: checkAuth resolves
// after this component mounts, so a snapshot would hide a subscriber's buttons until the
// next full page load.
//
// One flag, and it is the backend's: `UserInfo.isPremium` already answers true for an
// admin, so nothing here re-derives who premium applies to. What this hides is only what
// is *offered* — the routes behind it refuse anyone else.
const isPremium = computed(() => authStore.isPremium)
</script>

<template>
  <div v-if="isPremium">
    <slot/>
  </div>

</template>

<style scoped>

</style>
