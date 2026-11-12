<template>
  <div class="recipe-form sticker sk-pad-lg">
    <v-form @submit.prevent="onSubmit" ref="form" class="recipe-form__form">
      <h1 class="sk-title">{{ recipeId == null ? $t('new_recipe') : $t('edit_recipe') }}</h1>

      <!--
        Only while creating. An import fills blanks and appends, so on an existing recipe it
        would read as "add this file to that recipe" - which is a thing to want, and not what
        anyone would expect a button labelled "import" to do.
      -->
      <div v-if="recipeId == null" class="sk-row">
        <button
          type="button"
          class="sk-btn"
          :disabled="importing"
          @click="cooklangInput?.click()"
        >
          <v-progress-circular v-if="importing" indeterminate size="16" width="2"></v-progress-circular>
          <v-icon v-else>mdi-tray-arrow-up</v-icon>
          {{ $t('import_cooklang') }}
        </button>
        <span class="sk-hint">{{ $t('import_cooklang_hint') }}</span>
        <input
          ref="cooklangInput"
          type="file"
          accept=".cook,text/plain"
          class="d-none"
          @change="onCooklangFileSelected"
        />
      </div>

      <div v-if="importMessage" class="sticker sticker--cream sticker--sm import-message" role="status">
        <v-icon>mdi-information-outline</v-icon>
        <span class="import-message__text">{{ importMessage }}</span>
        <button
          type="button"
          class="sk-icon-btn sk-icon-btn--white"
          :aria-label="$t('close')"
          @click="importMessage = null"
        ><v-icon>mdi-close</v-icon></button>
      </div>

      <div class="sk-field">
        <label class="sk-label" for="recipe-title">{{ $t('title') }}</label>
        <v-text-field
          id="recipe-title"
          v-model="recipe.title"
          class="my-0"
          :rules="[requiredRule, max100]"
        ></v-text-field>
      </div>

      <div class="sk-field">
        <label class="sk-label" for="recipe-description">{{ $t('description') }}</label>
        <v-textarea
          id="recipe-description"
          v-model="recipe.description"
          rows="3"
          :rules="[max255]"
        ></v-textarea>
      </div>

      <!-- Exactly one dish class: clicking the one already chosen keeps it -->
      <div class="sk-chip-row" role="group" :aria-label="$t('recipe_dish_class')">
        <button
          v-for="dishClass in dishOptions"
          :key="dishClass.value"
          type="button"
          class="sk-chip"
          :class="{'is-active': recipe.dishClass === dishClass.value}"
          :aria-pressed="recipe.dishClass === dishClass.value"
          @click="recipe.dishClass = dishClass.value"
        >{{ $t(dishClass.label) }}</button>
      </div>

      <div class="recipe-form__picture">
        <editable-picture
          v-if="ready"
          path="recipes"
          :id="recipeId"
          :version="recipe.version"
          ref="editablePicture"
          width="100%"
          :aspect-ratio="4/3"
          :empty-label="$t('recipe_add_photo')"
        ></editable-picture>
      </div>

      <div class="recipe-form__numbers">
        <div class="sk-field">
          <label class="sk-label" for="recipe-yield">{{ $t('yield') }}</label>
          <v-number-input
            id="recipe-yield"
            v-model="recipe.yield"
            type="number"
            color="primary"
            :min=1
            :rules="[requiredRule]"
          ></v-number-input>
        </div>
        <div class="sk-field">
          <label class="sk-label" for="recipe-preparation">{{ $t('time_preparation') }}</label>
          <v-number-input
            id="recipe-preparation"
            v-model="recipe.preparationTime"
            type="number"
            :step="5"
            :min=0
          ></v-number-input>
        </div>
        <div class="sk-field">
          <label class="sk-label" for="recipe-cooking">{{ $t('time_cooking') }}</label>
          <v-number-input
            id="recipe-cooking"
            v-model="recipe.cookingTime"
            type="number"
            :step="5"
            :min=0
          ></v-number-input>
        </div>
        <div class="sk-field">
          <label class="sk-label" for="recipe-temperature">{{ $t('cooking_temperature') }}</label>
          <v-number-input
            id="recipe-temperature"
            v-model="recipe.cookingTemperature"
            type="number"
            :step="5"
            :min=0
          ></v-number-input>
        </div>
      </div>

      <!-- Ingredients -->
      <section class="sk-stack">
        <h2 class="sk-h2">{{$t("ingredients")}}</h2>
        <draggable v-model="recipe.ingredients" tag="div" class="recipe-form__rows" ghost-class="ghost" item-key="index" handle=".drag-handle">
          <template #item="{ index }">
            <div class="form-row form-row--ingredient">
              <!-- Handle for dragging -->
              <span class="drag-handle" :title="$t('recipe_drag_to_reorder')" aria-hidden="true">
                <v-icon>mdi-drag-vertical</v-icon>
              </span>

              <v-autocomplete
                v-model="recipe.ingredients[index].ingredient"
                :placeholder="`${$t('ingredient')} ${index + 1}`"
                :aria-label="`${$t('ingredient')} ${index + 1}`"
                v-model:search="queryList[index]"
                color="primary"
                :items="getAutocompleteItems(index)"
                item-color="primary"
                item-title="name"
                item-value="id"
                @update:search="(query) => onIngredientAutocompleteChange(query, index)"
                @update:model-value="(value) => onIngredientSelected(value, index)"
                :key="index"
                return-object
                @keydown.enter.prevent="selectFirstMatch(index)"
                class="form-row__name"
                :no-data-text="`${$t('no_data')}`"
              >
                <!-- Only the dropdown says "use this as a custom ingredient"; the option itself
                     carries the plain name, which is what the field shows once it is picked -->
                <template v-slot:item="{ props, item }">
                  <v-list-item
                    v-bind="props"
                    :title="item.raw.isCustom ? $t('custom_ingredient', {name: item.raw.name}) : item.raw.name"
                  >
                    <template #prepend v-if="item.raw.isCustom">
                      <v-icon>mdi-plus</v-icon>
                    </template>
                  </v-list-item>
                </template>
              </v-autocomplete>

              <v-select
                v-model="recipe.ingredients[index].unit"
                :aria-label="$t('unit')"
                :items="getUnitOptions(recipe.ingredients[index].ingredient?.allowedTypes)"
                color="primary"
                :item-title="getLocalizedLabel"
                item-value="value"
                return-object
                class="form-row__unit"
              >
                <!-- Customize how items appear in the dropdown -->
                <template v-slot:item="{ props, item }">
                  <v-list-item v-bind="props">
                    <template #prepend>
                      <v-icon>{{ item.raw.icon }}</v-icon>
                    </template>
                  </v-list-item>
                </template>

                <!-- Customize how selected item appears -->
                <template v-slot:selection="{ item }">
                  <v-icon start class="mr-2">{{ item.raw.icon }}</v-icon>
                  {{ $t(item.raw.label) }}
                </template>
              </v-select>

              <v-number-input
                v-model.number="recipe.ingredients[index].amount"
                :placeholder="$t('amount')"
                :aria-label="$t('amount')"
                color="primary"
                control-variant="stacked"
                min=0
                class="form-row__amount"
                v-if="recipe.ingredients[index].unit?.value != 'NONE'"
              ></v-number-input>

              <v-text-field
                v-model="recipe.ingredients[index].complement"
                :placeholder="$t('complement')"
                :aria-label="$t('complement')"
                :rules="[max50]"
                class="form-row__complement my-0"
              ></v-text-field>

              <button
                type="button"
                class="sk-icon-btn sk-icon-btn--white"
                :aria-label="$t('recipe_remove_ingredient')"
                :title="$t('recipe_remove_ingredient')"
                @click="removeIngredient(index)"
              ><v-icon>mdi-trash-can-outline</v-icon></button>
            </div>
          </template>
        </draggable>

        <!-- Button to add ingredient -->
        <button type="button" class="sk-btn sk-btn--block sk-btn--flat add-row" @click="addIngredient">
          <v-icon>mdi-plus</v-icon>{{$t("ingredient")}}
        </button>
      </section>

      <!-- Steps -->
      <section class="sk-stack">
        <h2 class="sk-h2">{{$t("steps")}}</h2>
        <draggable v-model="recipe.steps" tag="div" class="recipe-form__rows" ghost-class="ghost" item-key="index" handle=".drag-handle">
          <template #item="{ index }">
            <div class="form-row">
              <!-- Add a handle for dragging -->
              <span class="drag-handle" :title="$t('recipe_drag_to_reorder')" aria-hidden="true">
                <v-icon>mdi-drag-vertical</v-icon>
              </span>

              <v-text-field
                v-model="recipe.steps[index].text"
                :placeholder="`${$t('step')} ${index + 1}`"
                :aria-label="`${$t('step')} ${index + 1}`"
                :id="`step_${index}`"
                :rules="[max255]"
                class="form-row__step my-0"
                @keyup.enter="addStepAt(index)"
                @keyup.delete="deleteStepAt(index)"
              ></v-text-field>

              <button
                type="button"
                class="sk-icon-btn sk-icon-btn--white"
                :aria-label="$t('recipe_remove_step')"
                :title="$t('recipe_remove_step')"
                tabindex="-1"
                @click="removeStep(index)"
              ><v-icon>mdi-trash-can-outline</v-icon></button>
            </div>
          </template>
        </draggable>

        <!-- Button to add a new item -->
        <button type="button" class="sk-btn sk-btn--block sk-btn--flat add-row" @click="addStep">
          <v-icon>mdi-plus</v-icon>{{$t("steps_add_new")}}
        </button>
      </section>

      <div class="sk-field">
        <label class="sk-label" for="recipe-tips">{{ $t('tips') }}</label>
        <v-textarea
          id="recipe-tips"
          v-model="recipe.tips"
          rows="3"
          :rules="[max511]"
        ></v-textarea>
      </div>

      <error :error="errorMessage"></error>

      <div class="recipe-form__actions">
        <button
          v-if="recipeId"
          type="button"
          class="sk-btn sk-btn--lg"
          @click="toViewRecipe(recipeId)"
        >{{ $t('cancel') }}</button>
        <button
          type="button"
          class="sk-btn sk-btn--lg sk-btn--coral"
          :disabled="saving"
          @click="onSubmit"
        >{{ $t('save') }}</button>
      </div>
    </v-form>
  </div>

