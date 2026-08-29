<template>

  <v-dialog v-model="recipesDialog" >
    <v-card color="background" class="mx-10">
    <recipes-list :query="`user=${user.id}`"></recipes-list>
    </v-card>
  </v-dialog>

  <v-dialog v-model="likesDialog" width="260px">
    <v-card color="background">
      <recipes-list :query="`likedBy=${user.id}`"></recipes-list>
    </v-card>

  </v-dialog>

  <v-dialog v-model="followersDialog" width="auto">
    <followers-display :is-followers-tab=true></followers-display>
  </v-dialog>

  <v-dialog v-model="followsDialog" width="auto">
    <followers-display :is-followers-tab="false"></followers-display>
  </v-dialog>

  <error :error="errorMessage"></error>

  <v-card
  class="mx-auto pa-5"
  max-width="1000px"
  v-if="!errorMessage"
  >
    <v-container class="d-flex
      flex-wrap justify-center
      flex-sm-nowrap">
      <v-img
        color="surface-variant"
        :src="getUserIconUrl(user.id, user.version)"
        rounded="circle"
        height="200px"
        max-width="200px"
        min-width="200px"
        aspect-ratio="1"
        cover
        style="border: 3px solid #0d1821;"
      ></v-img>
      <v-container class="justify-center align-content-center">
      <v-card-title
        class="mx-auto px-3 mt-n4 text-black text-h2 font-weight-bold
        text-center text-sm-left"
      >{{ user.username }}</v-card-title>
      <v-card-text
        class="mx-auto px-3 mt-n4 text-h6 font-weight-light
        text-center text-sm-left"
      > {{user.bio}} </v-card-text>
      </v-container>
    </v-container>

    <v-container class="d-flex flex-wrap ga-2">
      <interactible-picto-info :value="user.recipesCount" :icon="`${ICON_USER_RECIPES}`" :action="openRecipesWindow"></interactible-picto-info>
      <interactible-picto-info :value="user.likesCount" :icon="`${ICON_USER_LIKES}`" :action="openLikesWindow"></interactible-picto-info>
      <interactible-picto-info :value="user.followsCount" :icon="`${ICON_USER_FOLLOWS}`" :action="openFollowsWindow"></interactible-picto-info>
      <interactible-picto-info :value="user.followersCount" :icon="`${ICON_USER_FOLLOWERS}`" :action="openFollowersWindow"></interactible-picto-info>
  </v-container>

    <v-container>
      <v-row
        class="d-flex align-center justify-center align-content-center mb-2 gx-4"
        dense
      >
        <action-button
          :icon="`${followsUser ? 'mdi-account-minus' : 'mdi-account-plus'}`"
          :text="`${followsUser ? $t('unfollow') : $t('follow')}`"
          :action="followUnfollow"
          v-if="userId != currentUserId"
        ></action-button>
        <action-button
          icon="mdi-pencil"
          :text="`${$t('edit')}`"
          :action="() => toEditUser(userId)"
          v-if="userId == currentUserId"
        ></action-button>
      </v-row>
    </v-container>

    <error :error="errorMessage"></error>


  </v-card>

  <recipes-list :query="`?user=${userId}`"></recipes-list>

</template>

<script lang="ts" setup>
import { useRoute } from 'vue-router';
import {ref} from "vue";
import {getUserIconUrl, toEditUser, toListRecipe} from "@/scripts/common";
import InteractiblePictoInfo from "@/components/InteractiblePictoInfo.vue";
import {follow, isFollowingUser, unfollow} from "@/scripts/follows";
import {useAuthStore} from "@/stores/auth";
import {ICON_USER_FOLLOWERS, ICON_USER_FOLLOWS, ICON_USER_LIKES, ICON_USER_RECIPES} from "@/scripts/icons";
import RecipesList from "@/components/RecipesList.vue";

const route = useRoute();

// Keeps this dynamic route from swallowing a sibling static path
// (/recipe/edit, /user/settings, ...) and makes /recipe/banana a clean 404
// rather than a 400 from the backend.
definePageMeta({ validate: (r) => /^\d+$/.test(String(r.params.id)) })
const userId = route.params.id
const errorMessage = ref(null)
const authStore = useAuthStore();
const currentUserId = computed(() => authStore.id)

const followsUser = ref(null)
const followersDialog = ref(false)
const followsDialog = ref(false)
const recipesDialog = ref(false)
const likesDialog = ref(false)

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

// Profiles carry no visibility filter server-side (UserService.getUser returns
// the full UserInfo to anyone), so there is no restricted branch here.
const { data: user, error, refresh: updateUser } = await useAsyncData(
  () => `user:${userId}`,
  () => $fetch<any>(`${apiBase()}/user/${userId}`),
  { default: () => ({}) },
)

if (error.value) {
  throw createError({
    statusCode: (error.value as any).statusCode === 404 ? 404 : 500,
    statusMessage: 'User not found',
    fatal: true,
  })
}

useShareMeta(() => ({
  kind: 'user',
  title: user.value?.username,
  description: user.value?.bio
    || (user.value?.username
      ? `${user.value.username} on Cook&Co — ${user.value.recipesCount ?? 0} recipes`
      : null),
  imageId: user.value?.id,
  imageVersion: user.value?.version,
}))

// Follow state is per-visitor, so it is never part of the server render.
onMounted(() => {
  isFollowingUser(userId).then((response) => {
    followsUser.value = response.data
  }).catch(function (error) {
    errorMessage.value = error.response.data
  })
})

const redirectRecipesOwned = () => {
  toListRecipe(`?owner=${userId}`)
}

const redirectRecipesLiked = () => {
  toListRecipe(`?likedBy=${userId}`)
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


</script>
