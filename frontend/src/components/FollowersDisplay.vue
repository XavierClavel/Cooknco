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
// The profile page moved from /user/view?user=N to /user/N.
const userId = route.params.id
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
<v-card class="ml-2" max-width="500px">
  <v-card-text v-if="!hasAccepted && !hasPending">{{$t("nothing_to_display")}}</v-card-text>
  <v-list lines="two" style="background-color: transparent !important; border-width: 0 !important">
    <v-list-subheader class="text-left ml-n14" inset v-if="hasPending">{{$t("pending")}}</v-list-subheader>

    <v-list-item
      v-for="user in usersPending"
      :key="user.user.username"
      :title="user.user.username"
    >
      <template v-slot:prepend>
        <v-avatar color="black" class="mr-2" style="border:2px solid #0d1821;">
          <v-img :src="getUserIconUrl(user.user.id, user.user.version)" @click.stop="toViewUser(user.user.id)" class="clickable_image"></v-img>
        </v-avatar>
      </template>

      <template v-slot:append v-if="forMyself">
        <v-btn
          color="black"
          style="border-width: 0 !important"
          :icon="ICON_ACCEPT"
          variant="text"
          v-if="isFollowersTab"
          @click.stop="doAcceptFollowRequest(user.user.id)"
        ></v-btn>
        <v-btn
          color="black"
          style="border-width: 0 !important"
          :icon="ICON_DENY"
          variant="text"
          v-if="isFollowersTab"
          @click.stop="doDeclineFollowRequest(user.user.id)"
        ></v-btn>
        <v-btn
          color="black"
          style="border-width: 0 !important"
          :icon="ICON_REMOVE"
          variant="text"
          v-if="!isFollowersTab"
          @click.stop="cancelFollowRequest(user.user.id)"
        ></v-btn>
      </template>
    </v-list-item>

    <v-divider inset v-if="hasPending && hasAccepted"></v-divider>

    <v-list-subheader class="text-left ml-n14" inset v-if="hasAccepted">{{$t("accepted")}}</v-list-subheader>

    <v-list-item
      v-for="user in usersAccepted"
      :key="user.user.username"
      :title="user.user.username"
    >
      <template v-slot:prepend>
        <v-avatar color="black" class="mr-2" style="border:2px solid #0d1821;">
          <v-img :src="getUserIconUrl(user.user.id, user.user.version)" @click.stop="toViewUser(user.user.id)" class="clickable_image"></v-img>
        </v-avatar>
      </template>

      <template v-slot:append v-if="forMyself">
        <v-btn
          color="black"
          style="border-width: 0 !important"
          :icon="ICON_REMOVE"
          variant="text"
          v-if="isFollowersTab"
          @click.stop="doRemoveFollower(user.user.id)"
        ></v-btn>
        <v-btn
          color="black"
          style="border-width: 0 !important"
          :icon="ICON_REMOVE"
          variant="text"
          v-if="!isFollowersTab"
          @click.stop="doUnfollow(user.user.id)"
        ></v-btn>
      </template>
    </v-list-item>
  </v-list>
</v-card>
</template>

<style scoped>
.clickable_image {
  cursor: pointer
}
</style>
