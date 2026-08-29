<template>
  <v-layout class="d-flex flex-wrap  justify-space-evenly mt-6">
    <v-card class="d-flex flex-column pa-0 ma-0" width="100%" color="transparent" variant="flat" style="border:0 !important">
    <v-card-title>{{$t("cookbooks")}}
      <v-btn
        color="black"
        class="ma-4 ml-6 text-h5 text-black"
        rounded="lg"
        height="50px"
        width="50px"
        @click="toCreateCookbook()"
        icon="mdi-plus"
        variant="outlined"
        style="border: 4px solid #0d1821 !important;"
      ></v-btn></v-card-title>

      <v-card class="d-flex pa-0 ma-0" color="transparent" variant="flat" style="border:0 !important">
        <v-row class="ma-1">
          <cookbook :cookbook="cookbook" v-for="cookbook in cookbooks"></cookbook>
        </v-row>

      </v-card>
    </v-card>


  </v-layout>
</template>

<script lang="ts" setup>
import {
  toCreateCookbook,
} from "@/scripts/common";
import {useRoute} from "vue-router";
import {listCookbooks} from "@/scripts/cookbooks";
import Cookbook from "@/components/Cookbook.vue";

const cookbooks = ref<object[]>([])

const route = useRoute();

listCookbooks(route.fullPath.split('?')[1] ?? '').then(
  function (response) {
    console.log(response.data)
    cookbooks.value = response.data
  }
)

</script>
