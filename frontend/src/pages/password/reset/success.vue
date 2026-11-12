<template>
  <auth-card :title="$t('reset_password_success_title')">
    <p class="auth-text">{{ $t('reset_password_success_description') }}</p>
    <button type="button" class="sk-btn sk-btn--coral sk-btn--block sk-btn--rect sk-btn--lg auth-btn" @click="toLogin">
      {{ $t('log_in') }}
    </button>
  </auth-card>
</template>

<script lang="ts" setup>
import {toLogin} from '@/scripts/common'
</script>
