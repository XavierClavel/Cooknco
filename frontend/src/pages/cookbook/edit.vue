<template>
  <v-card
  class="mx-auto pa-5 ma-auto my-5"
  max-width="1000px"
  >

    <v-form @submit.prevent="submit" class="mx-auto" ref="form">

        <v-text-field
          v-model="cookbook.title"
          :label="`${$t('title')}`"
          :rules="[requiredRule, max50]"
        ></v-text-field>

        <v-textarea
          v-model="cookbook.description"
          :label="`${$t('description')}`"
          single-line
          :rules="[max255]"
        ></v-textarea>


      <v-select
        max-width="200px"
        v-model="cookbook.visibility"
        :label="`${$t('visibility')}`"
        item-title="label"
        item-value="value"
        :items="visibilityOptions"
      >
        <!-- Customize how items appear in the dropdown -->
        <template v-slot:item="{ props, item }">
          <v-list-item v-bind="props">
            <template #prepend>
              <v-icon>{{ $t(item.raw.icon) }}</v-icon>
            </template>
          </v-list-item>
        </template>

        <!-- Customize how selected item appears -->
        <template v-slot:selection="{ item }">
          <v-icon start class="mr-2">{{ item.raw.icon }}</v-icon>
          {{ item.raw.label }}
        </template>
      </v-select>

       <editable-picture
         v-if="ready"
         path="cookbooks"
         :id="cookbookId"
         :version="cookbook.version"
         ref="editablePicture"
         width="600px"
         height="600px"
         class="my-2"
         aspect-ratio="1"
       ></editable-picture>

      <!-- Cookbook users -->
      <h2 class="my-3" >{{$t("users")}}</h2>
          <div class="d-flex flex-wrap align-center mb-2" v-for="index in members.length">

            <v-avatar size="50" variant="elevated" style="border:3px solid #0d1821 !important;" class="mr-3">
              <v-img
                v-if="members[index - 1]"
                color="surface-variant"
                :src="getUserIconUrl(members[index-1].id)"
                cover
                v-bind="props"
                class="clickable_image"
              ></v-img>
            </v-avatar>

            <v-autocomplete
              v-model="members[index-1]"
              :label="`${$t('user')} ${index}`"
              :items="autocompleteList[index-1]"
              item-color="primary"
              item-title="username"
              item-value="id"
              @update:search="(query) => onAutocompleteChange(query, index-1)"
              @update:modelValue="value => updateMember(index - 1, value)"
              :key="index"
              return-object
              class="mr-2"
            ></v-autocomplete>

            <v-select
              max-width="200px"
              v-model="members[index-1].role"
              :label="`${$t('role')}`"
              :items="['USER','ADMIN']"
            ></v-select>

            <div>
              <v-btn
                @click="removeUser(index-1)"
                icon="mdi-delete"
                color="primary"
                class="ml-4"
              ></v-btn>
            </div>


          </div>

      <!-- Button to add ingredient -->
      <v-btn
        @click="addUser"
        prepend-icon="mdi-plus-circle-outline"
        color="primary"
        flat
        class="mb-10"
      >{{$t("user")}}</v-btn>


      <error :error="errorMessage"></error>

      <v-container>
        <v-row
          class="d-flex align-center justify-center mb-2 ga-4"
          dense
        >
            <action-button
              v-if="cookbookId"
              icon="mdi-close-circle-outline"
              :text="`${$t('cancel')}`"
              :action="() => toViewCookbook(cookbookId)"
            ></action-button>
            <action-button
              icon="mdi-content-save"
              :text="`${$t('save')}`"
              :action="submit"
            ></action-button>
        </v-row>
      </v-container>
    </v-form>
  </v-card>

</template>

<script lang="ts" setup>
import { ref } from 'vue';
import { useRoute } from 'vue-router';
import {getUserIconUrl, toErrorMessage, toViewCookbook} from "@/scripts/common";
import EditablePicture from "@/components/EditablePicture.vue";
import {listUsers} from "@/scripts/users";
import {
  addRecipeToCookbook,
  createCookbook,
  editCookbook,
  getCookbook,
  getCookbookUsers,
  setCookbookUsers
} from "@/scripts/cookbooks";
import {useAuthStore} from "@/stores/auth";
import {visibilityOptions} from "@/scripts/values";
import {max255, max50, requiredRule} from "@/scripts/rules";



// Get the route object
const route = useRoute();
let cookbookId = ref(route.query.cookbook)
let addRecipeId = route.query.addRecipe
const editablePicture = ref(null)
const errorMessage = ref(null)
const form = ref(null)
const members = ref<Array<{ id: number, username: string, role: string }>>([]);
const ready = ref(false)


const autocompleteList = ref([])

const onAutocompleteChange = async (query, index) => {
  const response = await listUsers(query, 0, 20);
  autocompleteList.value[index] = response.data.items;
}

const updateMember = (index, value) => {
  if (value) {
    members.value[index] = { ...value, role: members.value[index]?.role ?? 'USER' };
  } else {
    members.value[index] = {role: "USER", username: ""};
  }
}


const cookbook = ref<object>({
  title: "",
  description: "",
  visibility: "PUBLIC"
})
const authStore = useAuthStore()


if (cookbookId.value == null) {
  members.value.push({
    id: authStore.id,
    username: authStore.username,
    role: "ADMIN",
  })
}


const addUser = () => {
  members.value.push({username: "",role: "USER", id: -1});
}


const removeUser = (index) => {
  members.value.splice(index,1);
}


async function submit() {
  const {valid, errors} = await form.value.validate()
  if (!valid) {
    return
  }
  console.log(members.value)
  const submitted = {}
  submitted["title"] = cookbook.value.title
  submitted["description"] = cookbook.value.description
  submitted['visibility'] = cookbook.value.visibility
  console.log(submitted)
  errorMessage.value = null
  try {
    if (cookbookId.value == null) {
      const response = await createCookbook(submitted)
      cookbookId.value = response.data.id
      if (addRecipeId != null) {
        await addRecipeToCookbook(cookbookId.value, addRecipeId)
      }
      // Lets the picture component pick up the id of the cookbook it belongs to.
      await nextTick()
    } else {
      await editCookbook(cookbookId.value, submitted)
    }

    const membersInput = members.value.filter(item => item.id != null).map(item => ({ id: item.id, isAdmin: item.role == "ADMIN" }))
    await setCookbookUsers(cookbookId.value, membersInput)

    await editablePicture.value.submitImage()
  } catch (error) {
    console.log(error)
    errorMessage.value = toErrorMessage(error, "image_upload_failed")
    return
  }

  toViewCookbook(cookbookId.value)
}


if (cookbookId.value != null) {
  getCookbook(cookbookId.value).then (
    function (response) {
      cookbook.value.title = response.data.title
      cookbook.value.description = response.data.description
      cookbook.value.visibility = response.data.visibility
      cookbook.value.version = response.data.version
      console.log(cookbook.value)
      ready.value = true
    }).catch(function (error) {
    console.log(error);
  })

  getCookbookUsers(cookbookId.value).then(
    function (response) {
      members.value = response.data.map(item => ({id: item.id, username: item.username, role: item.isAdmin ? "ADMIN" : "USER"}))
    }
  )
} else {
  ready.value = true
}



</script>

<style scoped>
</style>
