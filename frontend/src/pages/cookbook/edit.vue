<template>
  <div class="sk-narrow">
    <section class="sticker sk-pad-lg">
      <v-form @submit.prevent="submit" ref="form" class="sk-stack">
        <h1 class="sk-title cookbook-form__title">{{ cookbookId ? $t("edit_cookbook") : $t("new_cookbook") }}</h1>

        <div class="sk-field">
          <label class="sk-label" for="cookbook-title">{{ $t("title") }}</label>
          <v-text-field
            id="cookbook-title"
            class="my-0"
            v-model="cookbook.title"
            :rules="[requiredRule, max50]"
          ></v-text-field>
        </div>

        <div class="sk-field">
          <label class="sk-label" for="cookbook-description">{{ $t("description") }}</label>
          <v-textarea
            id="cookbook-description"
            v-model="cookbook.description"
            rows="2"
            auto-grow
            :rules="[max255]"
          ></v-textarea>
        </div>

        <div class="sk-field">
          <label class="sk-label" for="cookbook-visibility">{{ $t("visibility") }}</label>
          <v-select
            id="cookbook-visibility"
            class="cookbook-form__visibility"
            v-model="cookbook.visibility"
            :item-title="(option) => $t(option.label)"
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
              {{ $t(item.raw.label) }}
            </template>
          </v-select>
          <!-- Said under the select: "Followers" alone does not say whose, which is the whole question. -->
          <p v-if="visibilityHint" class="sk-hint ma-0">{{ $t(visibilityHint) }}</p>
        </div>

        <editable-picture
          v-if="ready"
          path="cookbooks"
          :id="cookbookId"
          :version="cookbook.version"
          ref="editablePicture"
          width="168px"
          aspect-ratio="1"
          buttons-size="40px"
          buttons-icon-size="text-body-1"
          buttons-spacing="ga-2"
          buttons-rounded="lg"
          :empty-label="$t('add_cover')"
        ></editable-picture>

        <!-- Cookbook users -->
        <h2 class="sk-h2 mt-2">{{ $t("users") }}</h2>
        <div class="member-row" v-for="index in members.length" :key="index">
          <span class="sk-avatar member-row__avatar">
            <img
              v-if="members[index - 1]?.id > 0"
              :src="getUserIconUrl(members[index - 1].id, members[index - 1].version)"
              alt=""
            />
          </span>

          <v-autocomplete
            class="member-row__user"
            v-model="members[index-1]"
            :aria-label="`${$t('user')} ${index}`"
            :placeholder="$t('username')"
            :items="autocompleteList[index-1]"
            item-color="primary"
            item-title="username"
            item-value="id"
            @update:search="(query) => onAutocompleteChange(query, index-1)"
            @update:modelValue="value => updateMember(index - 1, value)"
            :key="index"
            return-object
          ></v-autocomplete>

          <v-select
            class="member-row__role"
            v-model="members[index-1].role"
            :aria-label="$t('role')"
            :items="['USER','ADMIN']"
          ></v-select>

          <button
            type="button"
            class="sk-icon-btn sk-icon-btn--white"
            :aria-label="$t('remove_member')"
            @click="removeUser(index-1)"
          >
            <v-icon>mdi-trash-can-outline</v-icon>
          </button>
        </div>

        <!-- Button to add a member -->
        <button type="button" class="add-member" @click="addUser">
          <v-icon>mdi-plus</v-icon>
          {{ $t("user") }}
        </button>

        <error :error="errorMessage"></error>

        <div class="cookbook-form__actions">
          <button
            v-if="cookbookId"
            type="button"
            class="sk-btn sk-btn--lg"
            @click="toViewCookbook(cookbookId)"
          >{{ $t("cancel") }}</button>
          <button type="submit" class="sk-btn sk-btn--lg sk-btn--coral" :disabled="saving">
            <v-progress-circular v-if="saving" indeterminate size="18" width="2"></v-progress-circular>
            {{ $t("save") }}
          </button>
        </div>
      </v-form>
    </section>
  </div>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';
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
const members = ref<Array<{ id: number, username: string, role: string, version?: number }>>([]);
const ready = ref(false)
const saving = ref(false)


