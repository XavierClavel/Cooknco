<template>
  <v-container class="pa-0 ma-0" align="center">
  <error :error="errors"></error>
  <v-card
    v-if="!errors"
    class="pa-5 mr-sm-n5"
  >

    <v-container class="d-flex
      flex-wrap justify-center
      flex-sm-nowrap">
      <v-img
        color="surface-variant"
        :src="getCookbookIconUrl(cookbook.id, cookbook.version)"
        rounded="lg"
        height="200px"
        width="200px"
        max-width="200px"
        aspect-ratio="1"
        cover
        style="border: 3px solid #0d1821 !important;"
      ></v-img>
      <v-container class="d-flex flex-column mt-3 mt-sm-0">
        <v-card-title
          class="text-black text-h2 font-weight-bold my-n8
          text-center text-sm-left"
        >{{ cookbook.title }}</v-card-title>
        <v-card-text class="text-center text-sm-left">
          {{cookbook.description}}
        </v-card-text>
        <v-row class="ml-sm-2 d-flex flex-row
          justify-center justify-sm-start">
          <v-col class="d-inline-flex" cols="auto">
            <picto-info :icon="`${ICON_COOKBOOK_RECIPES}`" :value="cookbook.recipesCount" icon-size="text-h4" value-size="text-h5"></picto-info>
          </v-col>
          <v-col class="d-inline-flex" cols="auto">
            <picto-info :icon="`${ICON_COOKBOOK_USERS}`" :value="cookbook.usersCount" icon-size="text-h4" value-size="text-h5"></picto-info>
          </v-col>
        </v-row>
      </v-container>
    </v-container>



    <v-container>
      <v-row
        class="d-flex justify-left mb-2 gx-16"
        dense
      >
        <v-col cols="12" sm="auto" >
          <action-button
            icon="mdi-cog"
            :text="`${$t('edit')}`"
            :action="() => toEditCookbook(cookbookId)"
          ></action-button>
        </v-col>
        <v-col cols="12" sm="auto" >
          <v-dialog max-width="500">
            <template v-slot:activator="{ props: activatorProps }">
              <action-button
                v-bind="activatorProps"
                icon="mdi-door-open"
                :text="`${$t('leave')}`"
              ></action-button>
            </template>

            <template v-slot:default="{ isActive }">
              <v-card>
                <v-card-text class="font-weight-bold text-h4">{{$t("leaveCookbookTitle")}}</v-card-text>
                <v-card-text>
                  {{$t("leaveCookbookDescription")}}
                </v-card-text>

                <v-card-actions>
                  <v-spacer></v-spacer>

                  <v-btn
                    :text="`${$t('cancel')}`"
                    @click="isActive.value = false"
                  ></v-btn>
                  <v-btn
                    :text="`${$t('leave')}`"
                    @click="() => leave()"
                  ></v-btn>
                </v-card-actions>
              </v-card>
            </template>
          </v-dialog>
        </v-col>
      </v-row>
    </v-container>


  </v-card>
  </v-container>
  <recipes-list v-if="!errors"></recipes-list>
</template>

<script lang="ts" setup>
import {useRoute} from "vue-router";
import {ref} from "vue";
import {
  getCookbookIconUrl,
  getUserIconUrl,
  toEditCookbook,
  toEditUser,
  toListCookbooks,
  toSignup
} from "@/scripts/common";
import {getCookbook, isAdminOfCookbook, leaveCookbook} from "@/scripts/cookbooks";
import {ICON_COOKBOOK_RECIPES, ICON_COOKBOOK_USERS} from "@/scripts/icons";
import {useAuthStore} from "@/stores/auth";
const route = useRoute();

// Keeps this dynamic route from swallowing a sibling static path
// (/recipe/edit, /user/settings, ...) and makes /recipe/banana a clean 404
// rather than a 400 from the backend.
definePageMeta({ validate: (r) => /^\d+$/.test(String(r.params.id)) })
let cookbookId = ref(route.params.id)
const isAdmin = ref(false)
const errors = ref(null)
const {t} = useI18n()

const EMPTY_COOKBOOK = {
  title: "",
  description: "",
  users: [],
  recipesCount: null,
  usersCount: null,
  version: null,
}

// Fetched during render so useShareMeta can emit real Open Graph tags. Runs
// anonymously — see the note on the recipe page — so a non-public cookbook
// comes back 403 and gets a noindex stub instead of leaking its title.
const { data, error } = await useAsyncData(
  () => `cookbook:${cookbookId.value}`,
  async () => {
    try {
      return { cookbook: await $fetch<any>(`${apiBase()}/cookbook/${cookbookId.value}`), restricted: false }
    } catch (e: any) {
      if ((e?.status ?? e?.response?.status) === 403) return { cookbook: null, restricted: true }
      throw e
    }
  },
)

if (error.value) {
  throw createError({
    statusCode: (error.value as any).statusCode === 404 ? 404 : 500,
    statusMessage: 'Cookbook not found',
    fatal: true,
  })
}

const restricted = computed(() => !!data.value?.restricted)
const cookbook = computed<any>(() => data.value?.cookbook ?? EMPTY_COOKBOOK)

useShareMeta(() => ({
  kind: 'cookbook',
  title: restricted.value ? t('private_cookbook') : cookbook.value.title,
  description: restricted.value ? null : cookbook.value.description,
  imageId: cookbook.value.id,
  imageVersion: cookbook.value.version,
  noindex: restricted.value,
}))

onMounted(async () => {
  // A member of a private cookbook may see it; the anonymous render could not.
  if (restricted.value) {
    try {
      data.value = { cookbook: (await getCookbook(cookbookId.value)).data, restricted: false }
    } catch (e: any) {
      errors.value = e?.response?.data
    }
  }

  isAdminOfCookbook(cookbookId.value).then((response) => {
    isAdmin.value = response.data
  })
})

async function leave() {
  await leaveCookbook(cookbookId.value)
  const authStore = useAuthStore()
  toListCookbooks(authStore.id)
}
</script>
