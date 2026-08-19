<template>
  <v-layout class="mt-6">

      <v-card class="d-flex flex-column pa-0 ma-0" width="100%" color="transparent" variant="flat" style="border:0 !important">
        <v-card-title class="text-h7">
          {{$t("ingredients")}}
        </v-card-title>

      <v-card max-width="1500px"  class="ma-0">

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
              :key="ingredient.id"
              class="clickable_row"
              @click="toViewIngredient(ingredient.id)"
            >
              <td class="d-flex flex-row align-center">
                <v-avatar size="40" variant="elevated" class="mr-2" style="border:2px solid #0d1821 !important;">
                  <v-img
                    color="background"
                    :src="getIngredientImageUrl(ingredient.type)"
                    cover
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
// Browsing only: creating, editing and deleting ingredients live in the backoffice at /admin.
import {searchIngredients} from "@/scripts/ingredients";
import {getIngredientImageUrl, toViewIngredient} from "@/scripts/common";
import {getLocale} from "@/scripts/localization";
import {debounce} from "lodash";

const ingredients = ref<object[]>([])
const page = ref<number>(1)
const pagesCount = ref<number>(1)
const query = ref("")

const updateDisplay = debounce(() => {
  searchIngredients(`query=${query.value || ""}`,page.value - 1, 20).then (
    function (response) {
      ingredients.value = response.data.items
      pagesCount.value = Math.ceil(response.data.count / 20)
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
.clickable_row {
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
