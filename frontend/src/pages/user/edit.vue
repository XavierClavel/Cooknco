<template>
  <div class="sk-narrow">
    <section class="sticker sk-pad-lg">
      <form class="sk-stack" @submit.prevent="onSave">
        <h1 class="sk-title">{{ $t("edit_profile") }}</h1>

        <editable-picture
          v-if="ready"
          path="users"
          :id="userId"
          :version="user.version"
          rounded="circle"
          width="132px"
          aspect-ratio="1"
          controls="side"
          ref="editablePicture"
        ></editable-picture>

        <error :error="errorMessage"></error>

        <div class="sk-field">
          <label class="sk-label" for="profile-username">{{ $t("username") }}</label>
          <v-text-field
            id="profile-username"
            class="my-0"
            v-model="user.username"
          ></v-text-field>
        </div>

        <div class="sk-field">
          <label class="sk-label" for="profile-bio">{{ $t("description") }}</label>
          <v-textarea
            id="profile-bio"
            v-model="user.bio"
            rows="3"
            auto-grow
            :rules="[max255]"
          ></v-textarea>
        </div>

        <div class="profile-form__actions">
          <button type="button" class="sk-btn sk-btn--lg" @click="toViewUser(userId)">{{ $t("cancel") }}</button>
          <button type="submit" class="sk-btn sk-btn--lg sk-btn--coral" :disabled="saving">
            <v-progress-circular v-if="saving" indeterminate size="18" width="2"></v-progress-circular>
            {{ $t("save") }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>

<script lang="ts" setup>
import { useRoute } from 'vue-router';
import {ref} from "vue";
import {toErrorMessage, toViewUser} from "@/scripts/common";
import {getUser, updateUser} from "@/scripts/users";
import EditablePicture from "@/components/EditablePicture.vue";
import {max255} from "@/scripts/rules";
import {useAuthStore} from "@/stores/auth";

// Get the route object
const route = useRoute();
const userId = route.query.user
const errorMessage = ref(null)
const editablePicture = ref(null)
const ready = ref(false)
const saving = ref(false)

const user = ref<object>({})

getUser(userId).then (
  function (response) {
    console.log(response)
    user.value = response.data
    ready.value = true
  }).catch(function (error) {
    errorMessage.value = toErrorMessage(error)
})

async function onSave() {
  if (saving.value) return
  saving.value = true
  try {
    await submit()
  } finally {
    saving.value = false
  }
}

async function submit() {
  const submitted = {}
  submitted["username"] = user.value.username
  submitted["bio"] = user.value.bio
  console.log(submitted)
  errorMessage.value = null
  // Saved and uploaded in two steps, each reporting its own failure: the fallback
  // wording is only right for the step it guards.
  try {
    await updateUser(submitted)
  } catch (e) {
    console.log(e)
    errorMessage.value = toErrorMessage(e)
    return
  }

  try {
    const newVersion = await editablePicture.value.submitImage()
    useAuthStore().setImgVersion(newVersion)
  } catch (e) {
    console.log(e)
    errorMessage.value = toErrorMessage(e, "image_upload_failed")
    return
  }

  toViewUser(userId)
}

</script>

<style scoped>
.profile-form__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
  margin-top: 8px;
}
</style>
