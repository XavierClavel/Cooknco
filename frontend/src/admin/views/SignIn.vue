<template>
  <div class="wrap">
    <form class="card panel" @submit.prevent="submit">
      <div class="head">
        <span class="mark"><ui-icon name="shield" :size="17" /></span>
        <div>
          <h2>Cook&amp;Co {{ $t('admin_backoffice') }}</h2>
          <p class="small muted">{{ $t('admin_sign_in_hint') }}</p>
        </div>
      </div>

      <div class="fields">
        <label class="field">
          <span>{{ $t('mail') }}</span>
          <input v-model="mail" class="input" type="email" autocomplete="username" required />
        </label>
        <label class="field">
          <span>{{ $t('password') }}</span>
          <input v-model="password" class="input" type="password" autocomplete="current-password" required />
        </label>
      </div>

      <div v-if="error" class="alert danger">
        <ui-icon name="alert" />
        <span>{{ error }}</span>
      </div>

      <button class="btn primary" type="submit" :disabled="busy">
        {{ busy ? $t('admin_signing_in') : $t('log_in') }}
      </button>
    </form>
  </div>
</template>

<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {signIn} from '../lib/session'
import {errorKey} from '../lib/api'
import UiIcon from '../components/UiIcon.vue'

const emit = defineEmits<{(e: 'signed-in'): void}>()
const {t, te} = useI18n()

const mail = ref('')
const password = ref('')
const busy = ref(false)
const error = ref('')

async function submit() {
  busy.value = true
  error.value = ''
  try {
    const ok = await signIn(mail.value, password.value)
    // A valid account without the ADMIN role must not land in the console
    if (!ok) { error.value = t('admin_not_an_admin'); return }
    emit('signed-in')
  } catch (e: any) {
    const key = errorKey(e)
    error.value = key && te(key) ? t(key) : t('admin_sign_in_failed')
  } finally {
    busy.value = false
    password.value = ''
  }
}
</script>

<style scoped>
.wrap { min-height: 100%; display: grid; place-items: center; padding: 24px; }
.card { width: 100%; max-width: 380px; padding: 22px; display: flex; flex-direction: column; gap: 16px; }
.head { display: flex; gap: 11px; align-items: flex-start; }
.head h2 { font-size: 15px; }
.mark {
  width: 32px; height: 32px; flex: none;
  display: grid; place-items: center;
  border-radius: var(--radius);
  background: var(--c-accent);
  color: #fff;
}
.fields { display: flex; flex-direction: column; gap: 11px; }
.field > span { font-size: 12px; font-weight: 500; color: var(--c-text-muted); }
</style>
