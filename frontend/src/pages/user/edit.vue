<template>
  <v-card
  class="mx-auto pa-5 ma-auto my-5"
  max-width="1000px"
  >
    <v-container class="justify-center">
      <editable-picture
        v-if="ready"
        path="users"
        :id="userId"
        :version="user.version"
        rounded="circle"
        height="200px"
        width="200px"
        aspect-ratio="1"
        max-width="200px"
        buttons-size="50px"
        buttons-icon-size="text-h7"
        buttons-spacing="ga-4"
        buttons-rounded="lg"
        ref="editablePicture"
      ></editable-picture>
    </v-container>

    <error :error="errorMessage"></error>

    <v-text-field
      v-model="user.username"
      label="Username"
    ></v-text-field>

    <v-textarea
        v-model="user.bio"
        label="Description"
        :rules="[max255]"
    ></v-textarea>

    <v-container>
      <v-row
        class="d-flex align-center justify-center align-content-center mb-2 ga-4"
        dense
      >
          <action-button
            icon="mdi-close-circle-outline"
            :text="`${$t('cancel')}`"
            :action="() => toViewUser(userId)"
          ></action-button>
          <action-button
            icon="mdi-content-save"
            :text="`${$t('save')}`"
            :action="submit"
          ></action-button>
      </v-row>
    </v-container>



  </v-card>

</template>

<script lang="ts" setup>
import { useRoute } from 'vue-router';
import {ref} from "vue";
import {toEditUser, toErrorMessage, toViewUser} from "@/scripts/common";
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

const user = ref<object>({})

getUser(userId).then (
  function (response) {
    console.log(response)
    user.value = response.data
    ready.value = true
  }).catch(function (error) {
    errorMessage.value = toErrorMessage(error)
})

async function submit() {
  const submitted = {}
  submitted["username"] = user.value.username
  submitted["bio"] = user.value.bio
  console.log(submitted)
  try {
    await updateUser(submitted).catch()
    const newVersion = await editablePicture.value.submitImage()
    console.log(newVersion)
    const authStore = useAuthStore()
    authStore.setImgVersion(newVersion)
    toViewUser(userId)
  } catch (e) {
    console.log(e)
    errorMessage.value = toErrorMessage(e, "image_upload_failed")
  }

}

</script>
