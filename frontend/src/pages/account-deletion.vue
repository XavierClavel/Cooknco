<!--
  https://cooknco.eu/account-deletion - the URL the Play Console's "Data deletion" field
  points at. Google requires it to be readable with no account at all, which is why it names
  the steps instead of offering a button: somebody who cannot sign in has to be able to read
  what to do from here.

  What it describes exists on every surface - the app's settings screen, the website's
  settings page, and the mailbox for the case where neither is reachable. When one of those
  changes, this page changes with it.
-->
<template>
  <legal-document :doc="doc"></legal-document>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { legalDocument } from '@/scripts/legal'

const doc = computed(() => legalDocument('deletion'))
</script>
