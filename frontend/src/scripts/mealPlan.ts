import apiClient from '@/plugins/axios.js'
import { listRecipes } from '@/scripts/recipes'

/*
 * The account's meal plan (`MealPlanController`).
 *
 * Premium on every route: anyone else is answered `403 premium_required`, so nothing here is
 * called for an account the auth store already knows is not premium — the page shows itself
 * locked instead.
 *
 * A date is a day, `yyyy-MM-dd`, not epoch seconds like every other date in the API: a dinner
 * on Tuesday is on Tuesday wherever the browser is. So days are built from the browser's local
 * calendar and never go through `toISOString`, which would move them to UTC.
 */

export type MealSlot = 'BREAKFAST' | 'LUNCH' | 'SNACK' | 'DINNER'

export interface MealPlanEntry {
  id: number
  date: string
  slot: MealSlot
  position: number
  /** Always set, except on a meal noted as not cooked, which the client words itself. */
  title: string
  servings: number | null
  /** Null when there is no recipe to open: words, or a recipe gone out of reach. */
  recipe: { id: number, version: number } | null
  hasCooked: boolean
}

export interface MealPlanEntrySave {
  date: string
  slot: MealSlot
  recipeId?: number | null
  title?: string | null
  servings?: number | null
  hasCooked?: boolean
}

export interface MealPlanEntryEdit {
  date: string
  slot: MealSlot
  /** Stored as sent: null clears it. */
  servings: number | null
  /** Only for a dish shown as words, and only when it changed — see `updateMealPlanEntry`. */
  title?: string | null
}

/** `shared.enums.MealSlot`, in the order a day is eaten in, which is the order a day lists them. */
const MEAL_SLOTS: MealSlot[] = ['BREAKFAST', 'LUNCH', 'SNACK', 'DINNER']

/**
 * What a dish is planned for when nobody said: one, as in the app. A recipe too, whose yield
 * says how many it makes rather than how many sit down to it.
 */
const DEFAULT_MEAL_SERVINGS = 1

/** `shared.dto.MEAL_PLAN_MAX_SERVINGS` and `MEAL_PLAN_TITLE_MAX_LENGTH`, restated. */
const MEAL_PLAN_MAX_SERVINGS = 999
const MEAL_PLAN_TITLE_MAX_LENGTH = 255

/** Every dish from [from] to [to], both included. */
async function listMealPlan (from: string, to: string) {
  return apiClient.get<MealPlanEntry[]>(`/meal-plan`, { params: { from, to } })
}

/** What this account planned before as words, newest first. */
async function mealPlanSuggestions (query: string) {
  const params = query.trim() ? { query: query.trim() } : {}
  return apiClient.get<string[]>(`/meal-plan/suggestions`, { params })
}

async function createMealPlanEntry (dto: MealPlanEntrySave) {
  return apiClient.post<MealPlanEntry>(`/meal-plan`, dto)
}

/**
 * Moves a dish, changes how many it is for, or renames one shown as words.
 *
 * Send a title only when it changed: the backend lets go of a recipe that went out of reach
 * when such a dish is renamed, and a save that merely moved it must not cut a link that might
 * come back. Which recipe a dish is cannot be changed here — remove it and plan it again.
 */
async function updateMealPlanEntry (id: number, dto: MealPlanEntryEdit) {
  return apiClient.put<MealPlanEntry>(`/meal-plan/${id}`, dto)
}

async function deleteMealPlanEntry (id: number) {
  return apiClient.delete(`/meal-plan/${id}`)
}

/**
 * The cook's own recipes, the ones they liked and the ones in their cookbooks, matching
 * [query] — the three at once, since the backend ORs those filters. What the plan offers to
 * plan, as in the app: the recipes a cook already has to hand.
 */
async function searchOwnRecipes (userId: number, query: string, size = 20) {
  const params = new URLSearchParams({
    user: String(userId),
    likedBy: String(userId),
    cookbookUser: String(userId),
    sort: query.trim() ? 'BEST_MATCH' : 'DATE_DESCENDING',
  })
  if (query.trim()) params.append('search', query.trim())
  return listRecipes(params.toString(), 0, size)
}

/** The local calendar day of [date], as the API spells it. */
function toIsoDate (date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/** A `yyyy-MM-dd` day, at local midnight; null when it does not read as one. */
function fromIsoDate (value: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value || '')
  if (!match) return null
  return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
}

function addDays (date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days)
}

/** The Monday of the week [date] falls in. */
function weekStartOf (date: Date): Date {
  const sinceMonday = (date.getDay() + 6) % 7
  return addDays(date, -sinceMonday)
}

/**
 * The meal a dish added to a day is offered for: the first of lunch and dinner with nothing in
 * it yet, and dinner when both have something — the app's guess, right most often.
 */
function nextOpenSlot (entries: MealPlanEntry[]): MealSlot {
  const open = (['LUNCH', 'DINNER'] as MealSlot[]).find(slot => !entries.some(e => e.slot === slot))
  return open ?? 'DINNER'
}

export {
  MEAL_SLOTS,
  DEFAULT_MEAL_SERVINGS,
  MEAL_PLAN_MAX_SERVINGS,
  MEAL_PLAN_TITLE_MAX_LENGTH,
  listMealPlan,
  mealPlanSuggestions,
  createMealPlanEntry,
  updateMealPlanEntry,
  deleteMealPlanEntry,
  searchOwnRecipes,
  toIsoDate,
  fromIsoDate,
  addDays,
  weekStartOf,
  nextOpenSlot,
}
