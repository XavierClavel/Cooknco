<script setup lang="ts">
import {listCookbooks} from "@/scripts/cookbooks";
import Cookbook from "@/components/Cookbook.vue";

const props = defineProps({
  query: {
    type: String,
    required: false,
  },
  /** How each cookbook is drawn: see `Cookbook.vue`. */
  variant: {
    type: String,
    default: "row",
  },
})

const cookbooks = ref<object[]>([])
const router = useRouter()
const routesToCheck = ["/cookbook"]
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
    const response = await listCookbooks(query, currentPage.value, 20)
    if (response.data.length === 0) {
      allDataLoaded.value = true
      if (cookbooks.value.length == 0) {
        throw "no_cookbook_to_display"
      }
    } else {
      cookbooks.value.push(...response.data)
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
  cookbooks.value = []
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
    <div :class="variant === 'card' ? 'sk-recipe-grid' : 'cookbook-grid'">
      <cookbook
        v-for="cookbook in cookbooks"
        :key="cookbook.id"
        :cookbook="cookbook"
        :variant="variant"
      ></cookbook>
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

<style scoped>
.cookbook-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 280px), 1fr));
  gap: 18px;
  padding: 0 var(--lift) var(--lift) 0;
}
</style>
