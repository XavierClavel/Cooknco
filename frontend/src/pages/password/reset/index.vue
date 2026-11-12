<template>
  <auth-card :title="$t('reset_password')">
    <p class="auth-text">{{ $t('reset_password_text') }}</p>
    <error :error="errorMessage"></error>

    <v-form ref="loginForm" class="auth-form" @submit.prevent="submit">
      <v-text-field
        v-model="mail"
        type="email"
        name="email"
        autocomplete="email"
        prepend-inner-icon="mdi-email-outline"
        density="comfortable"
        :label="$t('mail')"
        :rules="[requiredRule]"
      ></v-text-field>

      <div class="auth-actions">
        <button type="button" class="sk-btn sk-btn--rect sk-btn--lg auth-btn" @click="toLogin">
          {{ $t('cancel') }}
        </button>
        <button type="submit" class="sk-btn sk-btn--coral sk-btn--rect sk-btn--lg auth-btn">
          {{ $t('send') }}
        </button>
      </div>
    </v-form>
  </auth-card>
</template>

<script lang="ts" setup>
import { ref } from 'vue';
import {requestPasswordReset, toLogin, toResetPasswordEmailSent} from "@/scripts/common";
import {requiredRule} from "@/scripts/rules";

const errorMessage = ref(null)
const loginForm = ref(null)

const mail = ref<string>()


const submit = async() => {
  errorMessage.value = null
  const {valid} = await loginForm.value.validate()
  if (!valid) {
    return
  }
  requestPasswordReset(mail.value)
    .then(function () {
      toResetPasswordEmailSent()
    })
    .catch(function (error) {
      errorMessage.value = error.response.data
      console.log(error);
    })
}
</script>
