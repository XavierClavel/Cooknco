<script setup lang="ts">
import {getCookbookIconUrl, getUserIconUrl, toViewCookbook} from "@/scripts/common";

const props = defineProps({
  cookbook: {
    type: Object,
    required: true,
  },
  /**
   * `row`: the cover beside the title, with the members' faces — the cookbooks page.
   * `card`: the cover on top, like a recipe card — search results.
   */
  variant: {
    type: String,
    default: "row",
  },
})

/** How many faces the row shows before the rest become a count. */
const FACES_SHOWN = 4

const members = computed(() => (props.cookbook.members ?? []).slice(0, FACES_SHOWN))
const usersCount = computed(() => props.cookbook.usersCount ?? props.cookbook.members?.length ?? 0)
const hiddenMembers = computed(() => Math.max(0, usersCount.value - members.value.length))
</script>

<template>
  <a
    class="cookbook sticker"
    :class="`cookbook--${variant}`"
    :href="`/cookbook/view?cookbook=${cookbook.id}`"
    @click.prevent="toViewCookbook(cookbook.id)"
  >
    <div class="cookbook__cover sk-placeholder">
      <img :src="getCookbookIconUrl(cookbook.id, cookbook.version)" alt="" loading="lazy" />
    </div>

    <div class="cookbook__body">
      <div class="cookbook__title">{{ cookbook.title }}</div>
      <div class="cookbook__meta">
        <template v-if="cookbook.recipesCount != null">{{ $t("cookbook_recipes_count", cookbook.recipesCount) }} · </template>{{ $t("cookbook_members_count", usersCount) }}
      </div>

      <div v-if="variant === 'row' && members.length" class="cookbook__members">
        <span class="cookbook__faces">
          <span
            v-for="member in members"
            :key="member.id"
            class="sk-avatar cookbook__face"
            :title="member.username"
          >
            <img :src="getUserIconUrl(member.id, member.version)" :alt="member.username" loading="lazy" />
          </span>
          <span v-if="hiddenMembers" class="sk-avatar cookbook__face cookbook__face--more">+{{ hiddenMembers }}</span>
        </span>
        <span v-if="usersCount > 1" class="sk-pill sk-pill--sky">{{ $t("cookbook_shared") }}</span>
      </div>
    </div>
  </a>
</template>

<style scoped>
.cookbook {
  display: flex;
  text-decoration: none;
  overflow: hidden;
  min-width: 0;
}

.cookbook:focus-visible {
  outline: 3px solid var(--mustard);
  outline-offset: 3px;
}

.cookbook__cover {
  overflow: hidden;
  flex-shrink: 0;
}

.cookbook__cover img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cookbook__body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.cookbook__title {
  font-size: 16px;
  font-weight: 700;
  line-height: 1.25;
  color: var(--ink);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.cookbook__meta {
  font-size: 12px;
  color: var(--muted);
}

/* ---- Row: the cover beside the text ---- */

.cookbook--row {
  align-items: center;
  gap: 14px;
  padding: 10px;
}

.cookbook--row .cookbook__cover {
  width: 76px;
  height: 76px;
  border: var(--line) solid var(--ink);
  border-radius: var(--radius-sm);
}

.cookbook--row .cookbook__body {
  flex: 1;
  align-self: stretch;
  padding-top: 4px;
}

.cookbook__members {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: auto;
}

.cookbook__faces {
  display: inline-flex;
  padding-left: 5px;
}

.cookbook__face {
  width: 22px;
  height: 22px;
  margin-left: -5px;
  border-width: 1.5px;
  font-size: 9px;
}

/* ---- Card: the cover on top ---- */

.cookbook--card {
  flex-direction: column;
}

.cookbook--card .cookbook__cover {
  aspect-ratio: 1;
  border-bottom: var(--line) solid var(--ink);
}

.cookbook--card .cookbook__title {
  font-size: 14px;
}

.cookbook--card .cookbook__body {
  gap: 4px;
  padding: 10px 10px 12px;
}
</style>
