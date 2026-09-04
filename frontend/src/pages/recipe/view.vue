<template>
  <v-card
    class="mx-auto pa-5 ma-auto my-5 d-flex flex-row"
    max-width="1000px"
    v-if="displayError"
  >
    <v-icon color="black" class="text-h3 mr-5 ml-3 mt-2" >mdi-alert</v-icon>
    <v-card-title prepend-icon="mdi-alert" class="text-h4">
      {{ errorMessage }}
    </v-card-title>
  </v-card>

  <v-card
  class="mx-auto pa-5 ma-auto my-5"
  max-width="1000px"
  v-if="!displayError"
  >
    <v-card-title
      class="mx-auto px-3 text-black text-h2 font-weight-bold"
    >{{ recipe.title }}</v-card-title>

    <v-card-text
      class="mx-auto px-3 mb-2"
    > {{recipe.description}} </v-card-text>

    <user-info :user="recipe.owner" class="mt-n8 mb-4"></user-info>

    <v-img
      style="border: 3px solid #0d1821 !important;"
      color="surface-variant"
      width="100%"
      aspect-ratio="4/3"
      class="mt-n3 mx-auto"
      rounded="lg"
      :src="getRecipeImageUrl(recipe.id, recipe.version)"

      cover
    ></v-img>


    <span class="d-flex align-center justify-center mb-2 my-5 ga-10">
      <v-btn
        :icon="recipeLiked ? 'mdi-heart' : 'mdi-heart-outline' "
        color="background"
        flat
        class="mb-10 text-h6"
        min-height="70px"
        min-width="70px"
        @click="onLikeButtonClick"
        elevation="2"
      ></v-btn>

      <v-menu class="mt-n16"
        :close-on-content-click="false"
      >
        <template v-slot:activator="{ props }">
          <v-btn
            color="background"
            :icon="userCookbooks.some((it) => it.hasRecipe) ? 'mdi-bookmark' : 'mdi-bookmark-outline'"
            height="70px"
            width="70px"
            v-bind="props"
            class="mt-n10 text-h6"
          ></v-btn>
        </template>

        <v-card >
          <v-list density="compact" style="border: 0 solid !important;">
          <v-list-item
            prepend-icon="mdi-plus"
            link
            title="New cookbook"
            @click="toCreateCookbookAddRecipe(recipeId)"
          ></v-list-item>
          <v-divider></v-divider>
          <v-list-item
            v-for="cookbook in userCookbooks"
            :key="cookbook.id"
            density="compact"
            :prepend-icon="cookbook.hasRecipe ? 'mdi-check-circle' : 'mdi-plus-circle-outline'"
            link
            :title="cookbook.title"
            @click="onSelectCookbook(cookbook)"
          ></v-list-item>
        </v-list>
        </v-card>
      </v-menu>

      <v-btn
        icon="mdi-share-variant"
        color="background"
        flat
        class="mb-10 text-h6"
        min-height="70px"
        min-width="70px"
        @click="onShareButtonClick"
        elevation="2"
      ></v-btn>
      <admin-only>
        <v-btn
          icon="mdi-tray-arrow-down"
          color="background"
          flat
          class="mb-10 text-h6"
          min-height="70px"
          min-width="70px"
          @click="downloadRecipe(recipeId)"
          elevation="2"
        ></v-btn>
      </admin-only>
      <report-dialog v-if="!isOwner" target-type="RECIPE" :target-id="Number(recipeId)">
        <template #activator="{props}">
          <v-btn
            v-bind="props"
            :icon="ICON_REPORT"
            :title="`${$t('report')}`"
            color="background"
            flat
            class="mb-10 text-h6"
            min-height="70px"
            min-width="70px"
            elevation="2"
          ></v-btn>
        </template>
      </report-dialog>
    </span>

    <v-alert
      v-if="recipe.isHidden"
      type="warning"
      variant="tonal"
      class="mb-4"
      :text="$t('recipe_hidden_by_moderation')"
    ></v-alert>

    <span class="d-flex flex-wrap">
        <picto-info :value="`${recipe.preparationTime} min`" icon="mdi-chef-hat" v-if="recipe.preparationTime"></picto-info>
        <picto-info :value="`${recipe.cookingTime} min`" icon="mdi-stove" v-if="recipe.cookingTime"></picto-info>
        <picto-info :value="`${recipe.cookingTemperature} °C`" icon="mdi-thermometer" v-if="recipe.cookingTemperature"></picto-info>
      </span>

    <v-number-input
      v-model="selectedYield"
      min="1"
      max-width="200px"
      class="mx-auto mt-8"
      control-variant="split"
      precision="0"
    ></v-number-input>

    <v-container v-if="recipe?.ingredients?.length">

      <h2 class="my-3" >Ingredients</h2>

    <v-list v-for="(ingredient, index) in recipe.ingredients">
      <v-card color="background" rounded="lg">
      <v-list-item
        :key="index"
        :subtitle="formatAmount(ingredient.amount * coefficient, ingredient.unit)"
        :title="`${ingredient.name} ${ingredient.complement ? '(' + ingredient.complement + ')' : ''}`"
      >
        <template v-slot:prepend>
          <v-avatar size="40" variant="elevated" class="mr-2" style="border:3px solid #0d1821 !important;">
            <v-img
              color="surface"
              :src="ingredient.id ? getIngredientImageUrl(ingredient.type) : defaultImageIngredient"
              cover
              v-bind="props"
              :class="{clickable_image: ingredient.id}"
              @click.stop="ingredient.id && toViewIngredient(ingredient.id)"
            ></v-img>
          </v-avatar>
        </template>

        <template v-slot:append>
          <v-btn
            v-if="ingredient.id"
            color="surface"
            icon="mdi-information"
            variant="text"
            style="border: 0 !important;"
            @click="toViewIngredient(ingredient.id)"
          ></v-btn>
        </template>

      </v-list-item>
      </v-card>
    </v-list>

    </v-container>

      <v-container v-if="recipe?.steps?.length">

      <h2 class="my-3" >Steps</h2>

        <v-list v-for="(step, index) in recipe.steps">
          <v-card color="background" rounded="lg">
            <v-list-item>
              <v-card-text class="text-wrap">{{step}}</v-card-text>
              <template v-slot:prepend>
                <v-avatar color="surface">
                  {{index + 1}}
                </v-avatar>
              </template>

            </v-list-item>
          </v-card>
        </v-list>

      </v-container>

    <v-card-text
      v-if="recipe.tips"
      class="mx-auto px-3"
    > {{recipe.tips}} </v-card-text>

    <v-container>
      <v-row
        class="d-flex align-center justify-center mb-2 ga-4"
        dense
      >
        <v-dialog max-width="500" v-if="isOwner">
          <template v-slot:activator="{ props: activatorProps }">
            <action-button
              icon="mdi-delete"
              :text="`${$t('delete')}`"
              v-bind="activatorProps"
            ></action-button>
          </template>

          <template v-slot:default="{ isActive }">
            <v-card>
              <v-card-text class="font-weight-bold text-h4">{{$t("deleteRecipeTitle")}}</v-card-text>
              <v-card-text>
                {{$t("deleteRecipeDescription")}}
              </v-card-text>

              <v-card-actions>
                <v-spacer></v-spacer>

                <v-btn
                  :text="`${$t('cancel')}`"
                  @click="isActive.value = false"
                ></v-btn>
                <v-btn
                  :text="`${$t('delete')}`"
                  @click="() => remove(recipeId)"
                ></v-btn>
              </v-card-actions>
            </v-card>
          </template>
        </v-dialog>
          <action-button
            icon="mdi-pencil"
            :text="`${$t('edit')}`"
            :action="() => toEditRecipe(recipeId)"
            v-if="isOwner"
          ></action-button>
      </v-row>
    </v-container>

    <v-snackbar
      v-model="snackbar"
      :timeout="2000"
      height="60px"
    >
      {{$t('successfully_copied')}}
      <template v-slot:actions>
        <v-btn
          variant="flat"
          color="primary"
          @click="snackbar = false"
          icon="mdi-close"
          height="40px"
          width="40px"
        >

        </v-btn>
      </template>
    </v-snackbar>



  </v-card>

  <v-card
    class="mx-auto pa-2 px-5 ma-auto my-5"
    max-width="1000px"
    v-if="!displayError"
  >
    <v-card-text
      class="mx-auto px-0 mb-2 text-h4 font-weight-bold"
    > Notes </v-card-text>

    <v-textarea
      v-model="notes"
      :label="`${$t('notes')}`"
      :rules="[]"
      v-if="editingNote"

    ></v-textarea>

    <v-card-text
      class="mx-auto mt-n4 pl-1 px-0"
      v-if="!editingNote"
    > {{remoteNotes}} </v-card-text>

    <v-card-actions v-if="editingNote">
      <v-spacer></v-spacer>
      <v-btn
        v-if="remoteNotes"
        :text="`${$t('cancel')}`"
        @click="() => cancelNoteEdition()"
      ></v-btn>
      <v-btn
        :text="`${$t('save')}`"
        @click="() => saveNote()"
      ></v-btn>
    </v-card-actions>

    <v-card-actions v-if="!editingNote">
      <v-spacer></v-spacer>
      <v-btn
        :text="`${$t('edit')}`"
        @click="() => startEditNote()"
      ></v-btn>
    </v-card-actions>

  </v-card>