</template>

<script lang="ts" setup>
import { ref } from 'vue';
import draggable from 'vuedraggable';
import { useRoute } from 'vue-router';
import {getRecipe, createRecipe, updateRecipe, importCooklang} from "@/scripts/recipes";
import {defaultImageRecipe, toErrorMessage, toViewRecipe} from "@/scripts/common";
import {searchIngredients} from "@/scripts/ingredients";
import EditablePicture from "@/components/EditablePicture.vue";
import {dishOptions} from "@/scripts/values";
import {findUnitOption, getUnitOptions, loadUnits, unitOptions} from "@/scripts/units";
import {loadUnitSystem, preferredUnitFor, unitSystem} from "@/scripts/unitSystem";
import {useI18n} from "vue-i18n";
import {getLocale} from "@/scripts/localization";
import {max100, max255, max50, max511, requiredRule} from "@/scripts/rules";
const {t} = useI18n()
const form = ref(null)


// Get the route object
const route = useRoute();
let recipeId = ref(route.query.id)
const ready = ref(false)
const editablePicture = ref(null)
const errorMessage = ref(null)
const importMessage = ref(null)
const importing = ref(false)
const cooklangInput = ref(null)

const autocompleteList = ref([])
const queryList = ref([])

// Mirrors the column width the backend enforces.
const CUSTOM_NAME_MAX_LENGTH = 50

