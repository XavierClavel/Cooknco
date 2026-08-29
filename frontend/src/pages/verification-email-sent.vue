<template>
  <v-container class="mx-auto" align="center">
    <v-card
    class="pa-5 ma-5"
    max-width="1000px"
    min-width="300px"
    >


      <form @submit.prevent="submit">
        <v-card-title>
          {{$t("verification_email_sent_title")}}
        </v-card-title>
        <v-card-text class="my-4">
          {{$t("verification_email_sent_description")}}
        </v-card-text>
      </form>
    </v-card>
  </v-container>

</template>

<script lang="ts" setup>
import { ref } from 'vue';
import { useRoute } from 'vue-router';
import {login, toSignup} from '@/scripts/common'
import {useI18n} from "vue-i18n";

// Get the route object
const route = useRoute();
const show1 = ref<boolean>(false)
const errorMessage = ref(null)
const { t } = useI18n();


const rules = {
  required: value => !!value || t('required'),
  min: v => v.length >= 8 || t('min_8_characters'),
  passwordMatch: () => user.value.password == password2.value || t('passwords_must_match'),
}

const user = ref<object>({
  username: '',
  password: '',
})



const submit = () => {
  errorMessage.value = null
  login(user.value).catch(function (error) {
    errorMessage.value = error.response.data
    console.log(error);
  })
  //createRecipe(recipe)
}



</script>
