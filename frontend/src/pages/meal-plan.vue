<template>
  <div class="sk-page">
    <header class="mp-head">
      <h1 class="sk-title">{{ $t("meal_plan") }}</h1>

      <div v-if="isPremium" class="mp-nav">
        <button :aria-label="$t('meal_plan_previous_week')" class="sk-icon-btn sk-icon-btn--white" type="button" @click="shiftWeek(-7)">
          <v-icon>mdi-chevron-left</v-icon>
        </button>
        <span class="mp-range-wrap">
          <button :aria-label="`${weekRange} — ${$t('meal_plan_pick_date')}`" class="mp-range" type="button" @click="openDatePicker">
            <span>{{ weekRange }}</span>
            <v-icon>mdi-calendar-blank-outline</v-icon>
          </button>
          <!-- The native picker opens from here; the button is what is seen and focused -->
          <input
            ref="datePicker"
            aria-hidden="true"
            class="mp-range__input"
            tabindex="-1"
            type="date"
            :value="toIsoDate(weekStart)"
            @change="onDatePicked"
          >
        </span>
        <button :aria-label="$t('meal_plan_next_week')" class="sk-icon-btn sk-icon-btn--white" type="button" @click="shiftWeek(7)">
          <v-icon>mdi-chevron-right</v-icon>
        </button>
        <button class="sk-btn sk-btn--mustard sk-btn--lg mp-plan" type="button" @click="openAdd(null, null)">
          <v-icon>mdi-plus</v-icon>{{ $t("meal_plan_plan_a_meal") }}
        </button>
      </div>
    </header>

    <!-- Shown locked rather than hidden, and never asked for: the routes refuse anyone else -->
    <section v-if="!isPremium" class="sticker sk-pad-lg mp-locked">
      <span aria-hidden="true" class="mp-locked__icon"><v-icon>mdi-lock-outline</v-icon></span>
      <div class="sk-stack mp-locked__text">
        <h2 class="sk-h2">{{ $t("meal_plan_locked_title") }}</h2>
        <p class="mp-locked__message">{{ $t("meal_plan_locked_message") }}</p>
        <span class="sk-pill sk-pill--mustard sk-pill--lg mp-locked__badge">{{ $t("meal_plan_premium_badge") }}</span>
      </div>
    </section>

    <template v-else>
      <nav :aria-label="weekRange" class="sticker mp-strip">
        <button
          v-for="day in days"
          :key="day.iso"
          :aria-label="`${longDate(day.date)}, ${$t('meal_plan_meals_count', {count: day.meals.length}, day.meals.length)}`"
          class="mp-strip__day"
          type="button"
          @click="focusDay(day.iso)"
        >
          <span class="mp-strip__name">{{ shortWeekday(day.date) }}</span>
          <span class="mp-strip__number" :class="{'is-today': day.isToday}">{{ day.date.getDate() }}</span>
          <span aria-hidden="true" class="mp-strip__dots">
            <span
              v-for="slot in dotSlots(day)"
              :key="slot"
              class="mp-dot"
              :class="{'is-on': day.meals.some(m => m.slot === slot)}"
            />
          </span>
        </button>
      </nav>

      <div v-if="loadFailed && !hasLoaded" class="sticker sk-pad mp-message" role="alert">
        <span>{{ $t("meal_plan_load_failed") }}</span>
        <button class="sk-btn" type="button" @click="load">{{ $t("meal_plan_retry") }}</button>
      </div>

      <div v-else-if="!hasLoaded" class="mp-loading">
        <v-progress-circular :aria-label="$t('meal_plan_loading')" color="ink" indeterminate />
      </div>

      <div v-else class="mp-days">
        <article
          v-for="day in days"
          :id="`mp-day-${day.iso}`"
          :key="day.iso"
          class="sticker mp-day"
          :class="{'sticker--cream': day.isToday}"
          tabindex="-1"
        >
          <header class="mp-day__head">
            <h2 class="sk-h3 mp-day__title">{{ dayTitle(day.date) }}</h2>
            <span v-if="day.isToday" class="sk-pill sk-pill--coral">{{ $t("today") }}</span>
            <button
              :aria-controls="`mp-day-body-${day.iso}`"
              :aria-expanded="!collapsed.has(day.iso)"
              :aria-label="dayTitle(day.date)"
              class="mp-plain mp-day__toggle"
              type="button"
              @click="toggleDay(day.iso)"
            >
              <v-icon>{{ collapsed.has(day.iso) ? 'mdi-chevron-down' : 'mdi-chevron-up' }}</v-icon>
            </button>
          </header>

          <div v-show="!collapsed.has(day.iso)" :id="`mp-day-body-${day.iso}`" class="mp-day__body">
            <p v-if="day.meals.length === 0" class="sk-muted mp-day__empty">{{ $t("meal_plan_nothing_planned") }}</p>

            <section v-for="meal in day.meals" :key="meal.slot" class="mp-meal">
              <h3 class="sk-eyebrow mp-meal__slot">{{ slotName(meal.slot) }}</h3>
              <ul class="mp-dishes">
                <li v-for="dish in meal.dishes" :key="dish.id" class="mp-dish">
                  <span v-if="!dish.hasCooked" aria-hidden="true" class="mp-thumb mp-thumb--cream">
                    <v-icon>mdi-food-off-outline</v-icon>
                  </span>
                  <span v-else-if="dish.recipe" aria-hidden="true" class="mp-thumb sk-placeholder">
                    <img
                      alt=""
                      loading="lazy"
                      :src="getRecipeThumbnailUrl(dish.recipe.id, dish.recipe.version)"
                      @error="(e) => (e.target as HTMLElement).remove()"
                    >
                  </span>
                  <span v-else aria-hidden="true" class="mp-thumb">
                    <v-icon>mdi-silverware-fork-knife</v-icon>
                  </span>

                  <div class="mp-dish__text">
                    <a
                      v-if="dish.hasCooked && dish.recipe"
                      class="mp-dish__title mp-dish__link"
                      :href="`/recipe/view?id=${dish.recipe.id}`"
                      @click.prevent="toViewRecipe(dish.recipe.id)"
                    >{{ dish.title }}</a>
                    <span v-else class="mp-dish__title">{{ dishTitle(dish) }}</span>
                    <span v-if="dish.hasCooked && dish.servings != null" class="mp-dish__serves">
                      {{ $t("meal_plan_serves", {count: dish.servings}) }}
                    </span>
                  </div>

                  <v-menu location="bottom end">
                    <template #activator="{ props: menuProps }">
                      <button
                        v-bind="menuProps"
                        :aria-label="$t('meal_plan_dish_actions', {dish: dishTitle(dish)})"
                        class="mp-plain mp-dish__more"
                        type="button"
                      >
                        <v-icon>mdi-dots-horizontal</v-icon>
                      </button>
                    </template>
                    <v-list density="compact">
                      <v-list-item
                        v-if="dish.hasCooked && dish.recipe"
                        prepend-icon="mdi-book-open-variant"
                        :title="$t('meal_plan_open_recipe')"
                        @click="toViewRecipe(dish.recipe.id)"
                      />
                      <v-list-item
                        prepend-icon="mdi-pencil-outline"
                        :title="$t('meal_plan_edit_dish')"
                        @click="openEdit(dish)"
                      />
                      <v-list-item
                        prepend-icon="mdi-delete-outline"
                        :title="$t('meal_plan_remove')"
                        @click="removeDish(dish)"
                      />
                    </v-list>
                  </v-menu>
                </li>
              </ul>
            </section>

            <button
              v-if="day.isToday"
              :aria-label="$t('meal_plan_add_to_meal', {meal: slotName(nextOpenSlot(day.entries))})"
              class="mp-add"
              type="button"
              @click="openAdd(day.date, nextOpenSlot(day.entries))"
            >
              <v-icon>mdi-plus</v-icon>{{ slotName(nextOpenSlot(day.entries)) }}
            </button>
          </div>
        </article>
      </div>
    </template>

    <v-dialog v-model="dialog.open" max-width="520" :persistent="dialog.saving">
      <form
        aria-labelledby="mp-dialog-title"
        class="sticker sk-pad-lg mp-dialog"
        @submit.prevent="saveDialog"
      >
        <h2 id="mp-dialog-title" class="sk-h2">
          {{ dialog.mode === 'add' ? $t("meal_plan_plan_a_meal") : $t("meal_plan_edit_dish") }}
        </h2>

        <div class="mp-dialog__row">
          <label class="sk-field">
            <span class="sk-label">{{ $t("meal_plan_day") }}</span>
            <input v-model="dialog.date" class="sk-input" required type="date">
          </label>
          <label class="sk-field">
            <span class="sk-label">{{ $t("meal_plan_meal") }}</span>
            <select v-model="dialog.slot" class="sk-input">
              <option v-for="slot in MEAL_SLOTS" :key="slot" :value="slot">{{ slotName(slot) }}</option>
            </select>
          </label>
        </div>

        <div v-if="dialog.mode === 'add'" :aria-label="$t('meal_plan_what')" class="sk-chip-row" role="group">
          <button
            v-for="kind in kinds"
            :key="kind.value"
            :aria-pressed="dialog.kind === kind.value"
            class="sk-chip"
            type="button"
            @click="dialog.kind = kind.value"
          >{{ $t(kind.label) }}</button>
        </div>

        <div v-if="dialog.mode === 'add' && dialog.kind === 'recipe'" class="sk-field">
          <span id="mp-recipe-label" class="sk-label">{{ $t("meal_plan_recipe") }}</span>
          <v-autocomplete
            v-model="dialog.recipe"
            aria-labelledby="mp-recipe-label"
            density="comfortable"
            hide-details
            item-title="title"
            item-value="id"
            :items="recipeItems"
            :loading="recipesLoading"
            :no-data-text="$t('meal_plan_no_recipe')"
            no-filter
            :placeholder="$t('meal_plan_search_recipes')"
            return-object
            @update:search="onRecipeSearch"
          >
            <template #item="{ props: itemProps, item }">
              <v-list-item v-bind="itemProps">
                <template #prepend>
                  <span class="mp-thumb mp-thumb--sm sk-placeholder">
                    <img alt="" loading="lazy" :src="getRecipeThumbnailUrl(item.raw.id, item.raw.version)">
                  </span>
                </template>
              </v-list-item>
            </template>
          </v-autocomplete>
        </div>

        <label v-if="showsTitle" class="sk-field">
          <span class="sk-label">{{ $t("meal_plan_dish_name") }}</span>
          <input
            v-model="dialog.title"
            autocomplete="off"
            class="sk-input"
            list="mp-suggestions"
            :maxlength="MEAL_PLAN_TITLE_MAX_LENGTH"
            :placeholder="$t('meal_plan_dish_name_placeholder')"
            required
            type="text"
            @input="onTitleInput"
          >
          <datalist id="mp-suggestions">
            <option v-for="suggestion in suggestions" :key="suggestion" :value="suggestion" />
          </datalist>
        </label>

        <p v-if="dialog.mode === 'add' && dialog.kind === 'notCooked'" class="sk-hint">
          {{ $t("meal_plan_not_cooked_hint") }}
        </p>

        <label v-if="showsServings" class="sk-field mp-dialog__servings">
          <span class="sk-label">{{ $t("meal_plan_servings") }}</span>
          <input
            v-model="dialog.servings"
            class="sk-input"
            inputmode="numeric"
            :max="MEAL_PLAN_MAX_SERVINGS"
            min="1"
            step="1"
            type="number"
          >
        </label>

        <p v-if="dialog.error" class="mp-dialog__error" role="alert">{{ $t(dialog.error) }}</p>

        <div class="mp-dialog__actions">
          <button class="sk-btn" :disabled="dialog.saving" type="button" @click="dialog.open = false">
            {{ $t("cancel") }}
          </button>
          <button class="sk-btn sk-btn--coral" :disabled="!canSave" type="submit">
            {{ $t("save") }}
          </button>
        </div>
      </form>
    </v-dialog>

    <v-snackbar v-model="snackbar" color="ink" timeout="4000">
      {{ snackbarText ? $t(snackbarText) : '' }}
    </v-snackbar>
  </div>
