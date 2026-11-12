<template>
  <div class="sk-page home">
    <header>
      <h1 class="sk-title">{{ $t("welcome") }}, {{ username }}!</h1>
      <p class="sk-subtitle">{{ $t("welcome_subtext") }}</p>
    </header>

    <v-infinite-scroll
      @load="loadMore"
      :disabled="isLoading || allRecipesLoaded"
      side="end"
      margin="200"
      class="home__scroll"
    >
      <!-- One group per day: an ink line, a coral circle with the count, the day as a pill -->
      <ol class="timeline">
        <li
          v-for="(timelineRecipe, date) in timelineRecipes"
          :key="date"
          class="timeline__group"
        >
          <div class="timeline__head">
            <span class="timeline__count">{{ timelineRecipe.length }}</span>
            <h2 class="sk-pill sk-pill--lg timeline__label">{{ $t(date) }}</h2>
          </div>
          <div class="sk-recipe-grid timeline__grid">
            <recipe v-for="recipe in timelineRecipe" :key="recipe.id" :recipe="recipe"></recipe>
          </div>
        </li>
      </ol>

      <template v-slot:loading>
        <v-progress-circular
          v-if="isLoading"
          indeterminate
          color="black"
          class="my-5 d-flex mx-auto"
        />
      </template>
    </v-infinite-scroll>
  </div>
</template>

<script lang="ts" setup>

import {listRecipes} from "@/scripts/recipes";
import {useAuthStore} from "@/stores/auth";
import {getLocale} from "@/scripts/localization";

const recipes = ref([])
const timelineRecipes = ref()
const authStore = useAuthStore()
const userId = computed(() => authStore.id)
const username = computed(() => authStore.username)
const currentPage = ref(0)
const isLoading = ref(false)
const allRecipesLoaded = ref(false)

const dayNames = ["sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"];



const loadMore = async ({ done }: { done: () => void }) => {
  if (isLoading.value || allRecipesLoaded.value) return
  isLoading.value = true
  try {
    await updateGrid()
    isLoading.value = false
  } catch (error) {
    console.error(error)
  } finally {
    isLoading.value = false
    done()
  }
}

const updateGrid = async() => {
  const response = await listRecipes(`?user=${userId.value}&followedBy=${userId.value}&sort=DATE_DESCENDING`, currentPage.value, 20)
  if (response.data.length === 0) {
    allRecipesLoaded.value = true
  } else {
    recipes.value.push(...response.data)
    currentPage.value++
    timelineRecipes.value = recipes.value.reduce((acc, recipe) => {
      const date = new Date(recipe.creationDate * 1000)
      let groupKey = date.toLocaleDateString()
      switch (groupKey) {
        case new Date().toLocaleDateString():
          groupKey = "today"
          break

        case new Date((new Date()).valueOf() - 1000*60*60*24).toLocaleDateString():
          groupKey = "yesterday"
          break

        case new Date((new Date()).valueOf() - 2 * 1000*60*60*24).toLocaleDateString():
          groupKey = dayNames[new Date(recipe.creationDate * 1000).getDay()]
          break

        case new Date((new Date()).valueOf() - 3 * 1000*60*60*24).toLocaleDateString():
          groupKey = dayNames[new Date(recipe.creationDate * 1000).getDay()]
          break

        case new Date((new Date()).valueOf() - 4 * 1000*60*60*24).toLocaleDateString():
          groupKey = dayNames[new Date(recipe.creationDate * 1000).getDay()]
          break

        case new Date((new Date()).valueOf() - 5 * 1000*60*60*24).toLocaleDateString():
          groupKey = dayNames[new Date(recipe.creationDate * 1000).getDay()]
          break

        case new Date((new Date()).valueOf() - 6 * 1000*60*60*24).toLocaleDateString():
          groupKey = dayNames[new Date(recipe.creationDate * 1000).getDay()]
          break

        default:
          groupKey = new Intl.DateTimeFormat(getLocale(), {
            day: 'numeric',
            month: 'long',
            year: 'numeric'
          }).format(date);
          break

      }

      if (!acc[groupKey]) {
        acc[groupKey] = [];
      }

      acc[groupKey].push(recipe);
      return acc;
    }, {}); // Initialize with a Map
  }
}

updateGrid()

</script>

<style scoped>
.home__scroll {
  overflow: visible !important;
}

.timeline {
  list-style: none;
  margin: 0;
  padding: 0;
}

.timeline__group {
  --dot: 36px;
  --indent: 52px;
  position: relative;
  padding: 0 0 32px var(--indent);
}

/* The line runs from this day's circle down to the next one's */
.timeline__group:not(:last-child)::before {
  content: "";
  position: absolute;
  left: calc(var(--dot) / 2 - 1px);
  top: calc(var(--dot) / 2);
  bottom: 0;
  border-left: var(--line) solid var(--ink);
}

.timeline__head {
  display: flex;
  align-items: center;
  min-height: var(--dot);
  margin-bottom: 12px;
}

.timeline__count {
  position: absolute;
  left: 0;
  top: 0;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--dot);
  height: var(--dot);
  border: var(--line) solid var(--ink);
  border-radius: 50%;
  background: var(--coral);
  color: var(--white);
  font-weight: 700;
  font-size: 14px;
}

.timeline__label {
  margin: 0;
}

@media (max-width: 600px) {
  .timeline__group {
    --dot: 30px;
    --indent: 40px;
  }

  .timeline__grid {
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
    gap: 16px 12px;
  }
}
</style>
