<template>
  <div class="sk-page">
    <header>
      <h1 class="sk-title">{{ $t("cookbooks") }}</h1>
      <p v-if="loaded" class="sk-hint cookbooks__count">{{ $t("cookbooks_count", cookbooks.length) }}</p>
    </header>

    <div class="cookbook-grid">
      <cookbook v-for="cookbook in cookbooks" :key="cookbook.id" :cookbook="cookbook"></cookbook>

      <button type="button" class="new-cookbook sticker sticker--dashed" @click="toCreateCookbook()">
        <v-icon>mdi-plus</v-icon>
        {{ $t("new_cookbook") }}
      </button>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {
  toCreateCookbook,
} from "@/scripts/common";
import {listCookbooks} from "@/scripts/cookbooks";
import Cookbook from "@/components/Cookbook.vue";

const cookbooks = ref<object[]>([])
const loaded = ref(false)

listCookbooks(window.location.search).then(
  function (response) {
    cookbooks.value = response.data
    loaded.value = true
  }
)

</script>

<style scoped>
.cookbooks__count {
  margin: 4px 0 0;
}

.cookbook-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 280px), 1fr));
  gap: 18px;
  padding: 0 var(--lift) var(--lift) 0;
}

.new-cookbook {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 100px;
  font-family: var(--font);
  font-size: 15px;
  font-weight: 700;
  color: var(--ink);
  cursor: pointer;
  transition: background .1s ease;
}

.new-cookbook:hover,
.new-cookbook:active {
  background: rgba(255, 255, 255, .25);
  box-shadow: none;
  transform: none;
}

.new-cookbook:focus-visible {
  outline: 3px solid var(--mustard);
  outline-offset: 3px;
}
</style>