/**
 * A blank step.
 *
 * Text and nothing else: a step written here has no timer and uses no ingredients, and this
 * page offers no way to give it either. Naming the absent fields would be this page claiming
 * to know what a step is made of, which is the thing it should stop doing - see where the
 * steps are loaded.
 */
const newStep = () => ({ text: '' })

const recipe = ref<object>({
  title: "",
  description: "",
  steps: [newStep()],
  dishClass: "MAIN_DISH",
  ingredients: [],
  tips: "",
})

function getLocalizedLabel(item: any) {
  console.log(item)
  return t(item.label)
}

const onIngredientAutocompleteChange = async (query, index) => {
  queryList.value[index] = query
  if (!query) return
  const response = await searchIngredients(`query=${query}`, 0, 20);
  autocompleteList.value[index] = response.data.items.map(item => ({
    id: item.id,
    name: item.name?.[getLocale().toUpperCase()] || '',
    allowedTypes: item.allowedTypes,
    defaultUnit: item.defaultUnit,
  }));
}

/**
 * The typed text, offered as a custom ingredient when it matches nothing.
 *
 * Its `name` is the name itself, not the "use X as a custom ingredient" wording: `item-title` is
 * what the field displays once the option is picked, so putting the prompt there left the prompt
 * sitting in the input until the next blur. The wording lives in the item slot instead, and the
 * option is already the shape a picked row holds, so nothing has to be swapped in afterwards.
 */
