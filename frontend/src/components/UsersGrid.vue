<script setup lang="ts">

import {listUsers} from "@/scripts/users";

const props = defineProps({
  query: {
    type: String,
    required: false,
  },
})

const users = ref<object[]>([])
const router = useRouter()
const route = useRoute()
const routesToCheck = ["/user"]
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
    const query = props.query || (route.fullPath.split('?')[1] ?? '')
    const response = await listUsers(query, currentPage.value, 20)
    if (response.data.items.length === 0) {
      allDataLoaded.value = true
      if (users.value.length == 0) {
        throw "no_user_to_display"
      }
    } else {
      users.value.push(...response.data.items)
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
  users.value = []
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
    <v-card class="d-flex pa-0 ma-0" color="transparent" variant="flat" style="border:0 !important">
      <v-row class="ma-1">
        <user :user="user" v-for="user in users" :key="user.id"></user>
      </v-row>
    </v-card>

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
