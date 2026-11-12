<template>
  <auth-card :title="$t('logout')">
    <p class="auth-text">{{ $t('logout_successful') }}</p>
    <div class="auth-actions">
      <button type="button" class="sk-btn sk-btn--rect sk-btn--lg auth-btn" @click="toSignup">
        {{ $t('sign_up') }}
      </button>
      <button type="button" class="sk-btn sk-btn--coral sk-btn--rect sk-btn--lg auth-btn" @click="toLogin">
        {{ $t('log_in') }}
      </button>
    </div>
  </auth-card>
</template>

<script lang="ts" setup>
import {toLogin, toSignup} from '@/scripts/common'
</script>
