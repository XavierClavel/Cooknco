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

        <v-card color="background" class="mb-2">
          <v-select
            v-model="settings.unitSystem"
            :prepend-inner-icon="ICON_WEIGHT"
            :label="`${$t('unit_system')}`"
            :items="unitSystems"
            item-title="label"
            item-value="value"
            class="mx-2 mt-2"
          ></v-select>
          <v-card-text class="pt-0 pb-2 text-caption">
            {{ $t('unit_system_hint') }}
          </v-card-text>
        </v-card>

        <v-card color="background" class="mb-2">
          <v-checkbox
            v-model="settings.mailNotificationsEnabled"
            :label="`${$t('mail_notifications')}`"
            color="black"
            base-color="black"
            variant="elevated"
            class="mx-2 my-0"
          ></v-checkbox>
          <v-card-text class="pt-0 pb-2 text-caption">
            {{ $t('mail_notifications_hint') }}
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

      <!--
        Deleting the account, at the bottom and behind a confirmation, because it is the one
        thing on this page that cannot be undone. cooknco.eu/account-deletion describes these
        exact steps, and the Play Console's data deletion entry points at that page: when
        this moves, that page moves with it.
      -->
      <v-divider class="my-2"></v-divider>

      <v-card color="background" class="mb-2">
        <v-card-text class="pb-0 text-caption">
          {{ $t('delete_account_hint') }}
        </v-card-text>
        <v-card-actions class="justify-center">
          <v-dialog max-width="500">
            <template v-slot:activator="{ props: activatorProps }">
              <action-button
                icon="mdi-account-remove"
                :text="`${$t('delete_account')}`"
                v-bind="activatorProps"
              ></action-button>
            </template>

            <template v-slot:default="{ isActive }">
              <v-card>
                <v-card-text class="font-weight-bold text-h5">{{ $t('delete_account_title') }}</v-card-text>
                <v-card-text>{{ $t('delete_account_description') }}</v-card-text>

                <v-card-actions>
                  <v-spacer></v-spacer>
                  <v-btn
                    :text="`${$t('cancel')}`"
                    @click="isActive.value = false"
                  ></v-btn>
                  <v-btn
                    :text="`${$t('delete_account_confirm')}`"
                    :loading="isDeleting"
                    @click="deleteAccount(isActive)"
                  ></v-btn>
                </v-card-actions>
              </v-card>
            </template>
          </v-dialog>
        </v-card-actions>
      </v-card>
    </v-card>
  </v-container>

</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';
import {login, toLogin, toMcp, toMyProfile, toSignup, toUpdatePassword} from '@/scripts/common'
import {useI18n} from "vue-i18n";
import {ICON_LOCALIZATION, ICON_SAVE, ICON_WEIGHT} from "@/scripts/icons";
import {forceLocale, fromApiLocale, getLocale, toApiLocale} from "@/scripts/localization";
import {getSettings, updateSettings} from "@/scripts/settings";
import {IMPERIAL, METRIC, setUnitSystem} from "@/scripts/unitSystem";
import {deleteMyAccount} from "@/scripts/users";
import {useAuthStore} from "@/stores/auth";

const errorMessage = ref(null)
const { t } = useI18n();

const locale = ref(getLocale())

const locales = [
  {label: "Français", value: "fr"},
  {label: "English", value: "en"},
]

const unitSystems = computed(() => [
  {label: t("unit_system_metric"), value: METRIC},
  {label: t("unit_system_imperial"), value: IMPERIAL},
])

const settings = ref({
  autoAcceptFollowRequests: false,
  isAccountPublic: false,
  mailNotificationsEnabled: false,
  unitSystem: METRIC,
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
  // Same idea for the units: applied before the request comes back, because every amount
  // already on screen is rendered from it and a page still showing grams after you asked
  // for pounds reads as a save that failed
  setUnitSystem(settings.value.unitSystem)
  // The language goes to the account, not only to this browser's cookie - it is what mails
  // and notifications are written in, and what every other browser of theirs will read back
  updateSettings({...settings.value, locale: toApiLocale(locale.value)}).then(response => {
    toMyProfile()
  })
}

const isDeleting = ref(false)

/**
 * Deletes the account, then leaves the page signed out.
 *
 * Nothing is asked of the server afterwards - the session died with the account, so the
 * ordinary sign-out call would answer 401 - and only the local state is dropped. The dialog
 * is closed first so the page is not left with a modal over a login redirect.
 */
const deleteAccount = async (isActive) => {
  if (isDeleting.value) return
  isDeleting.value = true
  try {
    await deleteMyAccount()
    isActive.value = false
    localStorage.removeItem("authToken")
    useAuthStore().logout()
    toLogin()
  } catch {
    isActive.value = false
    errorMessage.value = "delete_account_failed"
  } finally {
    isDeleting.value = false
  }
}



</script>
