<template>
  <auth-card :title="$t('log_in')">
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

    <v-form ref="form" class="auth-form" @submit.prevent="submit">
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
        name="password"
        autocomplete="current-password"
        prepend-inner-icon="mdi-lock-outline"
        :append-inner-icon="show1 ? 'mdi-eye-off-outline' : 'mdi-eye-outline'"
        density="comfortable"
        :rules="[requiredRule]"
        :type="show1 ? 'text' : 'password'"
        :label="$t('password')"
        @click:append-inner="show1 = !show1"
      ></v-text-field>

      <button type="submit" class="sk-btn sk-btn--coral sk-btn--block sk-btn--rect sk-btn--lg auth-btn">
        {{ $t('log_in') }}
      </button>
    </v-form>

    <router-link class="sk-link auth-center" to="/password/reset">
      {{ $t('forgotten_password') }}
    </router-link>

    <template #below>
      {{ $t('new_here') }}
      <router-link to="/signup">{{ $t('create_an_account') }}</router-link>
    </template>
  </auth-card>
</template>

<script lang="ts" setup>
import { ref } from 'vue';
import {login, loginOauthGoogle} from '@/scripts/common'
import {requiredRule} from "@/scripts/rules";

const show1 = ref<boolean>(false)
const errorMessage = ref(null)
const form = ref(null)

const user = ref({
  mail: '',
  password: '',
})

const submit = async() => {
  errorMessage.value = null
  const {valid} = await form.value.validate()
  if (!valid) {
    return
  }
  login(user.value).catch(function (error) {
    errorMessage.value = error.response.data
    console.log(error);
  })
}
</script>
