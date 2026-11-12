<template>
  <div v-if="displayError" class="sticker sk-pad recipe-error">
    <v-icon>mdi-alert</v-icon>
    <h1 class="sk-h2">{{ errorMessage }}</h1>
  </div>

  <div v-else class="sk-page recipe-view">
    <div v-if="recipe.isHidden" class="sticker sticker--cream sticker--sm sk-pad recipe-hidden" role="status">
      <v-icon>mdi-eye-off-outline</v-icon>
      {{ $t('recipe_hidden_by_moderation') }}
    </div>

    <!-- The photo, with its actions over it, and what the recipe is beside it -->
    <section class="recipe-hero">
      <div class="recipe-photo sticker sk-placeholder">
        <img
          v-if="recipe.id"
          :src="getRecipeImageUrl(recipe.id, recipe.version)"
          :alt="recipe.title"
        />

        <div class="recipe-photo__actions">
          <button
            type="button"
            class="sk-icon-btn"
            :aria-label="$t('recipe_back')"
            :title="$t('recipe_back')"
            @click="goBack"
          ><v-icon>mdi-arrow-left</v-icon></button>

          <div class="recipe-photo__group">
            <button
              type="button"
              class="sk-icon-btn"
              :class="{'sk-icon-btn--coral': recipeLiked}"
              :aria-pressed="!!recipeLiked"
              :aria-label="$t('recipe_like')"
              :title="recipeLiked ? $t('recipe_unlike') : $t('recipe_like')"
              @click="onLikeButtonClick"
            ><v-icon>{{ recipeLiked ? 'mdi-heart' : 'mdi-heart-outline' }}</v-icon></button>

            <v-menu :close-on-content-click="false">
              <template v-slot:activator="{ props }">
                <button
                  type="button"
                  class="sk-icon-btn"
                  v-bind="props"
                  :aria-label="$t('recipe_add_to_cookbook')"
                  :title="$t('recipe_add_to_cookbook')"
                ><v-icon>{{ userCookbooks.some((it) => it.hasRecipe) ? 'mdi-bookmark' : 'mdi-bookmark-outline' }}</v-icon></button>
              </template>

              <v-list density="compact">
                <v-list-item
                  prepend-icon="mdi-plus"
                  link
                  :title="$t('recipe_new_cookbook')"
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
            </v-menu>

            <button
              type="button"
              class="sk-icon-btn"
              :aria-label="$t('recipe_share')"
              :title="$t('recipe_share')"
              @click="onShareButtonClick"
            ><v-icon>mdi-share-variant-outline</v-icon></button>

            <!--
              The secondary actions. Not closed on a click inside, because the report dialog is
              opened from in here and would be unmounted along with the menu: every other item
              closes it by hand.
            -->
            <v-menu v-model="moreMenu" :close-on-content-click="false">
              <template v-slot:activator="{ props }">
                <button
                  type="button"
                  class="sk-icon-btn"
                  v-bind="props"
                  :aria-label="$t('recipe_more_actions')"
                  :title="$t('recipe_more_actions')"
                ><v-icon>mdi-dots-horizontal</v-icon></button>
              </template>

              <v-list density="compact">
                <v-list-item
                  v-if="isOwner"
                  prepend-icon="mdi-pencil"
                  link
                  :title="$t('edit')"
                  @click="closeMoreMenu(() => toEditRecipe(recipeId))"
                ></v-list-item>
                <v-list-item
                  v-if="isOwner"
                  prepend-icon="mdi-delete"
                  link
                  :title="$t('delete')"
                  @click="closeMoreMenu(() => deleteDialog = true)"
                ></v-list-item>
                <premium-only>
                  <v-list-item
                    prepend-icon="mdi-tray-arrow-down"
                    link
                    :title="$t('export_pdf')"
                    @click="closeMoreMenu(onDownloadButtonClick)"
                  ></v-list-item>
                  <v-list-item
                    prepend-icon="mdi-file-code-outline"
                    link
                    :title="$t('export_cooklang')"
                    @click="closeMoreMenu(onDownloadCooklangButtonClick)"
                  ></v-list-item>
                </premium-only>
                <report-dialog v-if="!isOwner" target-type="RECIPE" :target-id="Number(recipeId)">
                  <template #activator="{props}">
                    <v-list-item
                      v-bind="props"
                      :prepend-icon="ICON_REPORT"
                      link
                      :title="$t('report')"
                    ></v-list-item>
                  </template>
                </report-dialog>
              </v-list>
            </v-menu>
          </div>
        </div>
      </div>

      <div class="recipe-info sticker sk-pad-lg">
        <span v-if="dishLabel" class="sk-pill sk-pill--label recipe-info__class">{{ $t(dishLabel) }}</span>
        <h1 class="recipe-info__title">{{ recipe.title }}</h1>
        <p v-if="recipe.description" class="recipe-info__description">{{ recipe.description }}</p>

        <div class="sk-spread">
          <user-info v-if="recipe.owner?.id" :user="recipe.owner"></user-info>
          <span v-if="recipe.likesCount !== undefined" class="sk-pill sk-pill--coral sk-pill--lg">
            <v-icon>mdi-heart</v-icon>{{ likesShown }}
          </span>
        </div>

        <template v-if="recipe.preparationTime || recipe.cookingTime || recipe.cookingTemperature">
          <hr class="sk-divider recipe-info__rule" />
          <dl class="recipe-stats">
            <div v-if="recipe.preparationTime">
              <dt class="sk-eyebrow">{{ $t('recipe_prep') }}</dt>
              <dd>{{ recipe.preparationTime }} min</dd>
            </div>
            <div v-if="recipe.cookingTime">
              <dt class="sk-eyebrow">{{ $t('recipe_cook') }}</dt>
              <dd>{{ recipe.cookingTime }} min</dd>
            </div>
            <div v-if="recipe.cookingTemperature">
              <dt class="sk-eyebrow">{{ $t('recipe_oven') }}</dt>
              <dd>{{ recipe.cookingTemperature }} °C</dd>
            </div>
          </dl>
        </template>
      </div>
    </section>

    <section class="recipe-body">
      <!-- Left: what goes in, the author's word on it, and the reader's own -->
      <div class="recipe-col">
        <template v-if="recipe?.ingredients?.length">
          <div class="recipe-col__head">
            <h2 class="sk-h2">{{ $t('ingredients') }}</h2>
            <div v-if="recipe.yield" class="yield">
              <span class="sk-label">{{ $t('recipe_scaled_for') }}</span>
              <div class="yield__stepper" role="group" :aria-label="$t('recipe_scaled_for')">
                <button
                  type="button"
                  class="yield__btn"
                  :aria-label="$t('recipe_yield_decrease')"
                  :disabled="selectedYield <= 1"
                  @click="selectedYield = Math.max(1, selectedYield - 1)"
                ><v-icon>mdi-minus</v-icon></button>
                <span class="yield__value" aria-live="polite">{{ selectedYield }}</span>
                <button
                  type="button"
                  class="yield__btn yield__btn--coral"
                  :aria-label="$t('recipe_yield_increase')"
                  @click="selectedYield = selectedYield + 1"
                ><v-icon>mdi-plus</v-icon></button>
              </div>
            </div>
          </div>

          <ul class="ingredient-list sticker">
            <li v-for="(ingredient, index) in recipe.ingredients" :key="index" class="ingredient">
              <span class="sk-tile ingredient__tile">
                <img
                  :src="ingredient.id ? getIngredientImageUrl(ingredient.type) : defaultImageIngredient"
                  alt=""
                />
              </span>
              <span class="ingredient__text">
                <a
                  v-if="ingredient.id"
                  class="ingredient__name ingredient__name--link"
                  :href="`/ingredient/view?ingredient=${ingredient.id}`"
                  @click.prevent="toViewIngredient(ingredient.id)"
                >{{ ingredient.name }}</a>
                <span v-else class="ingredient__name">{{ ingredient.name }}</span>
                <span v-if="ingredient.complement" class="ingredient__complement">{{ ingredient.complement }}</span>
              </span>
              <span class="ingredient__amount">{{ formatAmount(ingredient.amount * coefficient, ingredient.unit) }}</span>
            </li>
          </ul>
        </template>

        <div v-if="recipe.tips" class="recipe-block">
          <span class="sk-eyebrow">{{ $t('recipe_from_author', {name: recipe.owner?.username}) }}</span>
          <div class="sticker sticker--mustard sk-pad recipe-tips">{{ recipe.tips }}</div>
        </div>

        <div class="recipe-block">
          <label class="sk-eyebrow" for="recipe-notes">{{ $t('recipe_your_notes') }}</label>
          <div class="sticker sk-pad recipe-notes">
            <template v-if="editingNote">
              <textarea
                id="recipe-notes"
                v-model="notes"
                class="sk-input"
                :placeholder="$t('notes')"
              ></textarea>
              <div class="recipe-notes__actions">
                <button
                  v-if="remoteNotes"
                  type="button"
                  class="sk-btn"
                  @click="cancelNoteEdition()"
                >{{ $t('cancel') }}</button>
                <button type="button" class="sk-btn sk-btn--coral" @click="saveNote()">{{ $t('save') }}</button>
              </div>
            </template>
            <template v-else>
              <p id="recipe-notes" class="recipe-notes__text">{{ remoteNotes }}</p>
              <div class="recipe-notes__actions">
                <button type="button" class="sk-btn" @click="startEditNote()">
                  <v-icon>mdi-pencil</v-icon>{{ $t('edit') }}
                </button>
              </div>
            </template>
          </div>
        </div>
      </div>

      <!-- Right: the method -->
      <div v-if="recipe?.steps?.length" class="recipe-col">
        <div class="recipe-col__head">
          <div>
            <h2 class="sk-h2">{{ $t('steps') }}</h2>
            <div class="recipe-steps__summary">
              {{ $t('recipe_steps_count', {count: recipe.steps.length}, recipe.steps.length) }}<template v-if="totalTime"> · {{ $t('recipe_total_time', {minutes: totalTime}) }}</template>
            </div>
          </div>
          <span v-if="recipe.cookingTemperature" class="sk-pill sk-pill--mustard sk-pill--lg">
            {{ $t('recipe_oven_at', {temperature: recipe.cookingTemperature}) }}
          </span>
        </div>

        <ol class="step-list">
          <li v-for="(step, index) in recipe.steps" :key="index" class="sticker sticker--white step">
            <div class="step__head">
              <span class="sk-eyebrow">{{ $t('step') }} {{ index + 1 }}</span>
              <span v-if="step.durationSeconds" class="sk-pill">
                <v-icon>mdi-timer-outline</v-icon>{{ formatDuration(step.durationSeconds) }}
              </span>
            </div>
            <p class="step__text">{{ step.text }}</p>
          </li>
        </ol>
      </div>
    </section>

    <v-dialog v-model="deleteDialog" max-width="500" v-if="isOwner">
      <v-card>
        <v-card-text class="font-weight-bold text-h4">{{$t("deleteRecipeTitle")}}</v-card-text>
        <v-card-text>
          {{$t("deleteRecipeDescription")}}
        </v-card-text>

        <v-card-actions class="ga-3">
          <v-spacer></v-spacer>
          <button type="button" class="sk-btn" @click="deleteDialog = false">{{ $t('cancel') }}</button>
          <button type="button" class="sk-btn sk-btn--coral" @click="remove(recipeId)">{{ $t('delete') }}</button>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-snackbar
      v-model="snackbar"
      :timeout="2000"
    >
      {{ snackbarMessage }}
      <template v-slot:actions>
        <button
          type="button"
          class="sk-icon-btn sk-icon-btn--coral"
          :aria-label="$t('close')"
          @click="snackbar = false"
        ><v-icon>mdi-close</v-icon></button>
      </template>
    </v-snackbar>
  </div>
