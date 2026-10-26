<script setup lang="ts">
import {computed} from "vue";
import {useI18n} from "vue-i18n";

const {t} = useI18n()

const props = defineProps({
  ingredient: {
    type: Object,
    required: true,
  },
})

/** Per 100 g, in the order the table lays them out, in the unit each is stated in. */
const FIELDS = [
  {key: 'calories', unit: 'kcal'},
  {key: 'carbohydrates', unit: 'g'},
  {key: 'sugars', unit: 'g'},
  {key: 'fibers', unit: 'g'},
  {key: 'cholesterol', unit: 'mg'},
  {key: 'unsaturatedFat', unit: 'g'},
  {key: 'saturatedFat', unit: 'g'},
  {key: 'proteins', unit: 'g'},
  {key: 'salt', unit: 'g'},
]

// Unknown is not zero: Ciqual leaves some figures unmeasured, and printing 0 would say "none"
const UNKNOWN = '—'

const format = v => v.toFixed(2).replace(/[.,]00$/, '')

const getValueAmount = v => v == null ? UNKNOWN : format(v * props.ingredient.gramsPerUnit / 100)

const getValueWeight = v => v == null ? UNKNOWN : v

const getValueVolume = v => v == null ? UNKNOWN : format(v * props.ingredient.gramsPerMilliliter)

const hasUnknown = computed(() => props.ingredient.id != null && FIELDS.some(f => props.ingredient[f.key] == null))

</script>

<template>
  <div>
  <v-table
    style="width: 1000px"
    fixed-header
    class="rounded-0"
    density="compact"
  >
    <thead>
    <tr>
      <th class="text-left">Value</th>
      <th v-if="ingredient.gramsPerUnit != null" class="text-center">{{ $t("per_unit") }}</th>
      <th v-if="ingredient.measurableByWeight" class="text-center">{{ $t("per_weight")}}</th>
      <th v-if="ingredient.gramsPerMilliliter != null" class="text-center">{{ $t("per_volume")}}</th>
    </tr>
    </thead>
    <tbody>
    <tr v-for="f in FIELDS" :key="f.key">
      <th class="text-left">
        {{$t(f.key)}} ({{ f.unit }})
      </th>
      <th class="text-center" v-if="ingredient.gramsPerUnit != null">
        {{ getValueAmount(ingredient[f.key])}}
      </th>
      <th class="text-center" v-if="ingredient.measurableByWeight">
        {{ getValueWeight(ingredient[f.key])}}
      </th>
      <th class="text-center" v-if="ingredient.gramsPerMilliliter != null">
        {{ getValueVolume(ingredient[f.key])}}
      </th>
    </tr>

    </tbody>
  </v-table>
  <p v-if="hasUnknown" class="text-caption mt-2">{{ $t('nutrition_unknown') }}</p>
  <!-- The citation is a condition of the licence: every reuse of Ciqual names Anses and the edition -->
  <p v-if="ingredient.ciqual" class="text-caption mt-2">
    {{ $t('nutrition_source') }}
    <a href="https://ciqual.anses.fr" target="_blank" rel="noopener">
      Anses. {{ ingredient.ciqual.edition }}. Table de composition nutritionnelle des aliments Ciqual
    </a>
  </p>
  </div>
</template>

<style scoped>

</style>
