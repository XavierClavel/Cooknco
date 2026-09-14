import apiClient from '@/plugins/axios.js';

export {
  unitSystem,
  loadUnitSystem,
  setUnitSystem,
  convertToPreferred,
  preferredUnitFor,
  METRIC,
  IMPERIAL,
}

const METRIC = 'METRIC'
const IMPERIAL = 'IMPERIAL'

/**
 * The ladder amounts are shown on in this browser - `shared.enums.UnitSystem`.
 *
 * Metric until the account says otherwise, which is what a signed-out visitor stays on and
 * what every recipe in the product was written in, so nothing anybody is already reading
 * changes on deploy. A ref rather than a plain value: every amount on screen is rendered
 * from it, and picking the other one in settings has to redraw them without a reload.
 */
const unitSystem = ref(METRIC)

let settingsPromise = null

/**
 * Reads the account's choice, once per session.
 *
 * Not fired at import like `loadUnits` is: `/user/settings` needs a session, and a page of
 * recipes seen by a signed-out visitor should not open with a 401 in its console. Silent on
 * failure for the same reason the language is - metric is a perfectly good answer, and no
 * amount is worth not drawing over it.
 */
async function loadUnitSystem() {
  if (settingsPromise == null) {
    settingsPromise = apiClient.get(`/user/settings`).then(function (response) {
      if (response.data?.unitSystem) unitSystem.value = response.data.unitSystem
      return unitSystem.value
    }).catch(function () {
      settingsPromise = null
      return unitSystem.value
    })
  }
  return await settingsPromise
}

/** Applies a choice the user just made, so the page it was made from is already right. */
function setUnitSystem(system) {
  if (system) unitSystem.value = system
}

/**
 * The amount and unit to print, for a reader reading in [system].
 *
 * The same rule as `shared.utils.UnitConversion`, off the same catalogue: a unit belonging
 * to no ladder (a countable piece, a spoon) is left alone, and everything else goes through
 * its base unit and lands on the largest display unit of [system] it reaches - which is also
 * what rolls 1500 g up to 1.5 kg, so there is no separate roll-up rule any more.
 *
 * Returns the amount unchanged while the catalogue is still in flight: a number in the unit
 * it was written in is right, just not yet converted.
 */
function convertToPreferred(amount, unit, system, units) {
  const from = units.find(it => it.value == unit)
  if (!from || !from.system || !(amount > 0)) return {amount, unit}
  const ladder = units
    .filter(it => it.type == from.type && it.system == system && it.isDisplayUnit)
    .sort((a, b) => a.factorToBase - b.factorToBase)
  if (!ladder.length) return {amount, unit}
  const base = amount * from.factorToBase
  const target = ladder.filter(it => base >= it.factorToBase).pop() ?? ladder[0]
  return {amount: base / target.factorToBase, unit: target.value}
}

/**
 * The same unit on the reader's ladder, for a picker that has no amount in it yet.
 *
 * Mirrors `shared.utils.UnitConversion.preferredFor`: by rank rather than by magnitude,
 * since there is no number to size yet - the nth display unit of a family on one ladder
 * becomes the nth on the other, so grams meet an imperial cook as ounces and kilograms as
 * pounds. A unit already on their ladder, or on neither, is left exactly as declared.
 */
function preferredUnitFor(unit, system, units) {
  const from = units.find(it => it.value == unit)
  if (!from || !from.system || from.system == system) return unit
  const ladder = (s) => units
    .filter(it => it.type == from.type && it.system == s && it.isDisplayUnit)
    .sort((a, b) => a.factorToBase - b.factorToBase)
  const own = ladder(from.system)
  const target = ladder(system)
  if (!own.length || !target.length) return unit
  // A unit that is not itself a display unit takes the rung it would be shown on, so
  // centilitres rank with millilitres rather than above them
  // own is sorted ascending, so counting the rungs it reaches gives its index
  const rank = Math.max(0, own.filter(it => from.factorToBase >= it.factorToBase).length - 1)
  return (target[rank] ?? target[target.length - 1]).value
}
