<script setup lang="ts">
import {searchIngredients} from "@/scripts/ingredients";
import Ingredient from "@/components/Ingredient.vue";

const props = defineProps({
  query: {
    type: String,
    required: false,
  },
})

const ingredients = ref<object[]>([])
const router = useRouter()
const routesToCheck = ["/ingredient"]
const currentPage = ref(0)
const isLoading = ref(false)
const allDataLoaded = ref(false)
const infiniteScrollKey = ref(0)
const refreshing = ref(true)
const errorMessage = ref(null)


const loadMore = async ({ done }: { done: () => void }) => {
  if (isLoading.value || allDataLoaded.value) return
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
    const query = props.query || window.location.search
    const response = await searchIngredients(query, currentPage.value, 20)
    if (response.data.items.length === 0) {
      allDataLoaded.value = true
      if (ingredients.value.length == 0) {
        throw "no_ingredient_to_display"
      }
    } else {
      ingredients.value.push(...response.data.items)
      currentPage.value++
      console.log(currentPage.value)
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

  allDataLoaded.value = false
  ingredients.value = []
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
    :disabled="isLoading || allDataLoaded"
    side="end"
    margin="200"
  >
    <div class="sk-recipe-grid">
      <ingredient :ingredient="ingredient" v-for="ingredient in ingredients" :key="ingredient.id"></ingredient>
    </div>

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
