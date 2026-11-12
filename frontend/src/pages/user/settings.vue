<template>
  <section class="sticker sk-pad-lg sk-narrow settings">
    <h1 class="sk-title">{{ $t("settings") }}</h1>
    <error :error="errorMessage"></error>

    <form class="settings__form" @submit.prevent="submit">
      <div class="sk-field settings__language">
        <label class="sk-label" for="settings-language">{{ $t('language') }}</label>
        <select id="settings-language" v-model="locale" class="sk-input">
          <option v-for="item in locales" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
      </div>

      <label class="settings__box settings__row" for="settings-public">
        <span class="settings__box-title">{{ $t('public_account') }}</span>
        <span class="sk-switch">
          <input id="settings-public" v-model="settings.isAccountPublic" type="checkbox" />
          <span></span>
        </span>
      </label>

      <div class="settings__box">
        <div class="sk-field">
          <label class="sk-label" for="settings-units">{{ $t('unit_system') }}</label>
          <select
            id="settings-units"
            v-model="settings.unitSystem"
            class="sk-input settings__select"
            aria-describedby="settings-units-hint"
          >
            <option v-for="item in unitSystems" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </div>
        <p id="settings-units-hint" class="sk-hint">{{ $t('unit_system_hint') }}</p>
      </div>

      <div class="settings__box">
        <label class="settings__row" for="settings-mail">
          <span class="settings__box-title">{{ $t('mail_notifications') }}</span>
          <span class="sk-switch">
            <input
              id="settings-mail"
              v-model="settings.mailNotificationsEnabled"
              type="checkbox"
              aria-describedby="settings-mail-hint"
            />
            <span></span>
          </span>
        </label>
        <p id="settings-mail-hint" class="sk-hint">{{ $t('mail_notifications_hint') }}</p>
      </div>

      <div class="settings__actions">
        <button type="button" class="sk-btn" @click="toUpdatePassword">{{ $t('password') }}</button>
        <button type="button" class="sk-btn" @click="toMcp">{{ $t('mcp_settings') }}</button>
        <button type="submit" class="sk-btn sk-btn--coral">{{ $t('save') }}</button>
      </div>
    </form>

    <!--
      Deleting the account, at the bottom and behind a confirmation, because it is the one
      thing on this page that cannot be undone. cooknco.eu/account-deletion describes these
      exact steps, and the Play Console's data deletion entry points at that page: when
      this moves, that page moves with it.
    -->
    <hr class="sk-divider" />

    <div class="settings__box settings__delete">
      <i18n-t keypath="delete_account_hint" tag="p" class="sk-hint settings__delete-text">
        <template #link>
          <router-link class="sk-link" to="/account-deletion">{{ $t('delete_account_hint_link') }}</router-link>
        </template>
      </i18n-t>

      <v-dialog max-width="500">
        <template #activator="{ props: activatorProps }">
          <button type="button" class="sk-btn sk-btn--ink" v-bind="activatorProps">
            {{ $t('delete_account') }}
          </button>
        </template>

        <template #default="{ isActive }">
          <div class="sticker sk-pad-lg settings__dialog">
            <h2 class="sk-h2">{{ $t('delete_account_title') }}</h2>
            <p>{{ $t('delete_account_description') }}</p>
            <div class="settings__dialog-actions">
              <button type="button" class="sk-btn" @click="isActive.value = false">{{ $t('cancel') }}</button>
              <button
                type="button"
                class="sk-btn sk-btn--ink"
                :disabled="isDeleting"
                @click="deleteAccount(isActive)"
              >
                <v-progress-circular v-if="isDeleting" indeterminate size="16" width="2"></v-progress-circular>
                {{ $t('delete_account_confirm') }}
              </button>
            </div>
          </div>
        </template>
      </v-dialog>
    </div>
  </section>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';
import {toLogin, toMcp, toMyProfile, toUpdatePassword} from '@/scripts/common'
import {useI18n} from "vue-i18n";
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
  isAccountPublic: false,
  mailNotificationsEnabled: false,
  unitSystem: METRIC,
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

<style scoped>
.settings {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.settings__form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.settings__language {
  max-width: 300px;
}

.settings__box {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  border: var(--line) solid var(--ink);
  border-radius: var(--radius-sm);
  background: var(--white);
}

.settings__box p {
  margin: 0;
}

.settings__row {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  cursor: pointer;
}

.settings__box-title {
  font-size: 15px;
  font-weight: 700;
}

.settings__select {
  max-width: 300px;
  background: var(--paper);
}

.settings__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
  padding-top: 8px;
}

.settings__delete {
  align-items: center;
}

.settings__delete-text {
  align-self: stretch;
}

.settings__dialog {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.settings__dialog p {
  margin: 0;
  font-size: 14px;
  line-height: 1.5;
}

.settings__dialog-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}
</style>
