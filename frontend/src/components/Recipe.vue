<script setup lang="ts">
import {getRecipeThumbnailUrl, toViewRecipe} from "@/scripts/common";

defineProps({
  recipe: {
    type: Object,
    required: true,
  },
  /** Title only, no author or likes: the landing page's showcase */
  compact: {
    type: Boolean,
    default: false,
  },
})
</script>

<template>
  <a
    class="recipe-card sticker"
    :href="`/recipe/view?id=${recipe.id}`"
    @click.prevent="toViewRecipe(recipe.id)"
  >
    <div class="recipe-card__photo sk-placeholder">
      <img
        :src="getRecipeThumbnailUrl(recipe.id, recipe.version)"
        :alt="recipe.title"
        loading="lazy"
       
      />
    </div>
    <div class="recipe-card__body" :class="{'recipe-card__body--compact': compact}">
      <div class="recipe-card__title">{{ recipe.title }}</div>
      <div v-if="!compact" class="recipe-card__meta">
        <user-info v-if="recipe.owner" :user="recipe.owner" />
        <span v-if="recipe.likesCount !== undefined" class="sk-pill sk-pill--coral">
          <v-icon>mdi-heart</v-icon>{{ recipe.likesCount }}
        </span>
      </div>
    </div>
  </a>
</template>

<style scoped>
.recipe-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  text-decoration: none;
  min-width: 0;
}

.recipe-card__photo {
  aspect-ratio: 4 / 3;
  border-bottom: var(--line) solid var(--ink);
  overflow: hidden;
}

.recipe-card__photo img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.recipe-card__body {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 10px;
  flex: 1;
  padding: 10px 10px 12px;
}

.recipe-card__body--compact {
  padding: 8px 10px;
}

.recipe-card__title {
  font-size: 14px;
  font-weight: 700;
  line-height: 1.25;
  color: var(--ink);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.recipe-card__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
</style>
