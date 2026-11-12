<template>
  <div class="sk-page">
    <error :error="errors"></error>

    <section v-if="!errors" class="cookbook-head sticker sk-pad-lg">
      <div class="cookbook-head__top">
        <div class="cookbook-head__cover sk-placeholder">
          <img
            v-if="cookbook.id"
            :src="getCookbookIconUrl(cookbook.id, cookbook.version)"
            :alt="cookbook.title"
          />
        </div>

        <div class="cookbook-head__text">
          <h1 class="sk-title">{{ cookbook.title }}</h1>
          <p v-if="cookbook.description" class="cookbook-head__description">{{ cookbook.description }}</p>

          <dl class="cookbook-head__stats">
            <div>
              <dt class="sk-eyebrow">{{ $t("recipes") }}</dt>
              <dd>{{ cookbook.recipesCount ?? "–" }}</dd>
            </div>
            <div>
              <dt class="sk-eyebrow">{{ $t("members") }}</dt>
              <dd>{{ cookbook.usersCount ?? "–" }}</dd>
            </div>
          </dl>
        </div>
      </div>

      <div class="cookbook-head__actions">
        <button type="button" class="sk-btn" @click="toEditCookbook(cookbookId)">
          <v-icon>mdi-pencil-outline</v-icon>
          {{ $t("edit") }}
        </button>

        <premium-only>
          <button type="button" class="sk-btn" :disabled="exporting" @click="onExport()">
            <v-progress-circular v-if="exporting" indeterminate size="18" width="2"></v-progress-circular>
            <v-icon v-else>mdi-tray-arrow-down</v-icon>
            {{ $t("export_pdf") }}
          </button>
        </premium-only>

        <v-dialog max-width="500">
          <template v-slot:activator="{ props: activatorProps }">
            <button type="button" class="sk-btn" v-bind="activatorProps">
              <v-icon>mdi-door-open</v-icon>
              {{ $t("leave") }}
            </button>
          </template>

          <template v-slot:default="{ isActive }">
            <div class="sticker sk-pad-lg sk-stack" role="alertdialog">
              <h2 class="sk-h2">{{ $t("leaveCookbookTitle") }}</h2>
              <p class="ma-0">{{ $t("leaveCookbookDescription") }}</p>
              <div class="sk-row justify-end">
                <button type="button" class="sk-btn" @click="isActive.value = false">{{ $t("cancel") }}</button>
                <button type="button" class="sk-btn sk-btn--coral" @click="() => leave()">{{ $t("leave") }}</button>
              </div>
            </div>
          </template>
        </v-dialog>
      </div>
    </section>

    <v-snackbar
      v-model="snackbar"
      :timeout="4000"
    >
      {{ snackbarMessage }}
      <template v-slot:actions>
        <button
          type="button"
          class="sk-icon-btn sk-icon-btn--white"
          :aria-label="$t('close')"
          @click="snackbar = false"
        >
          <v-icon>mdi-close</v-icon>
        </button>
      </template>
    </v-snackbar>

    <recipes-list v-if="!errors"></recipes-list>
  </div>
</template>

<script lang="ts" setup>
import {useRoute} from "vue-router";
import {ref} from "vue";
import {
  getCookbookIconUrl,
  toEditCookbook,
  toListCookbooks,
  toErrorMessage,
} from "@/scripts/common";
import {downloadCookbook, getCookbook, isAdminOfCookbook, leaveCookbook} from "@/scripts/cookbooks";
import {useAuthStore} from "@/stores/auth";
import {useI18n} from "vue-i18n";
const {t} = useI18n()
const route = useRoute();
let cookbookId = ref(route.query.cookbook)
const isAdmin = ref(false)
const cookbook = ref<object>({
  title: "",
  description: "",
  users: [],
  recipesCount: null,
  usersCount: null,
  version: null,
})
const errors = ref(null)
const snackbar = ref(false)
const snackbarMessage = ref<string>("")
const exporting = ref(false)


if (cookbookId.value != null) {
  getCookbook(cookbookId.value).then (
    function (response) {
      cookbook.value = response.data
      console.log(cookbook.value)
    }).catch(function (error) {
      console.log(error);
      errors.value = error.response.data;
  })
  isAdminOfCookbook(cookbookId.value).then(
    function (response) {
      isAdmin.value = response.data
    }
  )
}

/**
 * Prints the cookbook and saves it.
 *
 * Awaited so the button keeps its spinner for the whole print — a book is a page per
 * recipe through a real browser, which is seconds rather than the blink a single sheet
 * takes. The refusal is shown rather than swallowed: a cookbook too large to print says
 * so, and that is the one failure an operator can do something about.
 */
async function onExport() {
  if (exporting.value) return
  exporting.value = true
  try {
    await downloadCookbook(cookbookId.value)
  } catch (error) {
    snackbarMessage.value = t(toErrorMessage(error))
    snackbar.value = true
  } finally {
    exporting.value = false
  }
}

async function leave() {
  await leaveCookbook(cookbookId.value)
  const authStore = useAuthStore()
  toListCookbooks(authStore.id)
}
</script>

<style scoped>
.cookbook-head {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.cookbook-head__top {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.cookbook-head__cover {
  width: 180px;
  aspect-ratio: 1;
  flex-shrink: 0;
  border: var(--line) solid var(--ink);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.cookbook-head__cover img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cookbook-head__text {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding-top: 4px;
}

.cookbook-head__text .sk-title {
  overflow-wrap: anywhere;
}

.cookbook-head__description {
  margin: 0;
  color: var(--muted);
  white-space: pre-line;
  overflow-wrap: anywhere;
}

.cookbook-head__stats {
  display: flex;
  gap: 28px;
  margin: 12px 0 0;
}

.cookbook-head__stats dd {
  margin: 2px 0 0;
  font-size: 22px;
  font-weight: 700;
}

.cookbook-head__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
}

@media (max-width: 600px) {
  .cookbook-head__top {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  .cookbook-head__stats {
    justify-content: center;
  }
}
</style>