</template>

<script lang="ts" setup>
  import { useI18n } from 'vue-i18n'
  import { debounce } from 'lodash'
  import { useAuthStore } from '@/stores/auth'
  import { getRecipeThumbnailUrl, toErrorMessage, toViewRecipe } from '@/scripts/common'
  import {
    addDays,
    createMealPlanEntry,
    DEFAULT_MEAL_SERVINGS,
    deleteMealPlanEntry,
    fromIsoDate,
    listMealPlan,
    MEAL_PLAN_MAX_SERVINGS,
    MEAL_PLAN_TITLE_MAX_LENGTH,
    MEAL_SLOTS,
    type MealPlanEntry,
    mealPlanSuggestions,
    type MealSlot,
    nextOpenSlot,
    searchOwnRecipes,
    toIsoDate,
    updateMealPlanEntry,
    weekStartOf,
  } from '@/scripts/mealPlan'

  const { t, locale } = useI18n()
  const authStore = useAuthStore()

  // Set when the plan refuses us anyway — a grant that ended since the session was read
  const refused = ref(false)
  // The backend's own flag, which already answers true for an admin. Computed, for the reason
  // PremiumOnly's is: it must follow the auth store rather than be read once.
  const isPremium = computed(() => authStore.isPremium === true && !refused.value)

  // ── The week on screen ────────────────────────────────────────────────────────

  const today = ref(new Date())
  const weekStart = ref(weekStartOf(today.value))
  const entries = ref<MealPlanEntry[]>([])
  const hasLoaded = ref(false)
  const loadFailed = ref(false)
  const collapsed = ref(new Set<string>())

  let loadToken = 0

  async function load () {
    if (!isPremium.value) return
    const token = ++loadToken
    const from = toIsoDate(weekStart.value)
    const to = toIsoDate(addDays(weekStart.value, 6))
    loadFailed.value = false
    try {
      const response = await listMealPlan(from, to)
      // An answer for a week already navigated away from is not this week's
      if (token !== loadToken) return
      entries.value = response.data
      hasLoaded.value = true
    } catch (error) {
      if (token !== loadToken) return
      if (toErrorMessage(error, '') === 'premium_required') refused.value = true
      loadFailed.value = true
    }
  }

  watch(isPremium, premium => {
    if (premium) load()
  }, { immediate: true })

  function shiftWeek (days: number) {
    showWeekOf(addDays(weekStart.value, days))
  }

  function showWeekOf (date: Date) {
    const start = weekStartOf(date)
    if (toIsoDate(start) === toIsoDate(weekStart.value)) return
    weekStart.value = start
    hasLoaded.value = false
    entries.value = []
    load()
  }

  const datePicker = ref<HTMLInputElement | null>(null)

  function openDatePicker () {
    const input = datePicker.value
    if (!input) return
    try {
      input.showPicker()
    } catch {
      input.focus()
    }
  }

  function onDatePicked (event: Event) {
    const date = fromIsoDate((event.target as HTMLInputElement).value)
    if (date) showWeekOf(date)
  }

  interface PlannedMeal { slot: MealSlot, dishes: MealPlanEntry[] }
  interface PlannedDay {
    date: Date,
    iso: string,
    isToday: boolean,
    entries: MealPlanEntry[],
    meals: PlannedMeal[],
  }

  /** The seven days from Monday, each with its meals in the order a day is eaten in. */
  const days = computed<PlannedDay[]>(() => {
    const todayIso = toIsoDate(today.value)
    return Array.from({ length: 7 }, (_, i) => {
      const date = addDays(weekStart.value, i)
      const iso = toIsoDate(date)
      const ofTheDay = entries.value.filter(e => e.date === iso)
      const meals = MEAL_SLOTS
        .map(slot => ({
          slot,
          dishes: ofTheDay.filter(e => e.slot === slot).sort((a, b) => a.position - b.position || a.id - b.id),
        }))
        .filter(meal => meal.dishes.length > 0)
      return { date, iso, isToday: iso === todayIso, entries: ofTheDay, meals }
    })
  })

  /** Lunch and dinner always, the other two only when something is planned for them. */
  function dotSlots (day: PlannedDay): MealSlot[] {
    return MEAL_SLOTS.filter(slot => slot === 'LUNCH' || slot === 'DINNER' || day.meals.some(m => m.slot === slot))
  }

  function toggleDay (iso: string) {
    const next = new Set(collapsed.value)
    if (next.has(iso)) next.delete(iso)
    else next.add(iso)
    collapsed.value = next
  }

  function focusDay (iso: string) {
    if (collapsed.value.has(iso)) toggleDay(iso)
    const card = document.getElementById(`mp-day-${iso}`)
    if (!card) return
    card.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    card.focus({ preventScroll: true })
  }

  // ── Words ─────────────────────────────────────────────────────────────────────

  // English the way the rest of the product writes dates, day before month: "9 – 15 November"
  const intlLocale = computed(() => locale.value === 'en' ? 'en-GB' : locale.value)
  const capitalize = (s: string) => s.charAt(0).toUpperCase() + s.slice(1)

  const weekRange = computed(() => {
    const end = addDays(weekStart.value, 6)
    const format = new Intl.DateTimeFormat(intlLocale.value, { day: 'numeric', month: 'long' })
    return format.formatRange ? format.formatRange(weekStart.value, end) : `${format.format(weekStart.value)} – ${format.format(end)}`
  })

  const dayTitle = (date: Date) =>
    capitalize(new Intl.DateTimeFormat(intlLocale.value, { weekday: 'long', day: 'numeric' }).format(date))
  const shortWeekday = (date: Date) =>
    new Intl.DateTimeFormat(intlLocale.value, { weekday: 'short' }).format(date).replace(/\.$/, '')
  const longDate = (date: Date) =>
    capitalize(new Intl.DateTimeFormat(intlLocale.value, { weekday: 'long', day: 'numeric', month: 'long' }).format(date))

  const slotName = (slot: MealSlot) => t(`meal_slot_${slot.toLowerCase()}`)

  /** A meal not cooked names no dish: the backend leaves its title empty for us to word. */
  const dishTitle = (dish: MealPlanEntry) => dish.hasCooked ? dish.title : t('meal_plan_not_cooked')

  // ── Changing the plan ─────────────────────────────────────────────────────────

  const snackbar = ref(false)
  const snackbarText = ref<string | null>(null)

  function notify (key: string) {
    snackbarText.value = key
    snackbar.value = true
  }

  async function removeDish (dish: MealPlanEntry) {
    try {
      await deleteMealPlanEntry(dish.id)
      entries.value = entries.value.filter(e => e.id !== dish.id)
    } catch (error) {
      notify(toErrorMessage(error, 'meal_plan_save_failed'))
    }
    load()
  }

  type DishKind = 'recipe' | 'words' | 'notCooked'

  const kinds: {value: DishKind, label: string}[] = [
    { value: 'recipe', label: 'meal_plan_kind_recipe' },
    { value: 'words', label: 'meal_plan_kind_words' },
    { value: 'notCooked', label: 'meal_plan_not_cooked' },
  ]

  const dialog = reactive({
    open: false,
    mode: 'add' as 'add' | 'edit',
    entry: null as MealPlanEntry | null,
    date: '',
    slot: 'DINNER' as MealSlot,
    kind: 'recipe' as DishKind,
    recipe: null as {id: number, title: string, version: number} | null,
    title: '',
    servings: '' as string | number,
    saving: false,
    error: null as string | null,
  })

  /** Only a dish shown as words has a name to change: a recipe names itself. */
  const showsTitle = computed(() => dialog.mode === 'add'
    ? dialog.kind === 'words'
    : dialog.entry != null && dialog.entry.hasCooked && dialog.entry.recipe == null)

  const showsServings = computed(() => dialog.mode === 'add'
    ? dialog.kind !== 'notCooked'
    : dialog.entry?.hasCooked === true)

  /** Empty is "not said"; anything else has to be a whole number the backend accepts. */
  const servingsValue = computed<number | null | undefined>(() => {
    const raw = String(dialog.servings ?? '').trim()
    if (raw === '') return null
    const n = Number(raw)
    return Number.isInteger(n) && n >= 1 && n <= MEAL_PLAN_MAX_SERVINGS ? n : undefined
  })

  const canSave = computed(() => {
    if (dialog.saving || !fromIsoDate(dialog.date)) return false
    if (showsServings.value && servingsValue.value === undefined) return false
    if (showsTitle.value && !dialog.title.trim()) return false
    if (dialog.mode === 'add' && dialog.kind === 'recipe' && !dialog.recipe) return false
    return true
  })

  function openAdd (date: Date | null, slot: MealSlot | null) {
    Object.assign(dialog, {
      open: true,
      mode: 'add',
      entry: null,
      date: toIsoDate(date ?? today.value),
      slot: slot ?? nextOpenSlot(entries.value.filter(e => e.date === toIsoDate(date ?? today.value))),
      kind: 'recipe',
      recipe: null,
      title: '',
      servings: DEFAULT_MEAL_SERVINGS,
      saving: false,
      error: null,
    })
    searchRecipes('')
    loadSuggestions('')
  }

  function openEdit (dish: MealPlanEntry) {
    Object.assign(dialog, {
      open: true,
      mode: 'edit',
      entry: dish,
      date: dish.date,
      slot: dish.slot,
      kind: dish.hasCooked ? (dish.recipe ? 'recipe' : 'words') : 'notCooked',
      recipe: null,
      title: dish.title,
      servings: dish.servings ?? '',
      saving: false,
      error: null,
    })
    if (showsTitle.value) loadSuggestions('')
  }

  async function saveDialog () {
    if (!canSave.value) return
    dialog.saving = true
    dialog.error = null
    try {
      if (dialog.mode === 'add') {
        const base = { date: dialog.date, slot: dialog.slot }
        if (dialog.kind === 'notCooked') {
          await createMealPlanEntry({ ...base, hasCooked: false })
        } else if (dialog.kind === 'recipe') {
          await createMealPlanEntry({ ...base, recipeId: dialog.recipe!.id, servings: servingsValue.value })
        } else {
          await createMealPlanEntry({ ...base, title: dialog.title.trim(), servings: servingsValue.value })
        }
      } else {
        const entry = dialog.entry!
        // A name is sent only when it changed: a rename lets go of a recipe gone out of reach,
        // which a save that merely moved the dish must not do by accident
        const title = dialog.title.trim()
        const renamed = showsTitle.value && title !== entry.title ? title : null
        await updateMealPlanEntry(entry.id, {
          date: dialog.date,
          slot: dialog.slot,
          // Stored as sent, so a meal not cooked keeps the null it has
          servings: entry.hasCooked ? (servingsValue.value ?? null) : entry.servings,
          title: renamed,
        })
      }
      dialog.open = false
      load()
    } catch (error) {
      dialog.error = toErrorMessage(error, 'meal_plan_save_failed')
    } finally {
      dialog.saving = false
    }
  }

  // ── What the dialog offers ────────────────────────────────────────────────────

  const recipeItems = ref<object[]>([])
  const recipesLoading = ref(false)
  const suggestions = ref<string[]>([])
  let recipeToken = 0

  async function searchRecipes (query: string) {
    if (authStore.id == null) return
    const token = ++recipeToken
    recipesLoading.value = true
    try {
      const response = await searchOwnRecipes(authStore.id, query || '')
      if (token === recipeToken) recipeItems.value = response.data
    } catch (error) {
      console.error(error)
    } finally {
      if (token === recipeToken) recipesLoading.value = false
    }
  }

  const onRecipeSearch = debounce((query: string) => {
    // Vuetify reports the picked item's title as a search too; that is not one
    if (dialog.recipe && query === dialog.recipe.title) return
    searchRecipes(query)
  }, 250)

  async function loadSuggestions (query: string) {
    try {
      const response = await mealPlanSuggestions(query)
      suggestions.value = response.data
    } catch {
      suggestions.value = []
    }
  }

  const onTitleInput = debounce(() => loadSuggestions(dialog.title), 250)