const autocompleteList = ref([])

const onAutocompleteChange = async (query, index) => {
  // As a filter object, not a bare term: URLSearchParams would read "xavier" as a
  // parameter name and send `?xavier=`, so the list came back unfiltered and whoever was
  // being typed never appeared in it
  const response = await listUsers({query}, 0, 20);
  autocompleteList.value[index] = response.data.items.filter(it => !takenMemberIds(index).has(it.id));
}

/**
 * The accounts already on the cookbook, ignoring the row being filled in - which is allowed
 * to keep whoever it already holds.
 *
 * The server keeps one row per member, so adding the same account twice silently loses
 * whichever role was set first. A row nobody has picked yet carries id -1, which is not an
 * account and must not block the empty rows after it.
 */
const takenMemberIds = (index) => new Set(
  members.value
    .filter((member, i) => i != index && member?.id != null && member.id > 0)
    .map(member => member.id)
)

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

const visibilityHint = computed(() =>
  visibilityOptions.value.find(option => option.value === cookbook.value.visibility)?.hint
)
const authStore = useAuthStore()


if (cookbookId.value == null) {
  members.value.push({
    id: authStore.id,
    username: authStore.username,
    version: authStore.iconVersion,
    role: "ADMIN",
  })
}


const addUser = () => {
  members.value.push({username: "",role: "USER", id: -1});
}


const removeUser = (index) => {
  members.value.splice(index,1);
}


// One save at a time: a second click while the first is on its way would create the cookbook twice.
async function submit() {
  if (saving.value) return
  saving.value = true
  try {
    await save()
  } finally {
    saving.value = false
  }
}

async function save() {
  const {valid} = await form.value.validate()
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
  // Saved and uploaded in two steps, each reporting its own failure: the fallback
  // wording is only right for the step it guards.
  try {
    if (cookbookId.value == null) {
      const response = await createCookbook(submitted)
      cookbookId.value = response.data.id
      if (addRecipeId != null) {
        await addRecipeToCookbook(cookbookId.value, addRecipeId)
      }
    } else {
      await editCookbook(cookbookId.value, submitted)
    }

    const membersInput = members.value.filter(item => item.id != null).map(item => ({ id: item.id, isAdmin: item.role == "ADMIN" }))
    await setCookbookUsers(cookbookId.value, membersInput)
  } catch (error) {
    console.log(error)
    errorMessage.value = toErrorMessage(error)
    return
  }

  try {
    await editablePicture.value.submitImage(cookbookId.value)
  } catch (error) {
    console.log(error)
    // The cookbook itself is already saved: stay on the form so that the image
    // can be submitted again instead of silently dropping it.
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
      members.value = response.data.map(item => ({id: item.id, username: item.username, version: item.version, role: item.isAdmin ? "ADMIN" : "USER"}))
    }
  )
} else {
  ready.value = true
}



</script>

<style scoped>
.cookbook-form__title {
  margin-bottom: 4px;
}

.cookbook-form__visibility {
  max-width: 340px;
}

.member-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.member-row__avatar {
  width: 40px;
  height: 40px;
}

.member-row__user {
  flex: 1 1 220px;
  min-width: 0;
}

.member-row__role {
  flex: 0 1 180px;
  min-width: 120px;
}

.add-member {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 44px;
  border: var(--line) dashed var(--ink);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--ink);
  font-family: var(--font);
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.add-member:hover {
  background: rgba(255, 255, 255, .5);
}

.add-member:focus-visible {
  outline: 3px solid var(--mustard);
  outline-offset: 2px;
}

.cookbook-form__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
  margin-top: 8px;
}
</style>
