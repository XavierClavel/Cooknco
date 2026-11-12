<template>
  <auth-card :title="$t('sign_up')">
    <error :error="errorMessage"></error>

    <button
      type="button"
      class="sk-btn sk-btn--block sk-btn--rect sk-btn--lg sk-btn--flat auth-google"
      @click="loginOauthGoogle"
    >
      <img src="@/assets/google.webp" alt="" />
      {{ $t('continue_with_google') }}
    </button>

    <div class="sk-or">{{ $t('or') }}</div>

    <v-form ref="loginForm" class="auth-form" @submit.prevent="submit">
      <v-text-field
        v-model="user.username"
        name="username"
        autocomplete="username"
        prepend-inner-icon="mdi-account-outline"
        density="comfortable"
        :label="$t('username')"
        :rules="[requiredRule]"
      ></v-text-field>

      <v-text-field
        v-model="user.mail"
        type="email"
        name="email"
        autocomplete="email"
        prepend-inner-icon="mdi-email-outline"
        density="comfortable"
        :label="$t('mail')"
        :rules="[requiredRule]"
      ></v-text-field>

      <v-text-field
        v-model="user.password"
        name="new-password"
        autocomplete="new-password"
        prepend-inner-icon="mdi-lock-outline"
        :append-inner-icon="show1 ? 'mdi-eye-off-outline' : 'mdi-eye-outline'"
        density="comfortable"
        :rules="[requiredRule, min8Rule]"
        :type="show1 ? 'text' : 'password'"
        :label="$t('password')"
        @click:append-inner="show1 = !show1"
      ></v-text-field>

      <v-text-field
        v-model="password2"
        name="confirm-password"
        autocomplete="new-password"
        prepend-inner-icon="mdi-lock-check-outline"
        :append-inner-icon="show2 ? 'mdi-eye-off-outline' : 'mdi-eye-outline'"
        density="comfortable"
        :rules="[requiredRule, passwordRule(user.password, password2)]"
        :type="show2 ? 'text' : 'password'"
        :label="$t('password_confirm')"
        @click:append-inner="show2 = !show2"
      ></v-text-field>

      <button type="submit" class="sk-btn sk-btn--coral sk-btn--block sk-btn--rect sk-btn--lg auth-btn">
        {{ $t('sign_up') }}
      </button>
    </v-form>

    <template #below>
      {{ $t('already_have_account') }}
      <router-link to="/login">{{ $t('log_in') }}</router-link>
    </template>
  </auth-card>
</template>

<script lang="ts" setup>
import { ref } from 'vue';
import {loginOauthGoogle, signup} from "@/scripts/common";
import {min8Rule, passwordRule, requiredRule} from "@/scripts/rules";

const show1 = ref<boolean>(false)
const show2 = ref<boolean>(false)
const password2 = ref<string>('')
const errorMessage = ref(null)
const loginForm = ref(null)

const user = ref({
  username: '',
  mail: '',
  password: '',
})


const submit = async() => {
  errorMessage.value = null
  const {valid} = await loginForm.value.validate()
  if (!valid) {
    return
  }
  signup(user.value).catch(function (error) {
    errorMessage.value = error.response.data
    console.log(error);
  })
}
</script>
