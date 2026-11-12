<template>
  <div class="sk-page">
    <section class="sticker sk-pad-lg ing-head">
      <div class="ing-head__tile">
        <img alt="" :src="getIngredientImageUrl(ingredient.type)">
      </div>
      <div class="ing-head__text">
        <h1 class="sk-title ing-head__name">{{ ingredient.name?.[getLocale().toUpperCase()] }}</h1>
        <div v-if="recipesCount !== null" class="ing-head__count">
          <span class="sk-eyebrow">{{ $t("recipes") }}</span>
          <span class="ing-head__number">{{ recipesCount }}</span>
        </div>
      </div>
    </section>

    <section :aria-label="$t('nutrition_value')" class="sticker sk-pad-lg">
      <IngredientNutritionalData :ingredient="ingredient" />
    </section>

    <recipes-list :query="`?ingredient=${ingredientId}`" />
  </div>
</template>

<script lang="ts" setup>
  import { useRoute } from 'vue-router'
  import { ref } from 'vue'
  import { getIngredient, getIngredientRecipesCount } from '@/scripts/ingredients'
  import { getLocale } from '@/scripts/localization'
  import IngredientNutritionalData from '@/components/IngredientNutritionalData.vue'
  import { getIngredientImageUrl } from '@/scripts/common'

  const route = useRoute()
  const ingredientId = ref(route.query.ingredient)
  const ingredient = ref<object>({
    name: '',
  })
  const recipesCount = ref(null)

  getIngredient(ingredientId.value).then(response => {
    ingredient.value = response.data
  })

  getIngredientRecipesCount(ingredientId.value).then(response => {
    recipesCount.value = response.data
  })

</script>

<style scoped>
.ing-head {
  display: flex;
  align-items: center;
  gap: 28px;
}

.ing-head__tile {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 160px;
  height: 160px;
  border: var(--line) solid var(--ink);
  border-radius: var(--radius);
  background: var(--mint);
}

.ing-head__tile img {
  width: 62%;
  height: 62%;
  object-fit: contain;
}

.ing-head__text {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.ing-head__name {
  font-size: clamp(32px, 5vw, 48px);
  overflow-wrap: anywhere;
}

.ing-head__count {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ing-head__number {
  font-size: 24px;
  font-weight: 700;
}

@media (max-width: 600px) {
  .ing-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .ing-head__tile {
    width: 112px;
    height: 112px;
  }
}
</style>