</template>

<script lang="ts" setup>
import { useRoute, useRouter } from 'vue-router';
import {deleteRecipe, downloadRecipe, downloadRecipeAsCooklang, getRecipe} from "@/scripts/recipes";
import {computed, ref} from "vue";
import {
  defaultImageIngredient,
  formatAmount,
  getIngredientImageUrl,
  getRecipeImageUrl,
  toCreateCookbookAddRecipe,
  toEditRecipe,
  toListRecipe, toViewIngredient,
} from "@/scripts/common";
import {useAuthStore} from "@/stores/auth";
import {addLike, isLiked, removeLike} from "@/scripts/likes";
import {addRecipeToCookbook, getStatusInCookbooks, removeRecipeFromCookbook} from "@/scripts/cookbooks";
import {ICON_REPORT} from "@/scripts/icons";
import {useI18n} from "vue-i18n";
import {createNotes, getNotes, updateNotes} from "@/scripts/notes";
import {loadUnitSystem} from "@/scripts/unitSystem";
import {dishOptions} from "@/scripts/values";

// Get the route object
const route = useRoute();
const router = useRouter();
const recipeId = route.query.id
let displayError = ref<boolean>(false)
const editingNote = ref<boolean>(true)
const notes = ref<string>("")
const remoteNotes = ref<string>("Lorem ipsum sic dolor amet")
const errorMessage = ref<string>("This recipe does not exist")
const isOwner = ref<boolean>(false)
const recipeLiked = ref<string>(null)
/** Whether the reader already liked it when the count was read, so a click moves the count by one. */
const initiallyLiked = ref<boolean>(false)
const snackbar = ref(false)
const snackbarMessage = ref<string>("")
const moreMenu = ref(false)
const deleteDialog = ref(false)

