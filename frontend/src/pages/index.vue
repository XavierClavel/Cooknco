<!--
  The public landing page, and the only page at the root of the site a signed-out visitor
  ever sees.

  It exists because `/` used to resolve to `/home` — a personalised feed — which the router
  then bounced to `/login`. So the site's most important URL rendered a login form with an
  empty body: nothing for a search engine to index, and no link out of it to anything else.
  A member has no use for this page and is sent on to their feed by the router.

  Two things here are load-bearing rather than decorative:

  - **The recipe links are real `<a href>` elements** (the recipe sticker, `Recipe.vue`,
    renders one). A card that only navigates with `@click` and `toViewRecipe` is fine for a
    person and invisible to a crawler: there is no href to follow, so the recipe pages stay
    orphans however well their tags are written. `sitemap.xml` lists them all, but a link from a page that is itself
    linked-to is what actually carries weight.
  - **The headings are `<h1>`/`<h2>`, not `v-card-title`.** Vuetify's titles render as divs,
    which say nothing about what this page is about.
-->
<template>
  <div class="landing">

    <section class="landing__hero">
      <img :src="logo" alt="Cook&Co" class="landing__logo" />
      <h1 class="landing__title">Cook&amp;Co</h1>
      <p class="landing__tagline">{{ $t('landing_tagline') }}</p>

      <div class="landing__cta">
        <a class="sk-btn sk-btn--coral sk-btn--lg" href="/signup">{{ $t('sign_up') }}</a>
        <a class="sk-btn sk-btn--paper sk-btn--lg" href="/login">{{ $t('log_in') }}</a>
      </div>
    </section>

    <section class="landing__section">
      <h2 class="sk-h2">{{ $t('landing_what_is_it') }}</h2>
      <p class="landing__intro">{{ $t('landing_intro') }}</p>

      <div class="landing__features">
        <article v-for="feature in features" :key="feature.title" class="sticker sk-pad landing__feature">
          <span class="landing__feature-icon" :style="{ background: feature.color }">
            <v-icon :icon="feature.icon" />
          </span>
          <h3 class="sk-h3">{{ $t(feature.title) }}</h3>
          <p>{{ $t(feature.text) }}</p>
        </article>
      </div>
    </section>

    <section v-if="recipes.length" class="landing__section">
      <h2 class="sk-h2">{{ $t('landing_latest_recipes') }}</h2>
      <!-- Each card is a real href (see Recipe.vue), so this is a link a crawler can follow
           and a visitor can middle-click, not a click handler. -->
      <div class="landing__recipes">
        <recipe v-for="recipe in recipes" :key="recipe.id" :recipe="recipe" compact />
      </div>
    </section>

  </div>
</template>

<script lang="ts" setup>
import { ref } from 'vue'
import { listPublicRecipes } from '@/scripts/recipes'
import logo from '@/assets/logo.png'

const recipes = ref([])

const features = [
  { icon: 'mdi-pencil-outline', color: 'var(--mustard)', title: 'landing_feature_write_title', text: 'landing_feature_write_text' },
  { icon: 'mdi-book-open-variant-outline', color: 'var(--sky)', title: 'landing_feature_cookbooks_title', text: 'landing_feature_cookbooks_text' },
  { icon: 'mdi-account-multiple-outline', color: 'var(--blush)', title: 'landing_feature_share_title', text: 'landing_feature_share_text' },
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
.landing {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 40px;
  padding-bottom: 8px;
}

.landing__hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 12px;
}

.landing__logo {
  display: block;
  width: min(160px, 45vw);
  height: auto;
}

.landing__title {
  margin: 4px 0 0;
  font-size: clamp(36px, 6vw, 48px);
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.01em;
}

.landing__tagline {
  max-width: 460px;
  margin: 0;
  font-size: clamp(16px, 2.2vw, 19px);
  line-height: 1.4;
}

.landing__cta {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
  margin-top: 8px;
}

.landing__cta .sk-btn {
  box-shadow: var(--shadow);
  min-width: 96px;
}

.landing__section {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.landing__intro {
  max-width: 640px;
  margin: 0;
  font-size: 15px;
  line-height: 1.55;
}

.landing__features {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  padding: 0 var(--lift) var(--lift) 0;
}

.landing__feature {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.landing__feature p {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
}

.landing__feature-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: var(--line) solid var(--ink);
  border-radius: 8px;
  color: var(--ink);
  margin-bottom: 4px;
}

.landing__feature-icon .v-icon {
  font-size: 18px;
}

.landing__recipes {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px 16px;
  padding: 0 var(--lift) var(--lift) 0;
}

@media (max-width: 860px) {
  .landing__features { grid-template-columns: 1fr; }
  .landing__recipes { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 600px) {
  .landing__recipes { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
