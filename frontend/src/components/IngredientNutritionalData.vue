<script setup lang="ts">
  const props = defineProps({
    ingredient: {
      type: Object,
      required: true,
    },
  })

  const format = v => v.toFixed(2).replace(/[.,]00$/, '')

  const getValueAmount = v => v == null ? v : format(v * props.ingredient.gramsPerUnit / 100)

  const getValueWeight = v => v

  const getValueVolume = v => v == null ? v : format(v * props.ingredient.gramsPerMilliliter)

  // The rows, in the order the sheet reads them; each key is both the field and its label
  const nutrients = [
    'calories',
    'carbohydrates',
    'sugars',
    'fibers',
    'cholesterol',
    'unsaturatedFat',
    'saturatedFat',
    'proteins',
    'sodium',
  ]
</script>

<template>
  <div class="sk-table-scroll">
    <table class="sk-table nutrition-table">
      <thead>
        <tr>
          <th scope="col">{{ $t("nutrition_value") }}</th>
          <th v-if="ingredient.gramsPerUnit != null" scope="col">{{ $t("per_unit") }}</th>
          <th v-if="ingredient.measurableByWeight" scope="col">{{ $t("per_weight") }}</th>
          <th v-if="ingredient.gramsPerMilliliter != null" scope="col">{{ $t("per_volume") }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="nutrient in nutrients" :key="nutrient">
          <th scope="row">{{ $t(nutrient) }}</th>
          <td v-if="ingredient.gramsPerUnit != null">{{ getValueAmount(ingredient[nutrient]) }}</td>
          <td v-if="ingredient.measurableByWeight">{{ getValueWeight(ingredient[nutrient]) }}</td>
          <td v-if="ingredient.gramsPerMilliliter != null">{{ getValueVolume(ingredient[nutrient]) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.nutrition-table tbody th {
  padding: 12px 0;
  border-bottom: 1px solid var(--rule);
  color: var(--ink);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: none;
  text-align: left;
}

.nutrition-table thead th:first-child {
  padding-left: 0;
}

.nutrition-table tr:last-child th {
  border-bottom: 0;
}

.nutrition-table td {
  font-size: 15px;
  font-weight: 700;
}
</style>