</script>

<style scoped>
.mp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
}

.mp-nav {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.mp-range-wrap {
  position: relative;
  display: inline-flex;
}

.mp-range {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding: 0 10px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--ink);
  font-family: var(--font);
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
}

.mp-range:hover {
  background: rgba(255, 255, 255, .25);
}

.mp-range:focus-visible {
  outline: 3px solid var(--mustard);
  outline-offset: 2px;
}

.mp-range .v-icon {
  font-size: 18px;
}

.mp-range__input {
  position: absolute;
  left: 0;
  bottom: 0;
  width: 100%;
  height: 0;
  padding: 0;
  border: 0;
  opacity: 0;
  pointer-events: none;
}

.mp-plain {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--ink);
  cursor: pointer;
}

.mp-plain:hover {
  background: rgba(13, 24, 33, .08);
}

.mp-plain:focus-visible {
  outline: 3px solid var(--mustard);
  outline-offset: 1px;
}

/* ---- Locked ---- */

.mp-locked {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  max-width: 720px;
}

.mp-locked__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 56px;
  height: 56px;
  border: var(--line) solid var(--ink);
  border-radius: var(--radius-sm);
  background: var(--mustard);
}

.mp-locked__icon .v-icon {
  font-size: 28px;
}

.mp-locked__message {
  margin: 0;
  font-size: 15px;
  line-height: 1.5;
}

