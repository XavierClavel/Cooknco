<!--
  The public landing page, and the only page at the root of the site a signed-out visitor
  ever sees.

  It exists because `/` used to resolve to `/home` — a personalised feed — which the router
  then bounced to `/login`. So the site's most important URL rendered a login form with an
  empty body: nothing for a search engine to index, and no link out of it to anything else.
  A member has no use for this page and is sent on to their feed by the router.

  Two things here are load-bearing rather than decorative:

  - **The recipe links are real `<a href>` elements.** Everywhere else in the app a card
    navigates with `@click` and `toViewRecipe`, which is fine for a person and invisible to a
    crawler: there is no href to follow, so the recipe pages stay orphans however well their
    tags are written. `sitemap.xml` lists them all, but a link from a page that is itself
    linked-to is what actually carries weight.
  - **The headings are `<h1>`/`<h2>`, not `v-card-title`.** Vuetify's titles render as divs,
    which say nothing about what this page is about.
-->
<template>
  <v-container class="py-8" style="max-width: 1100px">

    <section class="text-center mb-12">
      <v-img
        :src="logo"
        alt="Cook&Co"
        height="96"
        class="mx-auto mb-4"
        contain
      />
      <h1 class="text-h3 text-sm-h2 font-weight-bold mb-3">Cook&amp;Co</h1>
      <p class="text-h6 text-sm-h5 font-weight-thin mb-6">{{ $t('landing_tagline') }}</p>

      <div class="d-flex flex-wrap justify-center ga-3">
        <v-btn color="primary" size="large" href="/signup">{{ $t('sign_up') }}</v-btn>
        <v-btn variant="outlined" size="large" href="/login">{{ $t('log_in') }}</v-btn>
      </div>
    </section>

    <section class="mb-12">
      <h2 class="text-h5 font-weight-bold mb-4">{{ $t('landing_what_is_it') }}</h2>
      <p class="text-body-1 mb-6">{{ $t('landing_intro') }}</p>

      <v-row>
        <v-col v-for="feature in features" :key="feature.title" cols="12" md="4">
          <v-card variant="tonal" class="h-100 pa-4">
            <v-icon :icon="feature.icon" size="32" class="mb-2" />
            <h3 class="text-h6 font-weight-bold mb-2">{{ $t(feature.title) }}</h3>
            <p class="text-body-2">{{ $t(feature.text) }}</p>
          </v-card>
        </v-col>
      </v-row>
    </section>

    <section v-if="recipes.length">
      <h2 class="text-h5 font-weight-bold mb-4">{{ $t('landing_latest_recipes') }}</h2>
      <v-row>
        <v-col
          v-for="recipe in recipes"
          :key="recipe.id"
          cols="6"
          sm="4"
          md="3"
        >
          <!-- A real href, so this is a link a crawler can follow and a visitor can
               middle-click, not a click handler. -->
          <a :href="`/recipe/view?id=${recipe.id}`" class="recipe-link">
            <v-card class="h-100">
              <v-img
                :src="getRecipeThumbnailUrl(recipe.id, recipe.version)"
                :alt="recipe.title"
                height="150px"
                color="surface-variant"
                cover
              />
              <v-card-text class="text-subtitle-2 font-weight-bold text-truncate">
                {{ recipe.title }}
              </v-card-text>
            </v-card>
          </a>
        </v-col>
      </v-row>
    </section>

  </v-container>
</template>

<script lang="ts" setup>
import { ref } from 'vue'
import { listPublicRecipes } from '@/scripts/recipes'
import { getRecipeThumbnailUrl } from '@/scripts/common'
import logo from '@/assets/logo.png'

const recipes = ref([])

const features = [
  { icon: 'mdi-notebook-edit-outline', title: 'landing_feature_write_title', text: 'landing_feature_write_text' },
  { icon: 'mdi-book-open-page-variant-outline', title: 'landing_feature_cookbooks_title', text: 'landing_feature_cookbooks_text' },
  { icon: 'mdi-account-group-outline', title: 'landing_feature_share_title', text: 'landing_feature_share_text' },
]

/**
 * A failure here leaves the section out rather than the page: everything above it is what
 * the page is for, and a visitor who arrived from a search should not get an error because
 * the API is having a moment.
 */
const loadRecipes = async () => {
  try {
    const response = await listPublicRecipes(8)
    recipes.value = response.data
  } catch (error) {
    console.error(error)
  }
}

loadRecipes()
</script>

<style scoped>
.recipe-link {
  text-decoration: none;
  color: inherit;
  display: block;
  height: 100%;
}
</style>
