<script setup lang="ts">
import {getUserIconUrl, toViewUser} from "../scripts/common";
import {ICON_ACCEPT, ICON_DENY, ICON_REMOVE} from "../scripts/icons";
import {
  acceptFollowRequest,
  declineFollowRequest,
  getFollowers,
  getFollows,
  removeFollower,
  unfollow
} from "@/scripts/follows";
import {useRoute} from "vue-router";
import {useAuthStore} from "@/stores/auth";
import {useI18n} from "vue-i18n";
import {usePollingStore} from "@/stores/pollingStore";
const { t } = useI18n();

const route = useRoute();
const userId = route.query.user
const authStore = useAuthStore()
const currentUser = computed(() => authStore.id)

const forMyself = ref(currentUser.value == userId)

const props = defineProps({
  isFollowersTab: {
    type: Boolean,
    required: true,
  },
})

const usersAccepted = ref(null)
const usersPending = ref(null)
const pollingStore = usePollingStore()

const refreshData = () => {
  console.log("refreshing data")
  console.log(userId)
  console.log(currentUser)
  console.log(forMyself)
  if (props.isFollowersTab) {
    console.log("followers")

    getFollowers(userId).then((response) => {
      if (forMyself) usersPending.value = response.data.filter((it) => it.pending)
      usersAccepted.value = response.data.filter((it) => !it.pending)
      console.log(usersPending.value)
      console.log(usersAccepted.value)
    })
  } else {
    console.log("follows")
    getFollows(userId).then((response) => {
      if (forMyself) usersPending.value = response.data.filter((it) => it.pending)
      usersAccepted.value = response.data.filter((it) => !it.pending)
    })
  }

}

refreshData()


const hasPending = computed(() => usersPending?.value?.length)
const hasAccepted = computed(() => usersAccepted?.value?.length)

async function doDeclineFollowRequest(id) {
  await declineFollowRequest(id)
  await refreshData()
  pollingStore.removePendingFollow(id)
}

async function doAcceptFollowRequest(id) {
  await acceptFollowRequest(id)
  await refreshData()
  pollingStore.removePendingFollow(id)
}

async function cancelFollowRequest(id) {
  await unfollow(id)
  await refreshData()
}

async function doUnfollow(id) {
  await unfollow(id)
  await refreshData()
}

async function doRemoveFollower(id) {
  await removeFollower(id)
  await refreshData()
}

const getTitle = () => {
  if (props.isFollowersTab) return "followers"
  else return "follows"
}

</script>
<template>
<div class="followers sticker sk-pad">
  <h2 class="sk-h2">{{ $t(getTitle()) }}</h2>
  <p v-if="!hasAccepted && !hasPending" class="sk-muted ma-0">{{$t("nothing_to_display")}}</p>

  <template v-if="hasPending">
    <div class="sk-eyebrow">{{$t("pending")}}</div>
    <ul class="followers__list">
      <li v-for="user in usersPending" :key="user.user.username" class="followers__row">
        <a
          class="followers__user"
          :href="`/user/view/?user=${user.user.id}`"
          @click.prevent="toViewUser(user.user.id)"
        >
          <span class="sk-avatar"><img :src="getUserIconUrl(user.user.id, user.user.version)" alt="" /></span>
          <span class="followers__name">{{ user.user.username }}</span>
        </a>

        <span v-if="forMyself" class="followers__actions">
          <button
            v-if="isFollowersTab"
            type="button"
            class="sk-icon-btn sk-icon-btn--white"
            :aria-label="$t('accept_follow_request')"
            :title="$t('accept_follow_request')"
            @click.stop="doAcceptFollowRequest(user.user.id)"
          ><v-icon>{{ ICON_ACCEPT }}</v-icon></button>
          <button
            v-if="isFollowersTab"
            type="button"
            class="sk-icon-btn sk-icon-btn--white"
            :aria-label="$t('decline_follow_request')"
            :title="$t('decline_follow_request')"
            @click.stop="doDeclineFollowRequest(user.user.id)"
          ><v-icon>{{ ICON_DENY }}</v-icon></button>
          <button
            v-if="!isFollowersTab"
            type="button"
            class="sk-icon-btn sk-icon-btn--white"
            :aria-label="$t('cancel_follow_request')"
            :title="$t('cancel_follow_request')"
            @click.stop="cancelFollowRequest(user.user.id)"
          ><v-icon>{{ ICON_REMOVE }}</v-icon></button>
        </span>
      </li>
    </ul>
  </template>

  <hr v-if="hasPending && hasAccepted" class="sk-divider" />

  <template v-if="hasAccepted">
    <div class="sk-eyebrow">{{$t("accepted")}}</div>
    <ul class="followers__list">
      <li v-for="user in usersAccepted" :key="user.user.username" class="followers__row">
        <a
          class="followers__user"
          :href="`/user/view/?user=${user.user.id}`"
          @click.prevent="toViewUser(user.user.id)"
        >
          <span class="sk-avatar"><img :src="getUserIconUrl(user.user.id, user.user.version)" alt="" /></span>
          <span class="followers__name">{{ user.user.username }}</span>
        </a>

        <span v-if="forMyself" class="followers__actions">
          <button
            v-if="isFollowersTab"
            type="button"
            class="sk-icon-btn sk-icon-btn--white"
            :aria-label="$t('remove_follower')"
            :title="$t('remove_follower')"
            @click.stop="doRemoveFollower(user.user.id)"
          ><v-icon>{{ ICON_REMOVE }}</v-icon></button>
          <button
            v-if="!isFollowersTab"
            type="button"
            class="sk-icon-btn sk-icon-btn--white"
            :aria-label="$t('unfollow')"
            :title="$t('unfollow')"
            @click.stop="doUnfollow(user.user.id)"
          ><v-icon>{{ ICON_REMOVE }}</v-icon></button>
        </span>
      </li>
    </ul>
  </template>
</div>
</template>

<style scoped>
.followers {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: min(440px, calc(100vw - 48px));
  max-height: 80vh;
  overflow-y: auto;
}

.followers__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.followers__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.followers__user {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  color: var(--ink);
  font-weight: 700;
  text-decoration: none;
  border-radius: var(--radius-sm);
}

.followers__user:hover .followers__name {
  text-decoration: underline;
}

.followers__user:focus-visible {
  outline: 3px solid var(--mustard);
  outline-offset: 2px;
}

.followers__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.followers__actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
</style>