const getAutocompleteItems = (index) => {
  const query = queryList.value[index]
  const items = autocompleteList.value[index] ?? []
  if (!query || items.some(it => it.name.toLowerCase() == query.toLowerCase())) return items
  return [...items, {id: null, name: query.trim().slice(0, CUSTOM_NAME_MAX_LENGTH), isCustom: true}]
}

function selectFirstMatch(index) {
  const query = queryList.value[index]
  const match = getAutocompleteItems(index).find(opt =>
    opt.name.toLowerCase().includes(query?.toLowerCase())
  )

  if (match) {
    recipe.value.ingredients[index].ingredient = match
    onIngredientSelected(match, index)
  }
}

const onIngredientSelected = (selected, index) => {
  if (selected) {
    queryList.value[index] = selected.name // Update displayed input text
  }
  recipe.value.ingredients[index].unit = findUnitOption(getDefaultUnit(selected))
}

/**
 * What the unit picker opens on for a freshly chosen ingredient.
 *
 * The catalogue's own default first, then weight, then whatever the ingredient allows - each
 * of them put on the ladder this author cooks on, so an imperial cook adding flour meets
 * ounces rather than grams and a picker to correct. The declared unit is mapped rather than
 * kept because it cannot have been a choice about *this* author: an operator sets it once
 * for the whole catalogue.
 *
 * Only the preselection. What the author then picks is what gets saved, as written.
 */
const getDefaultUnit = (ingredient) => {
  if (!ingredient) return "NONE"
  const preferred = (unit) => preferredUnitFor(unit, unitSystem.value, unitOptions.value)
  if (ingredient.defaultUnit) return preferred(ingredient.defaultUnit)
  const allowed = getUnitOptions(ingredient.allowedTypes)
  if (allowed.some(it => it.type == "WEIGHT")) return preferred("GRAM")
  const fallback = allowed.find(it => it.type != "NONE")?.value
  return fallback ? preferred(fallback) : "NONE"
}


// Function to add a new item
const addStep = () => {
  recipe.value.steps.push(newStep());
};

const addIngredient = () => {
  recipe.value.ingredients.push({ ingredient: null, complement: "" });
  queryList.value.push('');
  autocompleteList.value.push([]);
}

async function deleteStepAt(index) {
  if (recipe.value.steps[index]?.text) {
    return
  }
  recipe.value.steps.splice(index, 1);
  await nextTick()
  document.getElementById(`step_${index-1}`).focus()
}

async function addStepAt(index) {
  recipe.value.steps.splice(index + 1, 0, newStep());
  await nextTick()
  document.getElementById(`step_${index + 1}`).focus()
}

// Function to add a new item
const removeStep = (index) => {
  recipe.value.steps.splice(index,1);
};

const removeIngredient = (index) => {
  recipe.value.ingredients.splice(index,1);
}

// One save at a time: a second click while the first is on its way would create the recipe twice
const saving = ref(false)

async function onSubmit() {
  if (saving.value) return
  saving.value = true
  try {
    await submit()
  } finally {
    saving.value = false
  }
}

