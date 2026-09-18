<template>
  <v-container fluid class="">
  <v-footer height="40" class="ml-n4 mb-n2" rounded="lg" style="width: 120%; max-width: calc(100% + 30px);" >
    <a
      v-for="item in items"
      :key="item.title"
      :href="item.href"
      :title="item.title"
      class="d-inline-block mx-2 social-link"
      rel="noopener noreferrer"
      target="_blank"
    >
      <v-icon
        :icon="item.icon"
        :size="item.icon === '$vuetify' ? 24 : 16"
      />
    </a>

    <!--
      The privacy policy has to be reachable from the product itself, not only from the
      store listing, and the footer is the one place on every page. Router links rather than
      hrefs: both pages are part of the app and load without a reload.
    -->
    <router-link class="text-caption text-decoration-none on-surface mx-2" to="/privacy">
      {{ $t('privacy_policy') }}
    </router-link>
    <router-link class="text-caption text-decoration-none on-surface mx-2 d-none d-sm-inline-block" to="/account-deletion">
      {{ $t('account_deletion') }}
    </router-link>

    <div
      class="text-caption text-disabled"
      style="position: absolute; right: 16px;"
    >
      &copy; {{ (new Date()).getFullYear() }} <span class="d-none d-sm-inline-block">Xavier Clavel • Léo Duclos</span>
      —
      <a
        class="text-decoration-none on-surface"
        href="https://vuetifyjs.com/about/licensing/"
        rel="noopener noreferrer"
        target="_blank"
      >
        MIT License
      </a>
    </div>
  </v-footer>
  </v-container>
</template>

<script setup lang="ts">
  const items = [
    {
      title: 'Cooknco GitHub',
      icon: `mdi-github`,
      href: 'https://github.com/XavierClavel/RecipeManager',
    },
  ]
</script>

<style scoped lang="sass">
  .social-link :deep(.v-icon)
    color: rgba(var(--v-theme-on-background), var(--v-disabled-opacity))
    text-decoration: none
    transition: .2s ease-in-out

    &:hover
      color: rgba(25, 118, 210, 1)
</style>
