<template>

  <v-card
  class="mx-auto rounded-xl pa-5 ma-auto my-5"
  max-width="1000px"
  >

    <v-form @submit.prevent="submit" class="mx-auto" ref="form">
      <v-card-title>
        {{$t("update_password")}}
      </v-card-title>

      <error :error="errorMessage"></error>

      <v-text-field
        v-model="currentPassword"
        prepend-icon="mdi-lock-outline"
        :append-icon="showOld ? 'mdi-eye' : 'mdi-eye-off'"
        :rules="[requiredRule, min8Rule]"
        :type="showOld ? 'text' : 'password'"
        hint="At least 8 characters"
        :label="`${$t('current_password')}`"
        counter
        @click:append="showOld = !showOld"
        class="mx-3"
      ></v-text-field>

    <v-text-field
      v-model="newPassword1"
      prepend-icon="mdi-lock-outline"
      :append-icon="show1 ? 'mdi-eye' : 'mdi-eye-off'"
      :rules="[requiredRule, min8Rule]"
      :type="show1 ? 'text' : 'password'"
      :label="`${$t('new_password')}`"
      counter
      @click:append="show1 = !show1"
      class="mx-3"
    ></v-text-field>

    <v-text-field
      v-model="newPassword2"
      prepend-icon="mdi-lock-check-outline"
      :append-icon="show2 ? 'mdi-eye' : 'mdi-eye-off'"
      :rules="[requiredRule, passwordRule(password1, password2)]"
      :type="show2 ? 'text' : 'password'"
      :label="`${$t('confirm_new_password')}`"
      counter
      @click:append="show2 = !show2"
      class="mx-3"
    ></v-text-field>

      <v-container>
        <v-row
          class="d-flex align-center justify-center mb-2 gx-16"
          dense
          justify="center"
          align="center"
        >
          <v-col cols="12" sm="auto" justify-center>
            <action-button
              :icon="ICON_SAVE"
              :text="`${$t('save')}`"
              :action="submit"
            ></action-button>
          </v-col>
        </v-row>
      </v-container>

    </v-form>

  </v-card>

</template>

<script lang="ts" setup>
import {ref} from "vue";
import {useI18n} from "vue-i18n";
import {ICON_SAVE} from "@/scripts/icons";
import {logout, toMyProfile, toUpdatePasswordSuccess} from "@/scripts/common";
import {updatePassword} from "@/scripts/users";
import {min8Rule, passwordRule, requiredRule} from "@/scripts/rules";
import apiClient from "@/scripts/axios";
import {useAuthStore} from "@/stores/auth";

const showOld = ref(false)
const show1 = ref<boolean>(false)
const show2 = ref<boolean>(false)

const currentPassword = ref(null)
const newPassword1 = ref(null)
const newPassword2 = ref(null)

const errorMessage = ref(null)
const { t } = useI18n()
const form = ref(null)

const submit = async() => {
  const {valid, errors} = await form.value.validate()
  if (!valid) {
    return
  }
  errorMessage.value = null
  updatePassword(currentPassword.value, newPassword1.value)
    .then(function (response) {
      toUpdatePasswordSuccess()
    })
    .catch(function (error) {
    errorMessage.value = error.response.data
    console.log(error);
  })
}

</script>
