<template>

  <v-dialog v-model="recipesDialog" max-width="1100">
    <div class="sticker sk-pad profile-dialog">
      <recipes-list :query="`user=${user.id}`"></recipes-list>
    </div>
  </v-dialog>

  <v-dialog v-model="likesDialog" max-width="1100">
    <div class="sticker sk-pad profile-dialog">
      <recipes-list :query="`likedBy=${user.id}`"></recipes-list>
    </div>
  </v-dialog>

  <v-dialog v-model="followersDialog" width="auto">
    <followers-display :is-followers-tab=true></followers-display>
  </v-dialog>

  <v-dialog v-model="followsDialog" width="auto">
    <followers-display :is-followers-tab="false"></followers-display>
  </v-dialog>

  <div class="sk-page">
    <error :error="errorMessage"></error>

    <section v-if="!errorMessage" class="profile sticker sk-pad-lg sk-narrow">
      <div class="profile__top">
        <span class="sk-avatar profile__avatar">
          <img v-if="user.id" :src="getUserIconUrl(user.id, user.version)" :alt="user.username" />
        </span>

        <div class="profile__text">
          <h1 class="sk-title">{{ user.username }}</h1>
          <p v-if="user.bio" class="profile__bio">{{ user.bio }}</p>

          <div class="profile__stats">
            <button type="button" class="profile__stat" @click="openRecipesWindow">
              <span class="sk-eyebrow">{{ $t("recipes") }}</span>
              <span class="profile__stat-value">{{ user.recipesCount ?? "–" }}</span>
            </button>
            <button type="button" class="profile__stat" @click="openLikesWindow">
              <span class="sk-eyebrow">{{ $t("likes") }}</span>
              <span class="profile__stat-value">{{ user.likesCount ?? "–" }}</span>
            </button>
            <button type="button" class="profile__stat" @click="openFollowsWindow">
              <span class="sk-eyebrow">{{ $t("follows") }}</span>
              <span class="profile__stat-value">{{ user.followsCount ?? "–" }}</span>
            </button>
            <button type="button" class="profile__stat" @click="openFollowersWindow">
              <span class="sk-eyebrow">{{ $t("followers") }}</span>
              <span class="profile__stat-value">{{ user.followersCount ?? "–" }}</span>
            </button>
          </div>
        </div>
      </div>

      <div class="profile__actions">
        <button
          v-if="userId != currentUserId"
          type="button"
          class="sk-btn"
          :class="{'sk-btn--coral': !followsUser}"
          :disabled="following"
          @click="onFollowUnfollow"
        >
          <v-progress-circular v-if="following" indeterminate size="18" width="2"></v-progress-circular>
          <v-icon v-else>{{ followsUser ? 'mdi-account-minus' : 'mdi-account-plus' }}</v-icon>
          {{ followsUser ? $t('unfollow') : $t('follow') }}
        </button>
        <template v-if="userId == currentUserId">
          <button type="button" class="sk-btn" @click="toEditUser(userId)">
            <v-icon>mdi-pencil-outline</v-icon>
            {{ $t("edit") }}
          </button>
          <button type="button" class="sk-btn" @click="toSettings()">
            <v-icon>mdi-cog-outline</v-icon>
            {{ $t("settings") }}
          </button>
        </template>
        <report-dialog
          v-if="userId != currentUserId"
          target-type="USER"
          :target-id="Number(userId)"
        >
          <template #activator="{props}">
            <button type="button" class="sk-btn" v-bind="props">
              <v-icon>{{ ICON_REPORT }}</v-icon>
              {{ $t("report") }}
            </button>
          </template>
        </report-dialog>
      </div>
    </section>

    <recipes-list :query="`?user=${userId}`"></recipes-list>
  </div>

</template>

<script lang="ts" setup>
import { useRoute } from 'vue-router';
import {ref} from "vue";
import {getUserIconUrl, toEditUser, toListRecipe, toSettings} from "@/scripts/common";
import {getUser} from "@/scripts/users";
import {follow, isFollowingUser, unfollow} from "@/scripts/follows";
import {useAuthStore} from "@/stores/auth";
import {ICON_REPORT} from "@/scripts/icons";
import RecipesList from "@/components/RecipesList.vue";

const route = useRoute();
const userId = route.query.user
const errorMessage = ref(null)
const authStore = useAuthStore();
const currentUserId = computed(() => authStore.id)

const user = ref<object>({})
const followsUser = ref(null)
const followersDialog = ref(false)
const followsDialog = ref(false)
const recipesDialog = ref(false)
const likesDialog = ref(false)
const following = ref(false)

const openRecipesWindow = () => {
  recipesDialog.value = true
}

const openLikesWindow = () => {
  likesDialog.value = true
}

const openFollowersWindow = () => {
  followersDialog.value = true
}

const openFollowsWindow = () => {
  followsDialog.value = true
}

isFollowingUser(userId).then (
  function (response) {
    console.log(response)
    followsUser.value = response.data
  }).catch(function (error) {
    errorMessage.value = error.response.data
})

const redirectRecipesOwned = () => {
  toListRecipe(`?owner=${userId}`)
}

const redirectRecipesLiked = () => {
  toListRecipe(`?likedBy=${userId}`)
}

async function onFollowUnfollow() {
  if (following.value) return
  following.value = true
  try {
    await followUnfollow()
  } finally {
    following.value = false
  }
}

async function followUnfollow() {
  if (followsUser.value) {
    await unfollow(userId).catch(function (error) {
      errorMessage.value = error.response.data
    })
  }
  else {
    await follow(userId).catch(function (error) {
      errorMessage.value = error.response.data
    })
  }
  followsUser.value = !followsUser.value
  updateUser()
}


const updateUser = () => {
  getUser(userId).then (
    function (response) {
      console.log(response.data)
      user.value = response.data
    }).catch(function (error) {
    errorMessage.value = error.response.data
  });
}

updateUser()
</script>

<style scoped>
.profile {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.profile__top {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.profile__avatar {
  width: 168px;
  height: 168px;
}

.profile__text {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  min-width: 0;
  padding-top: 4px;
}

.profile__text .sk-title {
  overflow-wrap: anywhere;
}

.profile__bio {
  margin: 0;
  color: var(--muted);
  white-space: pre-line;
  overflow-wrap: anywhere;
}

.profile__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
}

.profile__stat {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 10px 12px;
  border: 1.5px solid var(--ink);
  border-radius: var(--radius-sm);
  background: var(--white);
  color: var(--ink);
  font-family: var(--font);
  text-align: left;
  cursor: pointer;
  min-width: 0;
}

.profile__stat:hover {
  background: var(--cream);
}

.profile__stat:focus-visible {
  outline: 3px solid var(--mustard);
  outline-offset: 2px;
}

.profile__stat .sk-eyebrow {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile__stat-value {
  font-size: 18px;
  font-weight: 700;
}

.profile__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
}

.profile-dialog {
  max-height: 85vh;
  overflow-y: auto;
}

@media (max-width: 700px) {
  .profile__top {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  .profile__avatar {
    width: 120px;
    height: 120px;
  }

  .profile__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
