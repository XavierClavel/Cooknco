<template>
  <section class="sticker sk-pad-lg sk-narrow password-update">
    <h1 class="sk-title">{{ $t("update_password") }}</h1>

    <error :error="errorMessage"></error>

    <v-form ref="form" class="sk-stack" @submit.prevent="submit">
      <div class="sk-field">
        <label class="sk-label" for="current-password">{{ $t('current_password') }}</label>
        <v-text-field
          id="current-password"
          v-model="currentPassword"
          name="current-password"
          autocomplete="current-password"
          :append-inner-icon="showOld ? 'mdi-eye-off-outline' : 'mdi-eye-outline'"
          density="comfortable"
          :rules="[requiredRule, min8Rule]"
          :type="showOld ? 'text' : 'password'"
          class="my-0"
          @click:append-inner="showOld = !showOld"
        ></v-text-field>
      </div>

      <div class="sk-field">
        <label class="sk-label" for="new-password">{{ $t('new_password') }}</label>
        <v-text-field
          id="new-password"
          v-model="newPassword1"
          name="new-password"
          autocomplete="new-password"
          :append-inner-icon="show1 ? 'mdi-eye-off-outline' : 'mdi-eye-outline'"
          density="comfortable"
          :rules="[requiredRule, min8Rule]"
          :type="show1 ? 'text' : 'password'"
          :hint="$t('min_8_characters')"
          persistent-hint
          class="my-0"
          @click:append-inner="show1 = !show1"
        ></v-text-field>
      </div>

      <div class="sk-field">
        <label class="sk-label" for="confirm-password">{{ $t('confirm_new_password') }}</label>
        <v-text-field
          id="confirm-password"
          v-model="newPassword2"
          name="confirm-password"
          autocomplete="new-password"
          :append-inner-icon="show2 ? 'mdi-eye-off-outline' : 'mdi-eye-outline'"
          density="comfortable"
          :rules="[requiredRule, passwordRule(newPassword1, newPassword2)]"
          :type="show2 ? 'text' : 'password'"
          class="my-0"
          @click:append-inner="show2 = !show2"
        ></v-text-field>
      </div>

      <div class="password-update__actions">
        <button type="submit" class="sk-btn sk-btn--coral">{{ $t('save') }}</button>
      </div>
    </v-form>
  </section>
</template>

<script lang="ts" setup>
import {ref} from "vue";
import {toUpdatePasswordSuccess} from "@/scripts/common";
import {updatePassword} from "@/scripts/users";
import {min8Rule, passwordRule, requiredRule} from "@/scripts/rules";

const showOld = ref(false)
const show1 = ref<boolean>(false)
const show2 = ref<boolean>(false)

const currentPassword = ref('')
const newPassword1 = ref('')
const newPassword2 = ref('')

const errorMessage = ref(null)
const form = ref(null)

const submit = async() => {
  const {valid} = await form.value.validate()
  if (!valid) {
    return
  }
  errorMessage.value = null
  updatePassword(currentPassword.value, newPassword1.value)
    .then(function () {
      toUpdatePasswordSuccess()
    })
    .catch(function (error) {
      errorMessage.value = error.response.data
      console.log(error);
    })
}
</script>

<style scoped>
.password-update {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.password-update :deep(.v-input:not(.v-input--error) .v-messages) {
  font-size: 12px;
  color: var(--muted);
  opacity: 1;
}

.password-update__actions {
  display: flex;
  justify-content: center;
  padding-top: 4px;
}
</style>
