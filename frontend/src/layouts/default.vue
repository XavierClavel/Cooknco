<template>
  <!--
    The shell (App.vue) draws the sidebar or the public logo; a page only adds itself and the
    footer beneath it.
  -->
  <div class="page-frame">
    <div class="page-frame__body">
      <router-view />
    </div>
    <AppFooter />
  </div>
</template>

<style scoped>
.page-frame {
  display: flex;
  flex-direction: column;
  gap: 28px;
  width: 100%;
  flex: 1;
}

.page-frame__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
</style>