const userCookbooks = ref([])
const authStore = useAuthStore()
const {t} = useI18n()
const selectedYield = ref(null)

const recipe = ref<object>({
  steps: [],
  ingredients: [],
  owner: {}
})

// A recipe with no yield has nothing to scale against: its amounts are shown as written
const coefficient = computed(() => recipe.value.yield ? selectedYield.value / recipe.value.yield : 1)

const dishLabel = computed(() => dishOptions.find(it => it.value === recipe.value.dishClass)?.label)

const totalTime = computed(() => (recipe.value.preparationTime ?? 0) + (recipe.value.cookingTime ?? 0))

const likesShown = computed(() =>
  recipe.value.likesCount + (recipeLiked.value ? 1 : 0) - (initiallyLiked.value ? 1 : 0)
)

const formatDuration = (seconds: number) => {
  const minutes = Math.round(seconds / 60)
  if (minutes < 1) return `${seconds} s`
  if (minutes < 60) return `${minutes} min`
  const rest = minutes % 60
  return rest ? `${Math.floor(minutes / 60)} h ${rest}` : `${minutes / 60} h`
}

// Not awaited: the amounts draw in metric and redraw the moment the account's own ladder
// comes back, which beats holding the whole recipe for a preference
loadUnitSystem()

getRecipe(recipeId).then (
  function (response) {
    recipe.value = response.data
    selectedYield.value = recipe.value.yield
    isOwner.value = response.data.owner.id == authStore.id
  }).catch(function (error) {
    displayError.value = true
  console.log(error);
}).finally(function () {
  // always executed
});

