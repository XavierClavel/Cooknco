<template>
  <v-layout class="mt-6">

      <v-card class="d-flex flex-column pa-0 ma-0" width="100%" color="transparent" variant="flat" style="border:0 !important">
        <v-card-title class="text-h7">
          {{$t("ingredients")}}
          <v-btn
            v-if="isAdmin"
            color="black"
            class="ma-4 ml-6 text-h5 text-black"
            rounded="lg"
            height="50px"
            width="50px"
            @click="newIngredient"
            icon="mdi-plus"
            variant="outlined"
            style="border: 4px solid #0d1821 !important;"
          ></v-btn>
        </v-card-title>

      <v-card max-width="1500px"  class="ma-0">

        <v-slide-x-reverse-transition v-show="isPanelOpen">
          <v-sheet class="pa-4 right-panel" elevation="3" z-index="50" style='z-index:20001;'>
            <v-btn icon @click="isPanelOpen = false">
              <v-icon>mdi-close</v-icon>
            </v-btn>
            <v-card-title class="text-h6 text-sm-h5">
              {{ action }}
            </v-card-title>

            <v-text-field
              v-model="selectedIngredient.name.FR"
              label="Name FR"
            ></v-text-field>


              <v-text-field
                v-model="selectedIngredient.name.EN"
                label="Name EN"
              ></v-text-field>


            <v-select
              v-model="selectedIngredient.type"
              label="Type"
              :items="ingredientTypes"
              item-title="value"
              item-value="value"
            ></v-select>


            <v-number-input
              v-model="selectedIngredient.calories"
              :label="`${$t('calories')}`"
              type="number"
              :min=0
              :step="10"
            ></v-number-input>

            <v-number-input
              v-model="selectedIngredient.proteins"
              :label="`${$t('proteins')}`"
              :min=0
              :step="0.1"
            ></v-number-input>


            <v-number-input
              v-model="selectedIngredient.carbohydrates"
              :label="`${$t('carbohydrates')}`"
              color="primary"
              :min=0
              :step="0.1"
            ></v-number-input>

            <v-number-input
              v-model="selectedIngredient.sugars"
              :label="`${$t('sugars')}`"
              color="primary"
              :min=0
              :step="0.1"
            ></v-number-input>

            <v-number-input
              v-model="selectedIngredient.fibers"
              :label="`${$t('fibers')}`"
              color="primary"
              :min=0
              :step="0.1"
            ></v-number-input>


            <v-number-input
              v-model="selectedIngredient.unsaturatedFat"
              :label="`${$t('unsaturatedFat')}`"
              color="primary"
              :min=0
              :step="0.1"
            ></v-number-input>

            <v-number-input
              v-model="selectedIngredient.saturatedFat"
              :label="`${$t('saturatedFat')}`"
              color="primary"
              :min=0
              :step="0.1"
            ></v-number-input>


            <v-number-input
              v-model="selectedIngredient.cholesterol"
              :label="`${$t('cholesterol')}`"
              color="primary"
              :min=0
              :step="0.1"
            ></v-number-input>


            <v-number-input
              v-model="selectedIngredient.sodium"
              :label="`${$t('sodium')}`"
              color="primary"
              :min=0
              :step="0.1"
            ></v-number-input>

            <v-card color="background" class="mb-2">
              <v-checkbox
                v-model="selectedIngredient.allowAmount"
                :label="`${$t('allowAmount')}`"
                color="black"
                base-color="black"
                bg-color="background"
                variant="elevated"
                class="mx-2 my-0 mb-n6"
              ></v-checkbox>
            </v-card>

            <v-card color="background" class="mb-2">
              <v-checkbox
                v-model="selectedIngredient.allowWeight"
                :label="`${$t('allowWeight')}`"
                color="black"
                base-color="black"
                bg-color="background"
                variant="elevated"
                class="mx-2 my-0 mb-n6"
              ></v-checkbox>
            </v-card>

            <v-card color="background" class="mb-2">
              <v-checkbox
                v-model="selectedIngredient.allowVolume"
                :label="`${$t('allowVolume')}`"
                color="black"
                base-color="black"
                bg-color="background"
                variant="elevated"
                class="mx-2 my-0 mb-n6"
              ></v-checkbox>
            </v-card>

            <v-number-input
              v-model="selectedIngredient.weightPerUnit"
              :label="`${$t('weightPerUnit')}`"
              :min=0
              :step="10"
            ></v-number-input>

            <v-number-input
              v-model="selectedIngredient.volumicMass"
              :label="`${$t('volumicMass')}`"
              :min=0
              :step="10"
            ></v-number-input>

            <span class="d-flex flex-row align-center justify-center mb-2 mt-16 ga-16" >
        <v-btn
          icon="mdi-close-circle-outline"
          color="primary"
          flat
          rounded="lg"
          class="mb-10 text-h6"
          min-height="70px"
          min-width="70px"
          @click="cancel"
        ></v-btn>
        <v-btn
          v-if="selectedIngredient.id != undefined"
          :icon="`${ICON_DELETE}`"
          color="primary"
          flat
          rounded="lg"
          class="mb-10 text-h6"
          min-height="70px"
          min-width="70px"
          @click="performDelete(selectedIngredient.id)"
        ></v-btn>
        <v-btn
          @click="save"
          icon="mdi-content-save"
          color="primary"
          flat
          rounded="lg"
          class="mb-10 text-h6"
          min-height="70px"
          min-width="70px"
        ></v-btn>
      </span>
          </v-sheet>
        </v-slide-x-reverse-transition>

        <v-text-field
          width="300px"
          class="ml-2"
          density="compact"
          :label="`${$t('search_ingredient')}`"
          clearable
          v-model="query"
          @update:modelValue="updateQuery"
        ></v-text-field>

        <div class="custom-scroll-table">
        <v-table
            min-height="300px"
            fixed-header
            class="rounded-0"
          >
            <thead>
            <tr>
              <th class="text-left">
                {{$t("ingredient")}}
              </th>
              <th class="text-center">
                {{$t("calories")}}
              </th>
              <th class="text-center">
                {{$t("cholesterol")}}
              </th>
              <th class="text-center">
                {{$t("carbohydrates")}}
              </th>
              <th class="text-center">
                {{$t("sugars")}}
              </th>
              <th class="text-center">
                {{$t("fibers")}}
              </th>
              <th class="text-center">
                {{$t("proteins")}}
              </th>
              <th class="text-center">
                {{$t("sodium")}}
              </th>
              <th class="text-center">
                {{$t("saturatedFat")}}
              </th>
              <th class="text-center">
                {{$t("unsaturatedFat")}}
              </th>
            </tr>
            </thead>
            <tbody>
            <tr
              v-for="ingredient in ingredients"
              :key="ingredient.username"
              @click="editIngredient(ingredient)"
              :class="{'primary': ingredient.id == 3  }"
              :style="{
                backgroundColor: isPanelOpen && ingredient && ingredient.id === selectedIngredient.id ? theme.current.value.colors.secondary : '',
              }"
            >
              <td class="d-flex flex-row align-center">
                <v-avatar size="40" variant="elevated" class="mr-2" style="border:2px solid #0d1821 !important;">
                  <v-img
                    color="background"
                    :src="getIngredientImageUrl(ingredient.type)"
                    cover
                    v-bind="props"
                    class="clickable_image"
                    @click.stop="toViewIngredient(ingredient.id)"
                  ></v-img>
                </v-avatar>
                {{ ingredient.name[getLocale().toUpperCase()]}}</td>
              <td class="text-center">{{ ingredient.calories }}</td>
              <td class="text-center">{{ ingredient.cholesterol }}</td>
              <td class="text-center">{{ ingredient.carbohydrates }}</td>
              <td class="text-center">{{ ingredient.sugars }}</td>
              <td class="text-center">{{ ingredient.fibers }}</td>
              <td class="text-center">{{ ingredient.proteins }}</td>
              <td class="text-center">{{ ingredient.sodium }}</td>
              <td class="text-center">{{ ingredient.saturatedFat }}</td>
              <td class="text-center">{{ ingredient.unsaturatedFat }}</td>
            </tr>
            </tbody>
          </v-table>
        </div>
        <v-container>
          <v-row justify="center">
            <v-col cols="6">
              <v-container class="max-width">
                <v-pagination
                  v-model="page"
                  :length="pagesCount"
                  class="my-4"
                  @update:modelValue="updateDisplay"
                ></v-pagination>
              </v-container>
            </v-col>
          </v-row>
        </v-container>
      </v-card>
      </v-card>

  </v-layout>
