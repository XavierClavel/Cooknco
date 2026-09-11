<template>
  <v-container class="mx-auto" align="center">
    <v-card
    class="lg pa-5 ma-5"
    max-width="1000px"
    min-width="300px"
    >


      <form @submit.prevent="submit">
        <v-card-title >
          {{$t("settings")}}
        </v-card-title>
        <error :error="errorMessage"></error>
        <v-select
        v-model="locale"
        :prepend-inner-icon="ICON_LOCALIZATION"
        :items="locales"
        item-title="label"
        item-value="value"
        ></v-select>

        <v-card color="background" class="mb-2">
          <v-checkbox
            v-model="settings.isAccountPublic"
            :label="`${$t('public_account')}`"
            color="black"
            base-color="black"
            bg-color="background"
            variant="elevated"
            class="mx-2 my-0 mb-n6 text-black"
          ></v-checkbox>
        </v-card>

        <v-card color="background" class="mb-2">
          <v-checkbox
            v-model="autoAcceptFollowRequests"
            :label="`${$t('auto_accept_follow_requests')}`"
            :disabled="settings.isAccountPublic"
            color="black"
            base-color="black"
            variant="elevated"
            :class="settings.isAccountPublic ? 'mx-2 my-0' : 'mx-2 my-0 mb-n6'"
          ></v-checkbox>
          <v-card-text
            v-if="settings.isAccountPublic"
            class="pt-0 pb-2 text-caption"
          >
            {{ $t('auto_accept_follow_requests_public_hint') }}
          </v-card-text>
        </v-card>

        <v-container>
          <v-row
            class="d-flex align-center justify-center mb-2 ga-4"
            dense
          >
              <action-button
                icon="mdi-lock-reset"
                :text="`${$t('password')}`"
                :action="toUpdatePassword"
              ></action-button>
              <action-button
                icon="mdi-robot-outline"
                :text="`${$t('mcp_settings')}`"
                :action="toMcp"
              ></action-button>
              <action-button
                :icon="ICON_SAVE"
                :text="`${$t('save')}`"
                :action="submit"
              ></action-button>
          </v-row>
        </v-container>




      </form>
    </v-card>
  </v-container>

</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';
import {login, toMcp, toMyProfile, toSignup, toUpdatePassword} from '@/scripts/common'
import {useI18n} from "vue-i18n";
import {ICON_LOCALIZATION, ICON_SAVE} from "@/scripts/icons";
import {forceLocale, fromApiLocale, getLocale, toApiLocale} from "@/scripts/localization";
import {getSettings, updateSettings} from "@/scripts/settings";

const errorMessage = ref(null)
const { t } = useI18n();

const locale = ref(getLocale())

const locales = [
  {label: "Français", value: "fr"},
  {label: "English", value: "en"},
]

const settings = ref({
  autoAcceptFollowRequests: false,
  isAccountPublic: false,
})

// Public accounts always auto accept: show the toggle locked on, but keep the
// stored preference untouched so it applies again if the account goes private
const autoAcceptFollowRequests = computed({
  get: () => settings.value.isAccountPublic || !!settings.value.autoAcceptFollowRequests,
  set: (value) => { settings.value.autoAcceptFollowRequests = value },
})

getSettings().then(response => {
  settings.value = response.data
  // Show what the account holds, not what this browser happens to be in: an account with no
  // language saved yet keeps the current one, which is then what a save would record
  locale.value = fromApiLocale(response.data.locale) ?? locale.value
})

const submit = () => {
  forceLocale(locale.value)
  // The language goes to the account, not only to this browser's cookie - it is what mails
  // and notifications are written in, and what every other browser of theirs will read back
  updateSettings({...settings.value, locale: toApiLocale(locale.value)}).then(response => {
    toMyProfile()
  })
}



</script>
