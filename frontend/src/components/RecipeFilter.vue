<script setup lang="ts">
import {getIngredient, searchIngredients} from "@/scripts/ingredients";
import {useAuthStore} from "@/stores/auth";
import {useI18n} from "vue-i18n";
import ChipRow from "@/components/ChipRow.vue";
import {dishOptions, sortOptions, sourceOptions} from "@/scripts/values";
import {useRoute} from "vue-router";
import {getCookie, setCookie} from "@/scripts/cookies";
import {getLocale} from "@/scripts/localization";
const autocompleteList = ref([])

const selectedDishType = ref([])
const selectedSource = ref([])
const selectedIngredients = ref([])
const selectedSort = ref(null); // Stores the selected sort field
const sortOrder = ref("desc"); // Default order is descending

const { t } = useI18n();

const authStore = useAuthStore()
const comboboxRef = ref()

const route = useRoute();
const router = useRouter();


if(route.query.user) {
  selectedSource.value.push(0)
}
if (route.query.likedBy) {
  selectedSource.value.push(1)
}
if (route.query.cookbookUser) {
  selectedSource.value.push(2)
}
if (route.query.followedBy) {
  selectedSource.value.push(3)
}

selectedDishType.value = route.query.dishClasses?.split(',')?.map(it => dishOptions.findIndex(dish => dish.value == it)) || []

selectedSort.value = route.query.sort?.split('_')[0]
sortOrder.value = route.query.sort?.split('_')[1] == "ASCENDING" ? "asc" : "desc"
const ingredients = route.query.ingredient?.split(',')
if (ingredients) {
  ingredients.forEach(it =>
    getIngredient(it).then(response => {selectedIngredients.value.push(response.data)
  console.log(selectedIngredients.value)}))
}


function onComboUpdate(newVal) {
  selectedIngredients.value = newVal.filter(item =>
    typeof item === 'object'
  )
  updateUrl()
}


function selectFirstMatch() {
  const query = comboboxRef.value?.search
  const match = autocompleteList.value.find(opt => {
    const name = getLocale() == 'fr' ? opt.name_fr : opt.name_en
    return name.toLowerCase().includes(query?.toLowerCase())
    }
  )

  if (match && !selectedIngredients.value.some(sel => sel.id === match.id)) {
    selectedIngredients.value.push(match)
  }

  comboboxRef.value.search = '' // clear the search text

  updateUrl()
}

const toggleSort = (field) => {

  if (selectedSort.value === "RANDOM" && sortOrder.value === "desc" && field === "RANDOM") {
    selectedSort.value = null;
  } else if (selectedSort.value === field) {
    if (sortOrder.value === "desc") {
      sortOrder.value = "asc"; // Toggle to ascending
    } else {
      selectedSort.value = null; // Unselect the chip
    }
  } else {
    selectedSort.value = field;
    sortOrder.value = "desc"; // Default to descending when newly selected
  }
  updateUrl()
};

const getChipColor = (field) => {
  if (selectedSort.value === field) {
    return "primary";
  }
  return "background"; // Default color when unselected
};


const onIngredientAutocompleteChange = async (query) => {
  const response = await searchIngredients(query, 0, 20);
  autocompleteList.value = response.data.items
}

const updateUrl = () => {
  console.log(selectedDishType.value)
  const currentQuery = route.query
  let selectedSortOrder = sortOrder.value == "asc" ? "_ASCENDING" : "_DESCENDING"
  if (selectedSort.value == "RANDOM") {
    selectedSortOrder = ""
  }
  const query = Object.fromEntries(
    Object.entries({
      ...currentQuery,
      user: selectedSource.value.includes(0) ? authStore.id : undefined,
      likedBy: selectedSource.value.includes(1) ? authStore.id : undefined,
      cookbookUser: selectedSource.value.includes(2) ? authStore.id : undefined,
      followedBy: selectedSource.value.includes(3) ? authStore.id : undefined,
      dishClasses: selectedDishType.value?.length ? selectedDishType.value.map(it => dishOptions[it].value).join(",") : undefined,
      ingredient: selectedIngredients.value?.length ? selectedIngredients.value.map(it => it.id).join(",") : undefined,
      sort: selectedSort.value != null ? selectedSort.value + selectedSortOrder  : undefined
    }).filter(([_, value]) => value !== undefined) // Remove undefined values
  )
  router.replace({
    path: '/recipe/list', // Keep the current path
    query: query,
  })
  console.log(query)
  setCookie('recipeQuery',query)
}

</script>

<template>
  <v-card
    class=" py-2 px-4"
  >

    <v-row class="d-flex justify-space-between align-start">
      <!-- Left Section: Filters & Input -->
      <v-col class="d-flex flex-column">

        <chip-row :values="sourceOptions"  :action="updateUrl" :selected="selectedSource"></chip-row>
        <chip-row :values="dishOptions" :action="updateUrl" :selected="selectedDishType"></chip-row>

          <v-combobox
            class="my-2 flex-grow-1 ma-1" max-width="400px"
            ref="comboboxRef"
            v-model="selectedIngredients"
            :items="autocompleteList"
            :item-title="`name.${getLocale().toUpperCase()}`"
            item-value="id"
            return-object
            multiple
            chips
            closable-chips
            clearable
            :label="$t('ingredients')"
            @update:search="(query) => onIngredientAutocompleteChange(query)"
            @keydown.enter.prevent="selectFirstMatch"
            @update:modelValue="onComboUpdate"
          >
            <template v-slot:selection="data">
              <v-chip
                size="small"
                class="text-primary"
                color="primary"
                variant="elevated"
              >
                {{ data.item.name}}
              </v-chip>
            </template>
          </v-combobox>

      </v-col>

      <!-- Right Section: Sorting Chips -->
      <v-col class="d-flex flex-column align-center align-sm-end align-self-center mx-auto" cols="auto">
        <v-chip
          v-for="option in sortOptions"
          :key="option.value"
          :color="getChipColor(option.value)"
          :variant="'elevated'"
          @click="toggleSort(option.value)"
          :prepend-icon="option.icon"
          class="ma-1"
        >
          {{$t(option.label)}}
          <v-icon v-if="selectedSort === option.value && option.ordered">
            {{ sortOrder === 'desc' ? 'mdi-arrow-down' : 'mdi-arrow-up' }}
          </v-icon>
        </v-chip>
      </v-col>
    </v-row>
  </v-card>
</template>

<style scoped>

</style>