isLiked(recipeId).then (
  function (response) {
    recipeLiked.value = response
    initiallyLiked.value = !!response
  }
)

getNotes(recipeId).then (
  function (response) {
    remoteNotes.value = response.data
    cancelNoteEdition()
  }
).catch(
  function () {
    remoteNotes.value = null
  }
)


const updateCookbook = () => {
  getStatusInCookbooks(recipeId).then(
    function (response) {
      userCookbooks.value = [...response.data]
    }
  )
}

updateCookbook()

const goBack = () => {
  // A link opened straight onto the recipe has nowhere to go back to inside the site
  if (window.history.state?.back) {
    router.back()
  } else {
    toListRecipe('')
  }
}

const closeMoreMenu = (action: () => unknown) => {
  moreMenu.value = false
  action()
}

const remove = (id) => {
  deleteRecipe(id)
  toListRecipe('')
}

const onLikeButtonClick = () => {
  if (recipeLiked.value) {
    removeLike(recipeId)
  } else {
    addLike(recipeId)
  }
  recipeLiked.value = !recipeLiked.value
}

const notify = (message: string) => {
  snackbarMessage.value = message
  snackbar.value = true
}

const onShareButtonClick = () => {
  navigator.clipboard.writeText(window.location.href);
  notify(t('successfully_copied'))
}

const onDownloadButtonClick = () => {
  downloadRecipe(recipeId).catch(function (error) {
    console.log(error)
    notify(t('unknown_error'))
  })
}

const onDownloadCooklangButtonClick = () => {
  downloadRecipeAsCooklang(recipeId).catch(function (error) {
    console.log(error)
    notify(t('unknown_error'))
  })
}

