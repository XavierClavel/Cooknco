import apiClient from '@/plugins/axios.js';
import {ICON_AMOUNT, ICON_NONE, ICON_VOLUME, ICON_WEIGHT} from "@/scripts/icons";

export {
  unitOptions,
  loadUnits,
  getUnitOptions,
  findUnitOption,
}

const unitOptions = ref([])

let unitsPromise = null

const getMeasurementTypeIcon = (type) => {
  switch (type) {
    case "AMOUNT":
      return ICON_AMOUNT
    case "WEIGHT":
      return ICON_WEIGHT
    case "VOLUME":
      return ICON_VOLUME
    default:
      return ICON_NONE
  }
}

async function loadUnits() {
  if (unitsPromise == null) {
    unitsPromise = apiClient.get(`/unit`).then(function (response) {
      unitOptions.value = response.data.map(unit => ({
        // The enum name, read as words: FLUID_OUNCE is "fluid ounce" in the picker, not
        // the constant with its underscore still in it
        label: unit.name.toLowerCase().replaceAll('_', ' '),
        value: unit.name,
        icon: getMeasurementTypeIcon(unit.type),
        type: unit.type,
        factorToBase: unit.factorToBase,
        // Which ladder the unit sits on, and whether a conversion may land on it. Read
        // rather than restated here, so adding a unit is a change to `AmountUnit` alone -
        // see `convertToPreferred` in @/scripts/unitSystem
        system: unit.system,
        isDisplayUnit: unit.isDisplayUnit,
      }))
      return unitOptions.value
    }).catch(function (error) {
      unitsPromise = null
      console.log(error);
      return unitOptions.value
    })
  }
  return await unitsPromise
}

function getUnitOptions(allowedTypes) {
  if (!allowedTypes) return unitOptions.value
  return unitOptions.value.filter(it => it.type == "NONE" || allowedTypes.includes(it.type))
}

function findUnitOption(unit) {
  return unitOptions.value.find(it => it.value == unit)
}

loadUnits()