</template>

<script lang="ts" setup>
import { useRoute } from 'vue-router';
import {deleteRecipe, downloadRecipe, getRecipe} from "@/scripts/recipes";
import {ref} from "vue";
import {
  defaultImageIngredient,
  formatAmount,
  getIngredientImageUrl,
  getRecipeImageUrl,
  toCreateCookbookAddRecipe,
  toEditRecipe,
  toListRecipe, toSignup, toViewIngredient, toViewUser,
} from "@/scripts/common";
import {useAuthStore} from "@/stores/auth";
import {addLike, isLiked, removeLike} from "@/scripts/likes";
import {addRecipeToCookbook, getStatusInCookbooks, listCookbooks, removeRecipeFromCookbook} from "@/scripts/cookbooks";
import {getIngredientIcon, ICON_REPORT} from "@/scripts/icons";
import {useI18n} from "vue-i18n";
import {max100, requiredRule} from "@/scripts/rules";
import {createNotes, getNotes, updateNotes} from "@/scripts/notes";

// Get the route object
const route = useRoute();
const recipeId = route.query.id
let displayError = ref<boolean>(false)
const editingNote = ref<boolean>(true)
const notes = ref<string>("")
const remoteNotes = ref<string>("Lorem ipsum sic dolor amet")
const errorMessage = ref<string>("This recipe does not exist")
const isOwner = ref<boolean>(false)
const recipeLiked = ref<string>(null)
const snackbar = ref(false)

