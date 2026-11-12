<template>
  <auth-card :title="$t('update_password')">
    <error :error="errorMessage"></error>

    <v-form ref="form" class="auth-form" @submit.prevent="submit">
      <v-text-field
        v-model="newPassword1"
        name="new-password"
        autocomplete="new-password"
        prepend-inner-icon="mdi-lock-outline"
        :append-inner-icon="show1 ? 'mdi-eye-off-outline' : 'mdi-eye-outline'"
        density="comfortable"
        :rules="[requiredRule, min8Rule]"
        :type="show1 ? 'text' : 'password'"
        :label="$t('new_password')"
        @click:append-inner="show1 = !show1"
      ></v-text-field>

      <v-text-field
        v-model="newPassword2"
        name="confirm-password"
        autocomplete="new-password"
        prepend-inner-icon="mdi-lock-check-outline"
        :append-inner-icon="show2 ? 'mdi-eye-off-outline' : 'mdi-eye-outline'"
        density="comfortable"
        :rules="[requiredRule, passwordRule(newPassword1, newPassword2)]"
        :type="show2 ? 'text' : 'password'"
        :label="$t('confirm_new_password')"
        @click:append-inner="show2 = !show2"
      ></v-text-field>

      <button type="submit" class="sk-btn sk-btn--coral sk-btn--block sk-btn--rect sk-btn--lg auth-btn">
        {{ $t('save') }}
      </button>
    </v-form>
  </auth-card>
</template>

<script lang="ts" setup>
import {ref} from "vue";
import {useRoute} from "vue-router";
import {resetPassword, toResetPasswordSuccess} from "@/scripts/common";
import {min8Rule, passwordRule, requiredRule} from "@/scripts/rules";

const show1 = ref<boolean>(false)
const show2 = ref<boolean>(false)

const newPassword1 = ref('')
const newPassword2 = ref('')

const errorMessage = ref(null)
const form = ref(null)
const route = useRoute()
const token = route.query.token

const submit = async() => {
  const {valid} = await form.value.validate()
  if (!valid) {
    return
  }
  resetPassword(token, newPassword1.value)
    .then(function () {
      toResetPasswordSuccess()
    })
    .catch(function (error) {
      errorMessage.value = error.response.data
      console.log(error);
    })
}
</script>
