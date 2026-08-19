/**
 * Ingredient shapes the catalogue editor works with.
 *
 * Which family a unit belongs to and how it converts comes from the server (`/unit`); the list
 * below is only the fallback the form uses until that answers, plus the short labels a dense
 * table wants. What genuinely lives here is the part the server has no opinion on: which fields
 * the form shows, and what a new ingredient of a given type starts out with.
 */

export const INGREDIENT_TYPES = [
  'VEGETABLE', 'FRUIT', 'GRAIN', 'NUT', 'DAIRY', 'FISH', 'MEAT',
  'CONDIMENT', 'OIL', 'BAKERY', 'BEVERAGE_INGREDIENT', 'ALCOHOL', 'MISCELLANEOUS',
]

/** Per-100g figures, in the order the form lays them out. */
export const NUTRITION_FIELDS = [
  {key: 'calories', step: 1},
  {key: 'proteins', step: 0.1},
  {key: 'carbohydrates', step: 0.1},
  {key: 'sugars', step: 0.1},
  {key: 'fibers', step: 0.1},
  {key: 'saturatedFat', step: 0.1},
  {key: 'unsaturatedFat', step: 0.1},
  {key: 'cholesterol', step: 0.1},
  {key: 'sodium', step: 0.001},
]

const UNITS = [
  {name: 'NONE', type: 'NONE', label: '—'},
  {name: 'UNIT', type: 'AMOUNT', label: 'unit'},
  {name: 'GRAM', type: 'WEIGHT', label: 'g'},
  {name: 'KILOGRAM', type: 'WEIGHT', label: 'kg'},
  {name: 'POUND', type: 'WEIGHT', label: 'lb'},
  {name: 'MILLILITERS', type: 'VOLUME', label: 'mL'},
  {name: 'CENTILITER', type: 'VOLUME', label: 'cL'},
  {name: 'LITER', type: 'VOLUME', label: 'L'},
  {name: 'TEASPOON', type: 'VOLUME', label: 'tsp'},
  {name: 'TABLESPOON', type: 'VOLUME', label: 'tbsp'},
  {name: 'CUP', type: 'VOLUME', label: 'cup'},
]

export const unitLabel = (unit: string) => UNITS.find(u => u.name === unit)?.label ?? unit

/** Reads an ingredient row's quantity, scaling g to kg and mL to L for display only. */
export function formatAmount(amount: number | null | undefined, unit: string): string {
  if (!amount) return ''
  let value = amount
  let scaled = unit
  if (unit === 'GRAM' && amount >= 1000) { value = amount / 1000; scaled = 'KILOGRAM' }
  else if (unit === 'MILLILITERS' && amount >= 1000) { value = amount / 1000; scaled = 'LITER' }
  const rounded = value.toFixed(2).replace(/[.,]?0+$/, '')
  return scaled === 'NONE' ? rounded : `${rounded} ${unitLabel(scaled)}`
}

/** Stands in for the catalogue while `/unit` is in flight, and if it fails. */
export const FALLBACK_UNITS = UNITS.map(({name, type}) => ({name, type}))

export type IngredientForm = Record<string, any> & {
  id: number | null
  name: {EN: string; FR: string}
  type: string
  gramsPerUnit: number | null
  gramsPerMilliliter: number | null
  measurableByWeight: boolean
  defaultUnit: string | null
}

/** A cleared input means "unknown", which is what a null conversion says on the wire. */
const numberOrNull = (value: unknown) =>
  value === '' || value === null || value === undefined || !Number.isFinite(Number(value)) ? null : Number(value)

/**
 * A conversion declares a capability only when it is positive, which is also where the server
 * draws the line — so the unit picker never offers a family the server would then reject.
 */
const declared = (value: unknown) => {
  const n = numberOrNull(value)
  return n !== null && n > 0
}

export function emptyForm(): IngredientForm {
  const form: IngredientForm = {
    id: null,
    name: {EN: '', FR: ''},
    type: 'MISCELLANEOUS',
    gramsPerUnit: null,
    gramsPerMilliliter: null,
    measurableByWeight: true,
    defaultUnit: 'GRAM',
  }
  NUTRITION_FIELDS.forEach(f => { form[f.key] = 0 })
  return form
}

/** An ingredient as the API returned it, flattened into what the form binds to. */
export function formOf(ingredient: Record<string, any>): IngredientForm {
  const form = emptyForm()
  form.id = ingredient.id
  form.name = {EN: ingredient.name?.EN ?? '', FR: ingredient.name?.FR ?? ''}
  form.type = ingredient.type
  form.gramsPerUnit = ingredient.gramsPerUnit ?? null
  form.gramsPerMilliliter = ingredient.gramsPerMilliliter ?? null
  form.measurableByWeight = ingredient.measurableByWeight ?? false
  form.defaultUnit = ingredient.defaultUnit ?? null
  NUTRITION_FIELDS.forEach(f => { form[f.key] = ingredient[f.key] ?? 0 })
  return form
}

export function payloadOf(form: IngredientForm): Record<string, unknown> {
  const body: Record<string, unknown> = {
    name: form.name,
    type: form.type,
    gramsPerUnit: numberOrNull(form.gramsPerUnit),
    gramsPerMilliliter: numberOrNull(form.gramsPerMilliliter),
    measurableByWeight: form.measurableByWeight,
    defaultUnit: form.defaultUnit || null,
  }
  NUTRITION_FIELDS.forEach(f => { body[f.key] = Number(form[f.key]) || 0 })
  return body
}

/**
 * Which unit families the ingredient supports. Mirrors the server's rule — a conversion being
 * present *is* the capability — so the picker narrows as the operator fills the form in, before
 * anything is saved.
 */
export function allowedTypesOf(form: IngredientForm): string[] {
  const types = ['NONE']
  if (declared(form.gramsPerUnit)) types.push('AMOUNT')
  if (form.measurableByWeight) types.push('WEIGHT')
  if (declared(form.gramsPerMilliliter)) types.push('VOLUME')
  return types
}

/**
 * What a new ingredient of a given type starts out with.
 *
 * Only conversions that hold for an entire type are filled in: water-like liquids at 1 g/mL and
 * oil at 0.92. Per-piece weight is always left empty — a guess there would recreate exactly the
 * fabricated per-unit figures nullable conversions exist to remove, and it is the one number only
 * the person entering the ingredient knows.
 */
const weightOnly = {gramsPerUnit: null, gramsPerMilliliter: null, measurableByWeight: true, defaultUnit: 'GRAM'}
const waterLike = {...weightOnly, gramsPerMilliliter: 1.0, defaultUnit: 'MILLILITERS'}
const oilLike = {...weightOnly, gramsPerMilliliter: 0.92, defaultUnit: 'TABLESPOON'}

const TYPE_DEFAULTS: Record<string, typeof weightOnly> = {
  BEVERAGE_INGREDIENT: waterLike,
  ALCOHOL: waterLike,
  OIL: oilLike,
}

export const typeDefaults = (type: string) => ({...(TYPE_DEFAULTS[type] ?? weightOnly)})
