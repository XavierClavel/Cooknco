<!--
  https://cooknco.eu/privacy - the URL the Play Console's "Privacy policy" field points at,
  and the one the app links to from its settings. It must stay reachable signed out and at
  this exact path: changing it means changing the console entry in the same breath, and a
  privacy policy that 404s is a listing rejection rather than a broken link.
-->
<template>
  <legal-document :doc="doc"></legal-document>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { legalDocument } from '@/scripts/legal'

const doc = computed(() => legalDocument('privacy'))
</script>
