<script setup lang="ts">
import {getUserIconUrl, toViewUser} from "@/scripts/common";

defineProps({
  user: {
    type: Object,
    required: true,
  },
})
</script>

<template>
  <a
    class="user-card sticker"
    :href="`/user/view/?user=${user.id}`"
    @click.prevent="toViewUser(user.id)"
  >
    <span class="sk-avatar user-card__avatar">
      <img :src="getUserIconUrl(user.id, user.version)" alt="" loading="lazy" />
    </span>
    <span class="user-card__name">{{ user.username }}</span>
  </a>
</template>

<style scoped>
.user-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 14px 10px 16px;
  min-width: 0;
  text-decoration: none;
}

.user-card:focus-visible {
  outline: 3px solid var(--mustard);
  outline-offset: 3px;
}

.user-card__avatar {
  width: 80px;
  height: 80px;
}

.user-card__name {
  max-width: 100%;
  font-size: 14px;
  font-weight: 700;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
