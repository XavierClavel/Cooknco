<script setup lang="ts">
import {useI18n} from "vue-i18n";

const {t} = useI18n()

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



</script>

<template>
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
    <tr>
      <th class="text-left">
        {{$t('calories')}}
      </th>
      <th class="text-center" v-if="ingredient.gramsPerUnit != null">
        {{ getValueAmount(ingredient.calories)}}
      </th>
      <th class="text-center" v-if="ingredient.measurableByWeight">
        {{ getValueWeight(ingredient.calories)}}
      </th>
      <th class="text-center" v-if="ingredient.gramsPerMilliliter != null">
        {{ getValueVolume(ingredient.calories)}}
      </th>
    </tr>

    <tr>
      <th class="text-left">
        {{$t('carbohydrates')}}
      </th>
      <th class="text-center" v-if="ingredient.gramsPerUnit != null">
        {{ getValueAmount(ingredient.carbohydrates)}}
      </th>
      <th class="text-center" v-if="ingredient.measurableByWeight">
        {{ getValueWeight(ingredient.carbohydrates)}}
      </th>
      <th class="text-center" v-if="ingredient.gramsPerMilliliter != null">
        {{ getValueVolume(ingredient.carbohydrates)}}
      </th>
    </tr>

    <tr>
      <th class="text-left">
        {{$t('sugars')}}
      </th>
      <th class="text-center" v-if="ingredient.gramsPerUnit != null">
        {{ getValueAmount(ingredient.sugars)}}
      </th>
      <th class="text-center" v-if="ingredient.measurableByWeight">
        {{ getValueWeight(ingredient.sugars)}}
      </th>
      <th class="text-center" v-if="ingredient.gramsPerMilliliter != null">
        {{ getValueVolume(ingredient.sugars)}}
      </th>
    </tr>

    <tr>
      <th class="text-left">
        {{$t('fibers')}}
      </th>
      <th class="text-center" v-if="ingredient.gramsPerUnit != null">
        {{ getValueAmount(ingredient.fibers)}}
      </th>
      <th class="text-center" v-if="ingredient.measurableByWeight">
        {{ getValueWeight(ingredient.fibers)}}
      </th>
      <th class="text-center" v-if="ingredient.gramsPerMilliliter != null">
        {{ getValueVolume(ingredient.fibers)}}
      </th>
    </tr>

    <tr>
      <th class="text-left">
        {{$t('cholesterol')}}
      </th>
      <th class="text-center" v-if="ingredient.gramsPerUnit != null">
        {{ getValueAmount(ingredient.cholesterol)}}
      </th>
      <th class="text-center" v-if="ingredient.measurableByWeight">
        {{ getValueWeight(ingredient.cholesterol)}}
      </th>
      <th class="text-center" v-if="ingredient.gramsPerMilliliter != null">
        {{ getValueVolume(ingredient.cholesterol)}}
      </th>
    </tr>

    <tr>
      <th class="text-left">
        {{$t('unsaturatedFat')}}
      </th>
      <th class="text-center" v-if="ingredient.gramsPerUnit != null">
        {{ getValueAmount(ingredient.unsaturatedFat)}}
      </th>
      <th class="text-center" v-if="ingredient.measurableByWeight">
        {{ getValueWeight(ingredient.unsaturatedFat)}}
      </th>
      <th class="text-center" v-if="ingredient.gramsPerMilliliter != null">
        {{ getValueVolume(ingredient.unsaturatedFat)}}
      </th>
    </tr>

    <tr>
      <th class="text-left">
        {{$t('saturatedFat')}}
      </th>
      <th class="text-center" v-if="ingredient.gramsPerUnit != null">
        {{ getValueAmount(ingredient.saturatedFat)}}
      </th>
      <th class="text-center" v-if="ingredient.measurableByWeight">
        {{ getValueWeight(ingredient.saturatedFat)}}
      </th>
      <th class="text-center" v-if="ingredient.gramsPerMilliliter != null">
        {{ getValueVolume(ingredient.saturatedFat)}}
      </th>
    </tr>

    <tr>
      <th class="text-left">
        {{$t('proteins')}}
      </th>
      <th class="text-center" v-if="ingredient.gramsPerUnit != null">
        {{ getValueAmount(ingredient.proteins)}}
      </th>
      <th class="text-center" v-if="ingredient.measurableByWeight">
        {{ getValueWeight(ingredient.proteins)}}
      </th>
      <th class="text-center" v-if="ingredient.gramsPerMilliliter != null">
        {{ getValueVolume(ingredient.proteins)}}
      </th>
    </tr>

    <tr>
      <th class="text-left">
        {{$t('sodium')}}
      </th>
      <th class="text-center" v-if="ingredient.gramsPerUnit != null">
        {{ getValueAmount(ingredient.sodium)}}
      </th>
      <th class="text-center" v-if="ingredient.measurableByWeight">
        {{ getValueWeight(ingredient.sodium)}}
      </th>
      <th class="text-center" v-if="ingredient.gramsPerMilliliter != null">
        {{ getValueVolume(ingredient.sodium)}}
      </th>
    </tr>

    </tbody>
  </v-table>
</template>

<style scoped>

</style>
