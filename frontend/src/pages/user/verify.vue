<template>
  <auth-card :title="$t('email_verification')">
    <error :error="errorMessage"></error>
    <p v-if="!errorMessage" class="auth-text">{{ $t('email_verified') }}</p>
    <button type="button" class="sk-btn sk-btn--coral sk-btn--block sk-btn--rect sk-btn--lg auth-btn" @click="toLogin">
      {{ $t('log_in') }}
    </button>
  </auth-card>
</template>

<script lang="ts" setup>
import { ref } from 'vue';
import { useRoute } from 'vue-router';
import {toLogin, verifyEmail} from '@/scripts/common'

const route = useRoute();
const errorMessage = ref(null)
const token = ref(route.query.token)

const verify = (token) => {
  verifyEmail(token).catch(function (error) {
    console.log(error.response)
    errorMessage.value = error.response.data
  })
}

verify(token.value)
</script>