async function submit() {
  const {valid, errors} = await form.value.validate()
  if (!valid) {
    return
  }
  const submitted = JSON.parse(JSON.stringify(recipe.value))
  submitted.ingredients = recipe.value.ingredients
    .filter((it) => it.ingredient )
    .map(item => ({
    id: item.ingredient.id,
    customName: item.ingredient.id == null ? item.ingredient.name : null,
    amount: item.unit == null || item.unit.value == "NONE" ? null : item.amount,
    unit: item.unit ? item.unit.value : "NONE",
    complement: item.complement}))
  // Everything the step arrived with goes back, with only the text - the one thing this page
  // edits - taken from the form. See where the steps are loaded.
  submitted.steps = submitted.steps
    .filter((it) => it && it.text)
    .map(({ text, ...rest }) => ({ ...rest, text }))
  delete submitted['version']
  console.log(submitted)
  errorMessage.value = null
  // Saved and uploaded in two steps, each reporting its own failure: the fallback
  // wording is only right for the step it guards.
  try {
    if (recipeId.value == null) {
      const response = await createRecipe(submitted)
      recipeId.value = response.data.id
    } else {
      await updateRecipe(recipeId.value, submitted)
    }
  } catch (error) {
    console.log(error)
    errorMessage.value = toErrorMessage(error)
    return
  }

  try {
    await editablePicture.value.submitImage(recipeId.value)
  } catch (error) {
    console.log(error)
    // The recipe itself is already saved: stay on the form so that the image can
    // be submitted again instead of silently dropping it.
    errorMessage.value = toErrorMessage(error, "image_upload_failed")
    return
  }

  toViewRecipe(recipeId.value)
}

/**
 * Fills the editor in from a Cooklang file.
 *
 * **Nothing already written is overwritten.** A field that has been filled in keeps what was
 * typed, and ingredients and steps are appended — the same rule the app's scanner follows,
 * and for the same reason: an import that replaced the form would make a mis-picked file
 * cost whatever had been written, while one that only adds is undone by deleting some rows.
 *
 * The parsing is the backend's, not this page's. A `.cook` file has to be matched against the
 * ingredient catalogue to be worth anything, and that is a query rather than a guess — so
 * what comes back is already the recipe, with the rows the catalogue placed carrying its ids
 * and the rest left as free text.
 */
async function onCooklangFileSelected(event) {
  const file = event.target.files?.[0]
  // Cleared straight away so that picking the same file twice fires `change` both times.
  event.target.value = ''
  if (!file) return

  importing.value = true
  importMessage.value = null
  try {
    const {data} = await importCooklang(await file.text())
    prefillFrom(data)
  } catch (error) {
    console.log(error)
    errorMessage.value = toErrorMessage(error)
  } finally {
    importing.value = false
  }
}

/** See [onCooklangFileSelected]. `imported` is a `CooklangImportInfo`. */
function prefillFrom(imported) {
  const parsed = imported.recipe
  const blank = (value) => value == null || value === ''

  if (blank(recipe.value.title)) recipe.value.title = parsed.title
  if (blank(recipe.value.description)) recipe.value.description = parsed.description
  if (blank(recipe.value.tips)) recipe.value.tips = parsed.tips
  if (blank(recipe.value.yield)) recipe.value.yield = parsed.yield
  if (blank(recipe.value.preparationTime)) recipe.value.preparationTime = parsed.preparationTime
  if (blank(recipe.value.cookingTime)) recipe.value.cookingTime = parsed.cookingTime
  if (blank(recipe.value.cookingTemperature)) recipe.value.cookingTemperature = parsed.cookingTemperature
  if (blank(recipe.value.dishClass)) recipe.value.dishClass = parsed.dishClass

  // The row positions a step points at are positions in the *imported* list, so they shift by
  // however many ingredients the form already held.
  const offset = recipe.value.ingredients.length
  parsed.ingredients.forEach((item, index) => {
    recipe.value.ingredients.push({
      ingredient: {
        id: item.id,
        name: imported.ingredientNames[index] ?? item.customName,
        // Unknown for a row the catalogue placed until the picker asks; a free-text row has
        // no capability data at all, which is what lets it keep the unit the file named.
        allowedTypes: undefined,
        isCustom: item.id == null,
      },
      amount: item.amount,
      unit: findUnitOption(item.unit),
      complement: item.complement ?? "",
    })
    queryList.value.push(imported.ingredientNames[index] ?? item.customName ?? '')
    autocompleteList.value.push([])
  })

  const steps = parsed.steps.map((step) => ({
    ...step,
    ingredients: (step.ingredients ?? []).map((it) => ({...it, index: it.index + offset})),
  }))
  // The one step a new form starts on is a placeholder, not something somebody wrote.
  const existing = recipe.value.steps.filter((it) => it.text)
  recipe.value.steps = [...existing, ...steps]

  const notes = []
  if (imported.unmatchedIngredients > 0) {
    notes.push(t('import_cooklang_unmatched', {count: imported.unmatchedIngredients}))
  }
  if (imported.stepsWereSplit) notes.push(t('import_cooklang_split'))
  importMessage.value = notes.length ? notes.join(' ') : t('import_cooklang_done')
}