</template>

<script lang="ts" setup>
import {createIngredient, deleteIngredient, getCount, searchIngredients, updateIngredient} from "@/scripts/ingredients";
import { useTheme } from 'vuetify'
import {getIngredientIcon, ICON_DELETE} from "@/scripts/icons";
import {getIngredientImageUrl, getUserIconUrl, toCreateCookbook, toViewIngredient} from "@/scripts/common";
import {getLocale} from "@/scripts/localization";
import {ingredientTypes} from "@/scripts/values";
import {useAuthStore} from "@/stores/auth";
import debounce from "lodash/debounce";

const theme = useTheme()

const ingredients = ref<object[]>([])
const selectedIngredient = ref({name: {}})
const page = ref<number>(1)
const pagesCount = ref<number>(1)
const isPanelOpen = ref<boolean>(false)
const action = ref<string>("Create ingredient")
const authStore = useAuthStore()
const isAdmin = ref(authStore.isAdmin)
const query = ref("")

async function save() {
  const { id, ...ingredientToUpdate} = selectedIngredient.value
  if (selectedIngredient.value.id == undefined) {
    await createIngredient(ingredientToUpdate)
  } else {
    await updateIngredient(selectedIngredient.value.id, ingredientToUpdate)
  }
  updateDisplay()
  cancel()
}

