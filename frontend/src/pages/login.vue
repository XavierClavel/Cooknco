<template>
  <v-container class="mx-auto" align="center">
    <v-card
    class="pa-5 ml-n2"
    max-width="1000px"
    min-width="300px"
    >


      <v-form @submit.prevent="submit" ref="form">
        <v-card-title>
          {{$t("login")}}
        </v-card-title>
        <error :error="errorMessage"></error>

        <v-btn
          class="mt-3"
          icon="mdi-google"
          base-color="background"
          width="150px"
          @click="loginOauthGoogle"
        >
          <img src="@/assets/google.webp" alt="icon" style="width: 24px; height: 24px;" />
        </v-btn>

        <v-container class="my-4">
          <v-row wrap no-gutters>
            <v-col cols="5" class="text-center">
              <v-divider class="mt-3" color="black" opacity="1"/>
            </v-col>
            <v-col cols="2" class="text-center text-h6 mt-n1" >
              {{ $t("or")}}
            </v-col>
            <v-col cols="5" class="text-center">
              <v-divider class="mt-3" color="black" opacity="1"/>
            </v-col>
          </v-row>
        </v-container>

          <v-text-field
            v-model="user.mail"
            prepend-inner-icon="mdi-email-outline"
            :label="`${$t('mail')}`"
            :rules="[requiredRule]"
          ></v-text-field>

          <v-text-field
            v-model="user.password"
            prepend-inner-icon="mdi-lock-outline"
            :append-inner-icon="show1 ? 'mdi-eye' : 'mdi-eye-off'"
            :rules="[requiredRule]"
            :type="show1 ? 'text' : 'password'"
            :label="`${$t('password')}`"
            counter
            @click:append-inner="show1 = !show1"
            @keyup.enter="submit"
          ></v-text-field>


        <a
          class="text-black"
          href="/password/reset"
        >
          {{$t('forgotten_password')}}
        </a>

        <v-container>
          <v-row
            class="d-flex align-center justify-center mb-2 gx-16"
            dense
          >
            <v-col cols="12" sm="auto" >
              <action-button
                icon="mdi-close-circle-outline"
                :text="`${$t('sign_up')}`"
                :action="toSignup"
              ></action-button>
            </v-col>
            <v-col cols="12" sm="auto">
              <action-button
                icon="mdi-send"
                :text="`${$t('log_in')}`"
                :action="submit"
              ></action-button>
            </v-col>
          </v-row>
        </v-container>




      </v-form>
    </v-card>
  </v-container>

</template>

<script lang="ts" setup>
import { ref } from 'vue';
import { useRoute } from 'vue-router';
import {login, loginOauthGoogle, toResetPassword, toSignup} from '@/scripts/common'
import {useI18n} from "vue-i18n";
import {requiredRule} from "@/scripts/rules";

// Get the route object
const route = useRoute();
const show1 = ref<boolean>(false)
const errorMessage = ref(null)
const { t } = useI18n();
const value = ref(null)
const form = ref(null)


const user = ref<object>({
  username: '',
  password: '',
})



const submit = async() => {
  errorMessage.value = null
  const {valid, errors} = await form.value.validate()
  if (!valid) {
    return
  }
  login(user.value).catch(function (error) {
    errorMessage.value = error.response.data
    console.log(error);
  })
  //createRecipe(recipe)
}



</script>
