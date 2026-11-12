<template>
  <footer class="footer sticker sticker--sm">
    <nav class="footer__links">
      <a
        class="footer__github"
        href="https://github.com/XavierClavel/RecipeManager"
        rel="noopener noreferrer"
        target="_blank"
      >GitHub</a>
      <!--
        The privacy policy has to be reachable from the product itself, not only from the
        store listing, and the footer is the one place on every page. Router links rather than
        hrefs: both pages are part of the app and load without a reload.
      -->
      <router-link to="/privacy">{{ $t('privacy_policy') }}</router-link>
      <router-link to="/account-deletion">{{ $t('account_deletion') }}</router-link>
    </nav>

    <div class="footer__credit">
      &copy; {{ (new Date()).getFullYear() }} Xavier Clavel • Léo Duclos —
      <a
        href="https://github.com/XavierClavel/RecipeManager/blob/develop/LICENSE.md"
        rel="noopener noreferrer"
        target="_blank"
      >MIT License</a>
    </div>
  </footer>
</template>

<style scoped>
.footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 24px;
  width: 100%;
  padding: 10px 16px;
  font-size: 12px;
  box-shadow: none;
}

.footer__links {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 18px;
}

.footer a {
  color: var(--ink);
  text-decoration: underline;
}

.footer__github {
  font-weight: 700;
}

.footer__credit a {
  text-decoration: none;
}
</style>
