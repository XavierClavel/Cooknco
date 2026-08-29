<script setup lang="ts">
import {listRecipes} from "@/scripts/recipes";

const props = defineProps({
  query: {
    type: String,
    required: false,
  },
})

const recipes = ref<object[]>([])
const router = useRouter()
const route = useRoute()
const routesToCheck = ["/recipe/list", "/ingredient/view", "/cookbook/recipes"]
const currentPage = ref(0)
const isLoading = ref(false)
const allRecipesLoaded = ref(false)
const infiniteScrollKey = ref(0)
const refreshing = ref(true)
const errorMessage = ref(null)


const loadMore = async ({ done }: { done: () => void }) => {
  if (isLoading.value || allRecipesLoaded.value) return
  isLoading.value = true
  try {
    await updateGrid()
  } catch (error) {
    console.error(error)
  } finally {
    isLoading.value = false
    done()
  }
}

const updateGrid = async() => {
  errorMessage.value = null
  try {
    const query = props.query || (route.fullPath.split('?')[1] ?? '')
    const response = await listRecipes(query, currentPage.value, 20)
    if (response.data.length === 0) {
      allRecipesLoaded.value = true
      if (recipes.value.length == 0) {
        throw "no_recipe_to_display"
      }
    } else {
      recipes.value.push(...response.data)
      currentPage.value++
    }
  } catch (e) {
    console.log(e)
    errorMessage.value = e
  } finally {
    isLoading.value = false
    refreshing.value = false
  }
}


const removeAfterEach = router.afterEach((to, from) => {
  if (props.query) return
  if (!routesToCheck.includes(router.currentRoute.value.name)) {
    return
  }

  allRecipesLoaded.value = false
  recipes.value = []
  currentPage.value = 0
  infiniteScrollKey.value++
  refreshing.value = true
  updateGrid()
})

// Cleanup when the component is unmounted
onUnmounted(() => {
  removeAfterEach() // Remove the navigation guard
})

updateGrid()

</script>

<template>
  <v-infinite-scroll
    :key="infiniteScrollKey"
    @load="loadMore"
    :disabled="isLoading || allRecipesLoaded"
    side="end"
    margin="200"
  >
    <v-container fluid>
      <div class="recipe-grid">
        <recipe
          v-for="recipe in recipes"
          :key="recipe.id"
          :recipe="recipe"
        />
      </div>
    </v-container>

    <template v-slot:loading>
      <v-progress-circular
        v-if="isLoading"
        indeterminate
        color="black"
        class="my-5 d-flex mx-auto"
      />
    </template>
  </v-infinite-scroll>

  <div  v-if="!refreshing && !isLoading">
    <error :error="errorMessage"></error>
  </div>
</template>

<style scoped>
.recipe-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  justify-content: center; /* center whole grid */

  padding: 0 clamp(16px, 8vw, 96px);
  box-sizing: border-box;
  margin: 0 auto;

  column-gap: 12px;  /* horizontal space */
  row-gap: 12px;     /* vertical space */
}

</style>
