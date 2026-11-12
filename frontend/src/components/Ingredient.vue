<script setup lang="ts">
import {getIngredientImageUrl, toViewIngredient} from "@/scripts/common";
import {getLocale} from "@/scripts/localization";

const props = defineProps({
  ingredient: {
    type: Object,
    required: true,
  },
})

const name = computed(() => props.ingredient.name?.[getLocale().toUpperCase()] ?? "")
const image = computed(() => getIngredientImageUrl(props.ingredient.type))
</script>

<template>
  <a
    class="ingredient-card sticker"
    :href="`/ingredient/view?ingredient=${ingredient.id}`"
    @click.prevent="toViewIngredient(ingredient.id)"
  >
    <span class="sk-tile ingredient-card__tile">
      <img v-if="image" :src="image" alt="" loading="lazy" />
      <template v-else>{{ name.charAt(0).toUpperCase() }}</template>
    </span>
    <span class="ingredient-card__name">{{ name }}</span>
  </a>
</template>

<style scoped>
.ingredient-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 14px 10px 16px;
  min-width: 0;
  text-decoration: none;
}

.ingredient-card:focus-visible {
  outline: 3px solid var(--mustard);
  outline-offset: 3px;
}

.ingredient-card__tile {
  width: 80px;
  height: 80px;
  border-width: var(--line);
  border-radius: var(--radius);
  font-size: 32px;
}

.ingredient-card__tile img {
  width: 60%;
  height: 60%;
  object-fit: contain;
}

.ingredient-card__name {
  max-width: 100%;
  font-size: 14px;
  font-weight: 700;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
