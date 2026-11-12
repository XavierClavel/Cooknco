<!--
  The narrow paper sticker every signed-out form and notice sits in: signing in and up, the
  password reset steps, the verification and logout notices. One title, the page's content,
  and an optional line under the card ("New here? Create an account").

  The helper classes below are deliberately not scoped: they dress what the pages put in the
  slots, which scoped styles of this component would not reach.
-->
<template>
  <div class="auth">
    <section class="auth__card sticker">
      <h1 class="auth__title">{{ title }}</h1>
      <slot />
    </section>
    <p v-if="$slots.below" class="auth__below">
      <slot name="below" />
    </p>
  </div>
</template>

<script setup lang="ts">
defineProps({
  title: {
    type: String,
    required: true,
  },
})
</script>

<style>
.auth {
  width: 100%;
  max-width: 400px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.auth__card {
  width: 100%;
  padding: 20px 18px 22px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.auth__title {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
  margin: 0;
}

.auth__below {
  margin: 0;
  font-size: 14px;
  text-align: center;
  color: var(--ink);
}

.auth__below a {
  color: var(--ink);
  font-weight: 700;
  text-decoration: underline;
}

.auth-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.45;
  white-space: pre-line;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.auth-form .v-input {
  margin: 0 !important;
}

/* Two buttons side by side: Cancel and Send, Sign up and Log in */
.auth-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

@media (max-width: 360px) {
  .auth-actions { grid-template-columns: 1fr; }
}

/* The main action of the card: full width, with the sticker's own shadow */
.auth-btn {
  box-shadow: var(--shadow);
}

.auth-google img {
  width: 20px;
  height: 20px;
}

.auth-center {
  align-self: center;
  font-size: 13px;
}

.auth__card .error-note.sticker {
  font-size: 14px;
  padding: 12px 14px;
  box-shadow: var(--shadow-sm);
}
</style>
