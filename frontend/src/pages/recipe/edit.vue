<template>
  <v-card
  class="mx-auto pa-5 ma-auto my-5"
  max-width="1000px"
  style="border: 4px solid #0d1821 !important;"
  >


    <v-form @submit.prevent="submit" class="mx-auto" ref="form">

      <!--
        Only while creating. An import fills blanks and appends, so on an existing recipe it
        would read as "add this file to that recipe" - which is a thing to want, and not what
        anyone would expect a button labelled "import" to do.
      -->
      <div v-if="recipeId == null" class="d-flex align-center mb-4">
        <v-btn
          prepend-icon="mdi-file-upload-outline"
          variant="tonal"
          :loading="importing"
          @click="cooklangInput?.click()"
        >{{ $t('import_cooklang') }}</v-btn>
        <span class="text-caption ml-3">{{ $t('import_cooklang_hint') }}</span>
        <input
          ref="cooklangInput"
          type="file"
          accept=".cook,text/plain"
          class="d-none"
          @change="onCooklangFileSelected"
        />
      </div>

      <v-alert
        v-if="importMessage"
        type="info"
        variant="tonal"
        class="mb-4"
        closable
        @click:close="importMessage = null"
      >{{ importMessage }}</v-alert>

      <v-text-field
        v-model="recipe.title"
        :label="`${$t('title')}`"
        :rules="[requiredRule, max100]"
      ></v-text-field>

      <v-textarea
        v-model="recipe.description"
        :label="`${$t('description')}`"
        :rules="[max255]"
      ></v-textarea>

      <div class="d-flex flex-wrap mx-2">
        <v-btn-toggle
          v-model="recipe.dishClass"
          color="primary"
          bg-color="background"
          base-color="background"
          rounded="lg"
          group
          mandatory
          class="ga-1 my-1 ml-n3 flex-wrap"
          style="height: auto; align-items: flex-start;"
        >
          <v-btn
            v-for="dishClass in dishOptions"
            :key="dishClass.value"
            :value="dishClass.value"
            class="ma-1 pa-1 px-2"
            height="45"
          >
            {{ $t(dishClass.label) }}
          </v-btn>
        </v-btn-toggle>
      </div>

      <editable-picture
         v-if="ready"
         path="recipes"
         :id="recipeId"
         :version="recipe.version"
         ref="editablePicture"
         width="100%"
         :aspect-ratio="4/3"
       ></editable-picture>


      <v-number-input
        v-model="recipe.yield"
        :label="`${$t('yield')}`"
        type="number"
        color="primary"
        :min=1
        :rules="[requiredRule]"
      ></v-number-input>

      <v-number-input
        v-model="recipe.preparationTime"
        :label="`${$t('time_preparation')}`"
        type="number"
        :step="5"
        :min=0
      ></v-number-input>

      <v-number-input
        v-model="recipe.cookingTime"
        :label="`${$t('time_cooking')}`"
        type="number"
        :step="5"
        :min=0
      ></v-number-input>

      <v-number-input
        v-model="recipe.cookingTemperature"
        :label="`${$t('cooking_temperature')}`"
        type="number"
        :step="5"
        :min=0
      >
      </v-number-input>

      <!-- Ingredients -->
      <h2 class="my-3 mt-12" >{{$t("ingredients")}}</h2>
      <draggable v-model="recipe.ingredients" tag="div" ghost-class="ghost" item-key="index" handle=".drag-handle">
        <template #item="{ element, index }">
          <div class="d-flex flex-wrap align-center mb-2">
            <!-- Handle for dragging -->
            <v-icon
              class="mr-2 drag-handle"
              color="black"
              small
            >mdi-drag</v-icon>

            <v-autocomplete
              v-model="recipe.ingredients[index].ingredient"
              :label="`${$t('ingredient')} ${index + 1}`"
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
              class="ma-1"
              min-width="150px"
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
              :label="`${$t('unit')}`"
              :items="getUnitOptions(recipe.ingredients[index].ingredient?.allowedTypes)"
              color="primary"
              :item-title="getLocalizedLabel"
              item-value="value"
              return-object
              min-width="120px"
              class="ma-1"
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
              :label="`${$t('amount')}`"
              color="primary"
              control-variant="stacked"
              min=0
              item-title="amount"
              return-object
              class="ma-1"
              max-width="150px"
              v-if="recipe.ingredients[index].unit?.value != 'NONE'"
            ></v-number-input>

            <v-text-field
              v-model="recipe.ingredients[index].complement"
              :label="`${$t('complement')}`"
              :rules="[max50]"
              class="ma-1"
            ></v-text-field>

            <div>
              <v-btn
                @click="removeIngredient(index)"
                icon="mdi-delete"
                color="primary"
                class="ml-4"
              ></v-btn>
            </div>


          </div>
        </template>
      </draggable>

      <!-- Button to add ingredient -->
      <v-btn
        @click="addIngredient"
        prepend-icon="mdi-plus-circle-outline"
        color="primary"
        flat
        class="mb-2 mr-2"
      >{{$t("ingredient")}}</v-btn>


      <!-- Steps -->
      <h2 class="my-3" >{{$t("steps")}}</h2>
      <draggable v-model="recipe.steps" tag="div" ghost-class="ghost" item-key="index" handle=".drag-handle">
        <template #item="{ element, index }">
          <div class="d-flex align-center">
            <!-- Add a handle for dragging -->
            <v-icon
              class="mr-2 drag-handle"
              color="black"
              small
            >mdi-drag</v-icon>

            <v-text-field
              v-model="recipe.steps[index].text"
              :label="`${$t('step')} ${index + 1}`"
              :id="`step_${index}`"
              :rules="[max255]"
              @keyup.enter="addStepAt(index)"
              @keyup.delete="deleteStepAt(index)"
            ></v-text-field>

            <v-btn
              @click="removeStep(index)"
              icon="mdi-delete"
              color="primary"
              class="ml-4"
              tabindex="-1"
            ></v-btn>
          </div>
        </template>
      </draggable>

      <!-- Button to add a new item -->
      <v-btn
        @click="addStep"
        prepend-icon="mdi-plus-circle-outline"
        color="primary"
        flat
        class="mb-10 mt-2"
      >{{$t("steps_add_new")}}</v-btn>

        <v-textarea
          v-model="recipe.tips"
          :label="`${$t('tips')}`"
          :rules="[max511]"
        ></v-textarea>

      <error :error="errorMessage"></error>

      <v-container>
        <v-row
          class="d-flex align-center justify-center align-content-center mb-2 ga-4"
          dense
        >
            <action-button
              v-if="recipeId"
              icon="mdi-close-circle-outline"
              :text="`${$t('cancel')}`"
              :action="() => toViewRecipe(recipeId)"
            ></action-button>
            <action-button
              icon="mdi-content-save"
              :text="`${$t('save')}`"
              :action="submit"
            ></action-button>
        </v-row>
      </v-container>
    </v-form>
  </v-card>

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

.drag-handle {
  cursor: grab;
  display:flex;
  align-items: center;
}


</style>
