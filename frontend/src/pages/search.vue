<script lang="ts" setup>

import {listUsers} from "@/scripts/users";
import {listRecipes} from "@/scripts/recipes";
import {useAuthStore} from "@/stores/auth";
import {searchIngredients} from "@/scripts/ingredients";
import {listCookbooks} from "@/scripts/cookbooks";
import router from "@/router";

const route = useRoute()
const searchQuery = ref(route.query.search || '')
const users = ref([])
const recipes = ref([])
const ingredients = ref([])
const cookbooks = ref([])
const selection = ref(route.query.filter || "everything")

const authStore = useAuthStore()
const userId = computed(() => authStore.id)

async function updateSearch() {
  console.log(searchQuery.value)
  await updateUsers()
  await updateRecipes()
  await updateIngredients()
  await updateCookbooks()
}

async function updateCookbooks() {
  const response = await listCookbooks(`query=${searchQuery.value}`, 0, 4);
  cookbooks.value = response.data
}

async function updateUsers() {
  const response = await listUsers(`query=${searchQuery.value}`, 0, 6);
  users.value = response.data.items;
  console.log(users.value)
}

async function updateRecipes() {
  const response = await listRecipes(`user=${userId.value}&likedBy=${userId.value}&cookbookUser=${userId.value}&followedBy=${userId.value}&search=${searchQuery.value}&sort=BEST_MATCH`, 0, 4)
  recipes.value = response.data
  console.log(recipes.value)
}

async function updateIngredients() {
  const response = await searchIngredients(`query=${searchQuery.value}`, 0, 6)
  ingredients.value = response.data.items
  console.log(ingredients.value)
}

watch(() => route.query.search, (newQuery) => {
  searchQuery.value = newQuery || ''
  updateSearch()
})

updateSearch()

const filters = ["everything", "recipes", "ingredients", "users", "cookbooks"]

function selectFilter(filter) {
  selection.value = filter
  onChipSelected()
}

function onChipSelected() {
  console.log(selection.value)
  router.push({ name: '/search', query: { search: searchQuery.value, filter: selection.value } })
}


</script>
<template>
  <div class="sk-page">
    <h1
      v-if="!ingredients?.length && !users?.length && !recipes?.length && !cookbooks?.length"
      class="sk-title"
    >{{$t("no_result")}}</h1>

    <div
      v-if="ingredients?.length || users?.length || recipes?.length || cookbooks?.length"
      class="sk-chip-row search__filters"
      role="group"
      :aria-label="$t('search_filters')"
    >
      <button
        v-for="filter in filters"
        :key="filter"
        type="button"
        class="sk-chip"
        :aria-pressed="selection == filter"
        @click="selectFilter(filter)"
      >{{ $t(filter) }}</button>
    </div>

    <template v-if="selection == 'everything'">
      <section v-if="users?.length" class="search__section">
        <h2 class="search__label">
          <button type="button" class="sticker sticker--white sticker--sm" @click="selection = 'users'">{{$t("users")}}</button>
        </h2>
        <div class="sk-recipe-grid">
          <user v-for="user in users" :key="user.id" :user="user"></user>
        </div>
      </section>

      <section v-if="recipes?.length" class="search__section">
        <h2 class="search__label">
          <button type="button" class="sticker sticker--white sticker--sm" @click="selection = 'recipes'">{{$t("recipes")}}</button>
        </h2>
        <div class="sk-recipe-grid">
          <recipe v-for="recipe in recipes" :key="recipe.id" :recipe="recipe"></recipe>
        </div>
      </section>

      <section v-if="ingredients?.length" class="search__section">
        <h2 class="search__label">
          <button type="button" class="sticker sticker--white sticker--sm" @click="selection = 'ingredients'">{{$t("ingredients")}}</button>
        </h2>
        <div class="sk-recipe-grid">
          <ingredient v-for="ingredient in ingredients" :key="ingredient.id" :ingredient="ingredient"></ingredient>
        </div>
      </section>

      <section v-if="cookbooks?.length" class="search__section">
        <h2 class="search__label">
          <button type="button" class="sticker sticker--white sticker--sm" @click="selection = 'cookbooks'">{{$t("cookbooks")}}</button>
        </h2>
        <div class="sk-recipe-grid">
          <cookbook v-for="cookbook in cookbooks" :key="cookbook.id" :cookbook="cookbook" variant="card"></cookbook>
        </div>
      </section>
    </template>

    <div v-if="selection == 'recipes'">
      <recipes-list :query="`user=${userId}&likedBy=${userId}&cookbookUser=${userId}&followedBy=${userId}&search=${searchQuery}&sort=BEST_MATCH`"></recipes-list>
    </div>

    <div v-if="selection == 'cookbooks'">
      <cookbooks-grid :query="`user=${userId}&query=${searchQuery}`" variant="card"></cookbooks-grid>
    </div>

    <div v-if="selection == 'users'">
      <users-grid :query="`query=${searchQuery}`"></users-grid>
    </div>

    <div v-if="selection == 'ingredients'">
      <ingredients-grid :query="`query=${searchQuery}`"></ingredients-grid>
    </div>
  </div>
</template>

<style scoped>
.search__filters {
  justify-content: center;
}

.search__section {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.search__label {
  margin: 0;
}

.search__label button {
  padding: 6px 16px;
  border-radius: 999px;
  font-family: var(--font);
  font-size: 15px;
  font-weight: 700;
}

.search__label button:focus-visible {
  outline: 3px solid var(--mustard);
  outline-offset: 2px;
}
</style>