// Not awaited with the units: nothing on this page reads it until an ingredient is picked,
// and a picker that opened before it arrived would only have preselected metric
loadUnitSystem()

loadUnits().then(function () {
  if (recipeId.value == null) {
    ready.value = true
    return
  }
  getRecipe(recipeId.value).then (
    function (response) {
      recipe.value.title = response.data.title
      recipe.value.description = response.data.description
      recipe.value.dishClass = response.data.dishClass
      // Every field of a step is kept, not just the ones this page knows about.
      //
      // A step carries more than its words - a timer, the ingredients it uses, whatever comes
      // next - and none of it is edited here. Listing the fields to keep meant this page had
      // to be changed each time one was added, and forgetting to would not break a build: it
      // would quietly drop the field on the next save from the browser. Spreading keeps them
      // by default, so the failure mode of doing nothing is that a new field survives.
      recipe.value.steps = response.data.steps.map((step) => ({ ...step }))
      recipe.value.ingredients = response.data.ingredients.map(item => ({
        ingredient: {
          id: item.id,
          name: item.name,
          allowedTypes: item.allowedTypes,
        },
        amount: item.amount,
        unit: findUnitOption(item.unit),
        complement: item.complement,
      }))
      recipe.value.yield = response.data.yield
      recipe.value.preparationTime = response.data.preparationTime
      recipe.value.cookingTime = response.data.cookingTime
      recipe.value.cookingTemperature = response.data.cookingTemperature
      recipe.value.tips = response.data.tips
      recipe.value.version = response.data.version
      console.log(recipe.value)
      ready.value = true
    }).catch(function (error) {
    console.log(error);
  }).finally(function () {
    // always executed
  });
})



</script>


<style scoped>
.recipe-form {
  width: 100%;
  max-width: 1000px;
  margin: 0 auto;
}

.recipe-form__form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* The label sits above each field, so the fields need none of their default spacing */
.recipe-form :deep(.v-text-field) {
  margin-top: 0 !important;
  margin-bottom: 0 !important;
}

.import-message {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
}

.import-message__text {
  flex: 1;
  min-width: 0;
}

.recipe-form__picture {
  width: 100%;
  max-width: 440px;
}

.recipe-form__numbers {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.recipe-form__rows {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.form-row > .sk-icon-btn {
  margin-top: 4px;
}

.form-row__name {
  flex: 2 1 200px;
  min-width: 0;
}

.form-row__unit {
  flex: 1 1 130px;
  min-width: 0;
}

.form-row__amount {
  flex: 1 1 130px;
  min-width: 0;
}

.form-row__complement {
  flex: 1.5 1 160px;
  min-width: 0;
}

.form-row__step {
  flex: 1;
  min-width: 0;
}

.drag-handle {
  display: flex;
  align-items: center;
  height: 48px;
  cursor: grab;
  color: var(--ink);
}

.add-row {
  border-style: dashed;
  background: transparent;
}

.add-row:hover:not(:disabled) {
  background: var(--cream);
}

.recipe-form__actions {
  display: flex;
  justify-content: center;
  gap: 14px;
  margin-top: 8px;
}

@media (max-width: 760px) {
  .recipe-form__numbers {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .form-row--ingredient {
    flex-wrap: wrap;
    padding-bottom: 12px;
    border-bottom: 1px solid var(--rule);
  }

  .form-row--ingredient .form-row__name {
    flex-basis: calc(100% - 100px);
  }
}
</style>