const cancel = () => {
  if (selectedIngredient != {}) {
    let {...copy} = selectedIngredient.value;
    copy.id = -1
    selectedIngredient.value = copy
  }


  isPanelOpen.value = false
}

const newIngredient = () => {
  isPanelOpen.value = true
  selectedIngredient.value = {name: {}}
  action.value = "Create ingredient"
}

const editIngredient = (ingredientToEdit) => {
  if (!isAdmin.value) return
  if (selectedIngredient.value.id == ingredientToEdit.id) {
    cancel()
  } else {
    isPanelOpen.value = true
    selectedIngredient.value = ingredientToEdit
    action.value = `Edit ingredient "${ingredientToEdit.name[getLocale().toUpperCase()]}"`
  }

}

const performDelete = (id) => {
  deleteIngredient(id).then(
    function (response) {
      if (response.status == 200) {
        const index = ingredients.value.findIndex((user) => user.id == id)
        ingredients.value.splice(index,1)
      }
      cancel()
    }).catch(function (error) {
    console.log(error);
  }).finally(function () {
    // always executed
  });

};



const updateDisplay = debounce(() => {
  searchIngredients(`query=${query.value || ""}`,page.value - 1, 20).then (
    function (response) {
      ingredients.value = response.data.items
      pagesCount.value = Math.ceil(response.data.count / 20)
      console.log(ingredients.value)
    }).catch(function (error) {
    console.log(error);
  })
}, 500)

const updateQuery = () => {
  page.value = 1
  updateDisplay()
}


updateDisplay()

</script>

<style scoped>
/* Styling to make the overlay appear as a fixed panel on the right */
.right-panel {
  position: fixed;
  top: 0;
  right: 0;         /* Sticks the panel to the right side */
  height: 100vh;    /* Full vertical height */
  width: 400px;
  overflow-y: auto; /* Enables scrolling within the panel if content is long */
}

.clickable_image {
  cursor: pointer
}

.custom-scroll-table::-webkit-scrollbar {
  height: 4px; /* Height of the horizontal scrollbar */
}

.custom-scroll-table::-webkit-scrollbar-track {
  background: #eee; /* Track background */
}

.custom-scroll-table::-webkit-scrollbar-thumb {
  background-color: #000000; /* Vuetify primary, for example */
  border-radius: 8px;
  border: 1px solid transparent;
  background-clip: content-box;
}


/* Firefox */
.custom-scroll-table {
  scrollbar-color: #000000 #eee;
  scrollbar-width: thin;
}

</style>
