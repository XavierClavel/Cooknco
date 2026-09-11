<template>
  <v-container class="mx-auto" align="center">
    <v-card
      class="pa-5 ma-5"
      max-width="1000px"
      min-width="300px"
    >
      <v-card-title>
        {{ $t("unsubscribe_title") }}
      </v-card-title>

      <v-card-text v-if="state === 'pending'" class="my-4">
        <v-progress-circular indeterminate size="24"></v-progress-circular>
      </v-card-text>

      <template v-else-if="state === 'done'">
        <v-card-text class="my-4">
          {{ $t("unsubscribe_done") }}
        </v-card-text>
        <v-card-text class="pt-0 text-caption">
          {{ $t("unsubscribe_done_hint") }}
        </v-card-text>
        <v-container>
          <v-row class="d-flex align-center justify-center mb-2 ga-4" dense>
            <action-button
              icon="mdi-cog-outline"
              :text="`${$t('settings')}`"
              :action="toSettings"
            ></action-button>
          </v-row>
        </v-container>
      </template>

      <template v-else>
        <error :error="'unsubscribe_failed'"></error>
        <v-container>
          <v-row class="d-flex align-center justify-center mb-2 ga-4" dense>
            <action-button
              icon="mdi-cog-outline"
              :text="`${$t('settings')}`"
              :action="toSettings"
            ></action-button>
          </v-row>
        </v-container>
      </template>
    </v-card>
  </v-container>
</template>

<script lang="ts" setup>
import { ref } from 'vue';
import { useRoute } from 'vue-router';
import { toSettings } from '@/scripts/common'
import { unsubscribeFromMails } from '@/scripts/settings'

const route = useRoute();
const state = ref<'pending' | 'done' | 'failed'>('pending')

/**
 * Unsubscribes on arrival rather than behind a confirm button.
 *
 * Somebody who pressed "unsubscribe" in a mail has already said what they want, and asking
 * again is how an unsubscribe turns into a spam report. Doing it from the page instead of on
 * the link itself is what keeps the mail scanners out of it: a scanner that fetches the URL
 * gets the app shell and never runs this, and the backend only ever acts on the POST below.
 *
 * The way back is the settings page, which needs a session - the token in the link cannot
 * switch these mails on again, only off.
 */
unsubscribeFromMails(route.query.token)
  .then(() => { state.value = 'done' })
  .catch(() => { state.value = 'failed' })
</script>