const userCookbooks = ref([])
const authStore = useAuthStore()
const {t} = useI18n()
const selectedYield = ref(null)

const coefficient = computed(() =>  selectedYield.value / recipe.value.yield)

const recipe = ref<object>({
  steps: [''],
  ingredients: [],
  owner: {}
})

getRecipe(recipeId).then (
  function (response) {
    recipe.value = response.data
    selectedYield.value = recipe.value.yield
    console.log("Recipe", recipe.value)
    isOwner.value = response.data.owner.id == authStore.id
    console.log("Recipe owner", recipe.value.owner)
  }).catch(function (error) {
    displayError.value = true
  console.log(error);
    console.log(displayError)
}).finally(function () {
  // always executed
});

isLiked(recipeId).then (
  function (response) {
    recipeLiked.value = response
    console.log(response)
  }
)

getNotes(recipeId).then (
  function (response) {
    remoteNotes.value = response.data
    cancelNoteEdition()
  }
).catch(
  function (error) {
    remoteNotes.value = null
  }
)


const updateCookbook = () => {
  getStatusInCookbooks(recipeId).then(
    function (response) {
      userCookbooks.value = [...response.data]
      console.log("status in cookbooks",response.data)
    }
  )
}

updateCookbook()

const remove = (id) => {
  deleteRecipe(id)
  toListRecipe()
}

const onLikeButtonClick = () => {
  if (recipeLiked.value) {
    removeLike(recipeId)
  } else {
    addLike(recipeId)
  }
  recipeLiked.value = !recipeLiked.value
}

const onShareButtonClick = () => {
  navigator.clipboard.writeText(window.location.href);
  snackbar.value = true
}

const onSelectCookbook = (cookbook) => {
  if (cookbook.hasRecipe) {
    removeRecipeFromCookbook(cookbook.id, recipeId).then(
      function(response) {
      updateCookbook()
    })
  } else {
    addRecipeToCookbook(cookbook.id, recipeId).then(
      function(response) {
        updateCookbook()
      })
  }
}

const cancelNoteEdition = () => {
  notes.value = remoteNotes.value
  stopEditNote()
}

const saveNote = () => {
  if (remoteNotes.value == null) {
    createNotes(recipeId, notes.value).then(
      function (response) {
        remoteNotes.value = response.data
        cancelNoteEdition()
      }
    )
  }
    else {
    updateNotes(recipeId, notes.value).then(
      function (response) {
        remoteNotes.value = response.data
        cancelNoteEdition()
      }
    )
    }

}

const startEditNote = () => {
  editingNote.value = true
}

const stopEditNote = () => {
  editingNote.value = false
}

</script>
