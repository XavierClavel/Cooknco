<script lang="ts" setup>

import {listUsers} from "@/scripts/users";
import {listRecipes} from "@/scripts/recipes";
import {useAuthStore} from "@/stores/auth";
import {searchIngredients} from "@/scripts/ingredients";
import {listCookbooks} from "@/scripts/cookbooks";

const route = useRoute()
const router = useRouter()
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

function onChipSelected() {
  console.log(selection.value)
  router.push({ path: '/search', query: { search: searchQuery.value, filter: selection.value } })
}


</script>
<template>
  <v-card-title
    v-if="!ingredients?.length && !users?.length && !recipes?.length && !cookbooks?.length"
    class="text-black text-h2 font-weight-bold my-8"
  >{{$t("no_result")}}</v-card-title>
  <div class="d-flex flex-wrap justify-center">
    <v-chip-group
      v-if="ingredients?.length || users?.length || recipes?.length || cookbooks?.length"
      v-model="selection"
      selected-class="v-chip--selected v-chip--variant-flat v-chip--color-black"
      variant="outlined"
      mandatory
      @update:modelValue="onChipSelected"
    >
      <v-chip :text="$t('everything')" value="everything"></v-chip>
      <v-chip :text="$t('recipes')" value="recipes"></v-chip>
      <v-chip :text="$t('ingredients')" value="ingredients"></v-chip>
      <v-chip :text="$t('users')" value="users"></v-chip>
      <v-chip :text="$t('cookbooks')" value="cookbooks"></v-chip>
    </v-chip-group>
  </div>
  <div v-if="selection == 'everything'">
  <v-card
    class="my-5"
    color="transparent"
    style="border:0"
    variant="flat"
    v-if="users?.length"
  >
    <v-card-title
      class="text-black text-h2 font-weight-bold clickable-title"
      @click="selection = 'users'"
    >{{$t("users")}}</v-card-title>
  </v-card>

    <v-row class="mx-5">
      <user v-for="user in users" :user="user"></user>
    </v-row>
  <v-card
    class="my-5 mt-16"
    color="transparent"
    style="border:0"
    variant="flat"
    v-if="recipes?.length"
  >
    <v-card-title
      class="text-black text-h2 font-weight-bold clickable-title"
      @click="selection = 'recipes'"
    >{{$t("recipes")}}</v-card-title>
  </v-card>
    <v-row class="mx-5 ga-4">
      <recipe v-for="recipe in recipes" :recipe="recipe"></recipe>
    </v-row>
  <v-card
    class="my-5 mt-16"
    color="transparent"
    style="border:0"
    variant="flat"
    v-if="ingredients?.length"
  >
    <v-card-title
      class="text-black text-h2 font-weight-bold clickable-title"
      @click="selection = 'ingredients'"
    >{{$t("ingredients")}}</v-card-title>
  </v-card>

  <v-row class="mx-5">
    <ingredient v-for="ingredient in ingredients" :ingredient="ingredient"></ingredient>
  </v-row>

  <v-card
    class="my-5 mt-16"
    color="transparent"
    style="border:0"
    variant="flat"
    v-if="cookbooks?.length"
  >
    <v-card-title
      class="text-black text-h2 font-weight-bold clickable-title"
      @click="selection = 'cookbooks'"
    >{{$t("cookbooks")}}
    </v-card-title>
  </v-card>

  <v-row class="mx-5 mb-16">
    <cookbook v-for="cookbook in cookbooks" :cookbook="cookbook"></cookbook>
  </v-row>
  </div>

  <div v-if="selection == 'recipes'">
    <recipes-list :query="`user=${userId}&likedBy=${userId}&cookbookUser=${userId}&followedBy=${userId}&search=${searchQuery}&sort=BEST_MATCH`"></recipes-list>
  </div>

  <div v-if="selection == 'cookbooks'">
    <cookbooks-grid :query="`user=${userId}&query=${searchQuery}`"></cookbooks-grid>
  </div>

  <div v-if="selection == 'users'">
    <users-grid :query="`query=${searchQuery}`"></users-grid>
  </div>

  <div v-if="selection == 'ingredients'">
    <ingredients-grid :query="`query=${searchQuery}`"></ingredients-grid>
  </div>

</template>
<style>
.v-chip.v-chip--selected {
  background-color: black !important;
  color: white !important;
}

.clickable-title {
  cursor: pointer;
  transition: text-decoration 0.2s ease;
}

.clickable-title:hover {
  text-decoration: underline;
}
</style>