.mp-locked__badge {
  align-self: flex-start;
}

/* ---- The week strip ---- */

.mp-strip {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
  max-width: 730px;
  padding: 12px 16px;
}

.mp-strip__day {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 4px 0;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--ink);
  font-family: var(--font);
  cursor: pointer;
}

.mp-strip__day:hover {
  background: var(--mint);
}

.mp-strip__day:focus-visible {
  outline: 3px solid var(--mustard);
  outline-offset: 1px;
}

.mp-strip__name {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .06em;
  text-transform: uppercase;
  color: var(--muted);
}

.mp-strip__number {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  font-size: 17px;
  font-weight: 700;
}

.mp-strip__number.is-today {
  border: var(--line) solid var(--ink);
  background: var(--coral);
  color: var(--white);
}

.mp-strip__dots {
  display: flex;
  gap: 3px;
  min-height: 5px;
}

.mp-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #9aa59d;
}

.mp-dot.is-on {
  background: var(--ink);
}

/* ---- Days ---- */

.mp-message {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.mp-loading {
  display: flex;
  justify-content: center;
  padding: 32px 0;
}

.mp-days {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  align-items: start;
  gap: 22px 18px;
  padding: 0 var(--lift) var(--lift) 0;
}

@media (max-width: 1280px) {
  .mp-days { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 960px) {
  .mp-days { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 600px) {
  .mp-days { grid-template-columns: minmax(0, 1fr); }
  .mp-strip { padding: 10px 6px; }
}

.mp-day {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px 16px;
  min-width: 0;
}

.mp-day:focus-visible {
  outline: 3px solid var(--mustard);
  outline-offset: 3px;
}

.mp-day__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mp-day__title {
  min-width: 0;
}

.mp-day__toggle {
  margin-left: auto;
}

.mp-day__body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mp-day__empty {
  margin: 0;
  font-size: 14px;
}

.mp-meal {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mp-meal__slot {
  margin: 0;
}

.mp-dishes {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.mp-dish {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.mp-thumb {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  border: 1.5px solid var(--ink);
  border-radius: var(--radius-sm);
  background: var(--mint);
  overflow: hidden;
}

.mp-thumb img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.mp-thumb .v-icon {
  font-size: 22px;
}

.mp-thumb--cream {
  background: var(--cream);
}

.mp-thumb--sm {
  width: 36px;
  height: 36px;
  margin-right: 12px;
}

.mp-dish__text {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.mp-dish__title {
  color: var(--ink);
  font-size: 16px;
  font-weight: 700;
  line-height: 1.25;
  overflow-wrap: anywhere;
}

.mp-dish__link {
  text-decoration: none;
}

.mp-dish__link:hover {
  text-decoration: underline;
}

.mp-dish__link:focus-visible {
  outline: 3px solid var(--mustard);
  outline-offset: 2px;
  border-radius: 4px;
}

.mp-dish__serves {
  font-size: 13px;
  color: var(--muted);
}

.mp-add {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 48px;
  border: var(--line) dashed var(--ink);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--ink);
  font-family: var(--font);
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}

.mp-add:hover {
  background: rgba(255, 255, 255, .5);
}

.mp-add:focus-visible {
  outline: 3px solid var(--mustard);
  outline-offset: 2px;
}

/* ---- The dialog ---- */

.mp-dialog {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.mp-dialog__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

@media (max-width: 480px) {
  .mp-dialog__row { grid-template-columns: 1fr; }
}

.mp-dialog__servings {
  max-width: 160px;
}

.mp-dialog__error {
  margin: 0;
  color: var(--coral);
  font-weight: 700;
  font-size: 14px;
}

.mp-dialog__actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
}
</style>
