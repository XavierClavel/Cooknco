<!--
  The one renderer for the privacy policy and the deletion notice.

  Both are reachable signed out - the Play Console points at them and a reviewer opens them
  with no account - so nothing here reads the session or calls the API. The copy itself is
  in `@/locales/legal`, per language, and carries no markup: a document that has to exist
  twice is easier to keep honest when neither copy can drift in layout as well as in wording.
-->
<template>
  <v-container class="mx-auto" align="center">
    <v-card
      class="pa-5 ma-5 text-left"
      max-width="1000px"
      min-width="300px"
    >
      <v-card-title class="text-h5 text-wrap">
        {{ doc.title }}
      </v-card-title>

      <v-card-subtitle class="text-caption pb-2">
        {{ doc.updated }}
      </v-card-subtitle>

      <v-card-text v-for="(paragraph, index) in doc.intro" :key="`intro-${index}`" class="pb-2">
        {{ paragraph }}
      </v-card-text>

      <template v-for="(section, index) in doc.sections" :key="`section-${index}`">
        <v-card-title class="text-h6 text-sm-h6 text-wrap pb-1">
          {{ section.heading }}
        </v-card-title>

        <v-card-text v-for="(paragraph, i) in section.body ?? []" :key="`body-${index}-${i}`" class="py-1">
          {{ paragraph }}
        </v-card-text>

        <v-card-text v-if="section.bullets?.length" class="py-1">
          <ul class="legal-list">
            <li v-for="(bullet, i) in section.bullets" :key="`bullet-${index}-${i}`">
              <span v-if="bullet.term" class="font-weight-bold">{{ bullet.term }}&nbsp;—&nbsp;</span>{{ bullet.text }}
            </li>
          </ul>
        </v-card-text>

        <v-card-text v-for="(paragraph, i) in section.after ?? []" :key="`after-${index}-${i}`" class="py-1">
          {{ paragraph }}
        </v-card-text>
      </template>
    </v-card>
  </v-container>
</template>

<script lang="ts" setup>
import type { LegalDocument } from '@/scripts/legal'

defineProps<{ doc: LegalDocument }>()
</script>

<style scoped lang="sass">
.legal-list
  padding-left: 1.25rem

  li
    margin-bottom: 0.5rem
</style>
