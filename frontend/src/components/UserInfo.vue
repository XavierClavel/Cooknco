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
  <!-- A user as a small pill: their picture in a cream circle, then their name -->
  <a
    class="user-pill sk-pill"
    :href="`/user/view/?user=${user.id}`"
    @click.prevent.stop="toViewUser(user.id)"
  >
    <span class="user-pill__avatar">
      <img :src="getUserIconUrl(user.id, user.version)" alt="" loading="lazy" />
    </span>
    <span class="user-pill__name">{{ user.username }}</span>
  </a>
</template>

<style scoped>
.user-pill {
  padding-left: 2px;
  text-decoration: none;
  min-width: 0;
  max-width: 100%;
}

.user-pill:hover {
  background: var(--cream);
}

.user-pill__avatar {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--cream);
  overflow: hidden;
}

.user-pill__avatar img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-pill__name {
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