const onSelectCookbook = (cookbook) => {
  if (cookbook.hasRecipe) {
    removeRecipeFromCookbook(cookbook.id, recipeId).then(
      function() {
      updateCookbook()
    })
  } else {
    addRecipeToCookbook(cookbook.id, recipeId).then(
      function() {
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

<style scoped>
.recipe-error {
  display: flex;
  align-items: center;
  gap: 16px;
  max-width: 1000px;
}

.recipe-error .v-icon {
  font-size: 36px;
}

.recipe-hidden {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 700;
}

/* ---------- Hero: the photo and the card beside it ---------- */

.recipe-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 32px;
  align-items: start;
}

.recipe-photo {
  position: relative;
  aspect-ratio: 4 / 3;
  overflow: hidden;
}

.recipe-photo > img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.recipe-photo__actions {
  position: absolute;
  top: 16px;
  left: 16px;
  right: 16px;
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.recipe-photo__group {
  display: flex;
  gap: 8px;
}

.recipe-photo__actions .sk-icon-btn:not(.sk-icon-btn--coral) {
  background: var(--white);
}

.recipe-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.recipe-info__class {
  align-self: flex-start;
}

.recipe-info__title {
  font-size: clamp(28px, 3.4vw, 40px);
  font-weight: 700;
  line-height: 1.1;
  margin: 0;
  overflow-wrap: anywhere;
}

.recipe-info__description {
  margin: 0;
  color: var(--muted);
  white-space: pre-line;
  overflow-wrap: anywhere;
}

.recipe-info__rule {
  border-top-width: var(--line);
}

.recipe-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
}

.recipe-stats dd {
  margin: 2px 0 0;
  font-size: 18px;
  font-weight: 700;
}

/* ---------- Body: ingredients and notes, then steps ---------- */

.recipe-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.15fr);
  gap: 32px;
  align-items: start;
}

.recipe-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.recipe-col__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  min-height: 52px;
}

.yield {
  display: flex;
  align-items: center;
  gap: 12px;
}

.yield__stepper {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border: var(--line) solid var(--ink);
  border-radius: 999px;
  background: var(--white);
  box-shadow: var(--shadow-sm);
}

.yield__value {
  min-width: 40px;
  text-align: center;
  font-weight: 700;
}

.yield__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  color: var(--ink);
  cursor: pointer;
}

.yield__btn:focus-visible {
  outline: 3px solid var(--mustard);
  outline-offset: 2px;
}

.yield__btn:disabled {
  opacity: .4;
  cursor: default;
}

.yield__btn--coral {
  background: var(--coral);
  color: var(--white);
}

.ingredient-list {
  list-style: none;
  margin: 0;
  padding: 4px 18px;
}

.ingredient {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 0;
  border-bottom: 1px solid var(--rule);
}

.ingredient:last-child {
  border-bottom: 0;
}

.ingredient__tile {
  width: 38px;
  height: 38px;
  background: var(--white);
  overflow: hidden;
}

.ingredient__tile img {
  width: 26px;
  height: 26px;
  object-fit: contain;
}

.ingredient__text {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.ingredient__name {
  font-weight: 700;
  color: var(--ink);
  overflow-wrap: anywhere;
}

.ingredient__name--link {
  text-decoration: none;
}

.ingredient__name--link:hover,
.ingredient__name--link:focus-visible {
  text-decoration: underline;
}

.ingredient__complement {
  font-size: 12px;
  color: var(--label);
}

.ingredient__amount {
  flex-shrink: 0;
  font-weight: 700;
  text-align: right;
}

.recipe-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 8px;
}

.recipe-tips {
  font-weight: 700;
  white-space: pre-line;
  overflow-wrap: anywhere;
}

.recipe-notes {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.recipe-notes__text {
  margin: 0;
  white-space: pre-line;
  overflow-wrap: anywhere;
}

.recipe-notes__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.recipe-steps__summary {
  font-size: 13px;
  font-weight: 700;
  margin-top: 2px;
}

.step-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.step {
  padding: 14px 20px 16px;
}

.step__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.step__text {
  margin: 6px 0 0;
  font-weight: 500;
  white-space: pre-line;
  overflow-wrap: anywhere;
}

@media (max-width: 960px) {
  .recipe-hero,
  .recipe-body {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 600px) {
  .recipe-photo__actions {
    top: 10px;
    left: 10px;
    right: 10px;
  }

  .recipe-photo__group {
    gap: 6px;
  }

  .recipe-stats dd {
    font-size: 16px;
  }
}
</style>
