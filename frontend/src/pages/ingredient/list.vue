<template>
  <div class="sk-page">
    <h1 class="sk-title">{{ $t("ingredients") }}</h1>

    <section class="sticker sk-pad ing-card">
      <div class="sk-input-wrap ing-search">
        <v-icon>mdi-magnify</v-icon>
        <input
          v-model="query"
          :aria-label="$t('search_ingredient')"
          class="sk-input"
          :placeholder="$t('search_ingredient')"
          type="search"
          @input="updateQuery"
        >
      </div>

      <div class="sk-table-scroll ing-scroll">
        <table class="sk-table ing-table">
          <thead>
            <tr>
              <th scope="col">{{ $t("ingredient") }}</th>
              <th scope="col">{{ $t("calories") }}</th>
              <th scope="col">{{ $t("cholesterol") }}</th>
              <th scope="col">{{ $t("carbohydrates") }}</th>
              <th scope="col">{{ $t("sugars") }}</th>
              <th scope="col">{{ $t("fibers") }}</th>
              <th scope="col">{{ $t("proteins") }}</th>
              <th scope="col">{{ $t("sodium") }}</th>
              <th scope="col">{{ $t("saturatedFat") }}</th>
              <th scope="col">{{ $t("unsaturatedFat") }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="ingredient in ingredients"
              :key="ingredient.id"
              class="ing-row"
              @click="toViewIngredient(ingredient.id)"
            >
              <td>
                <a
                  class="ing-name"
                  :href="`/ingredient/view?ingredient=${ingredient.id}`"
                  @click.prevent.stop="toViewIngredient(ingredient.id)"
                >
                  <span class="sk-tile">
                    <img alt="" class="ing-icon" :src="getIngredientImageUrl(ingredient.type)">
                  </span>
                  {{ nameOf(ingredient) }}
                </a>
              </td>
              <td>{{ ingredient.calories }}</td>
              <td>{{ ingredient.cholesterol }}</td>
              <td>{{ ingredient.carbohydrates }}</td>
              <td>{{ ingredient.sugars }}</td>
              <td>{{ ingredient.fibers }}</td>
              <td>{{ ingredient.proteins }}</td>
              <td>{{ ingredient.sodium }}</td>
              <td>{{ ingredient.saturatedFat }}</td>
              <td>{{ ingredient.unsaturatedFat }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <nav v-if="pagesCount > 1" :aria-label="$t('pagination')" class="ing-pages">
        <button
          :aria-label="$t('previous_page')"
          class="ing-page"
          :disabled="page <= 1"
          type="button"
          @click="goTo(page - 1)"
        >
          <v-icon>mdi-chevron-left</v-icon>
        </button>
        <template v-for="(item, i) in pageItems" :key="i">
          <span v-if="item === null" aria-hidden="true" class="ing-gap">…</span>
          <button
            v-else
            :aria-current="item === page ? 'page' : undefined"
            class="ing-page"
            :class="{'is-active': item === page}"
            type="button"
            @click="goTo(item)"
          >{{ item }}</button>
        </template>
        <button
          :aria-label="$t('next_page')"
          class="ing-page"
          :disabled="page >= pagesCount"
          type="button"
          @click="goTo(page + 1)"
        >
          <v-icon>mdi-chevron-right</v-icon>
        </button>
      </nav>
    </section>
  </div>
</template>

<script lang="ts" setup>
  // Browsing only: creating, editing and deleting ingredients live in the backoffice at /admin.
  import { searchIngredients } from '@/scripts/ingredients'
  import { getIngredientImageUrl, toViewIngredient } from '@/scripts/common'
  import { getLocale } from '@/scripts/localization'
  import { debounce } from 'lodash'

  /** A page number, or null for the gap between two that are not next to each other. */
  type PageItem = number | null

  const PAGE_SIZE = 20

  const ingredients = ref<object[]>([])
  const page = ref<number>(1)
  const pagesCount = ref<number>(1)
  const query = ref('')

  const nameOf = ingredient => ingredient.name?.[getLocale().toUpperCase()]

  const updateDisplay = debounce(() => {
    searchIngredients(`query=${query.value || ''}`, page.value - 1, PAGE_SIZE).then(
      function (response) {
        ingredients.value = response.data.items
        pagesCount.value = Math.ceil(response.data.count / PAGE_SIZE)
      }).catch(function (error) {
      console.log(error)
    })
  }, 500)

  const updateQuery = () => {
    page.value = 1
    updateDisplay()
  }

  const goTo = (target: number) => {
    if (target < 1 || target > pagesCount.value || target === page.value) return
    page.value = target
    updateDisplay()
  }

  /** The first and last pages, and the ones around the current one. */
  const pageItems = computed(() => {
    const total = pagesCount.value
    const current = page.value
    const shown = new Set([1, total, current - 1, current, current + 1].filter(p => p >= 1 && p <= total))
    const sorted = [...shown].sort((a, b) => a - b)
    const items: PageItem[] = []
    sorted.forEach((p, i) => {
      if (i > 0 && p - sorted[i - 1] > 1) items.push(null)
      items.push(p)
    })
    return items
  })

  updateDisplay()
</script>

<style scoped>
.ing-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.ing-search {
  max-width: 480px;
}

.ing-scroll {
  scrollbar-color: var(--ink) var(--rule);
  scrollbar-width: thin;
}

.ing-table th {
  min-width: 84px;
}

.ing-table th:first-child {
  min-width: 200px;
}

.ing-row {
  cursor: pointer;
}

.ing-row:hover td {
  background: var(--cream);
}

.ing-name {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--ink);
  font-weight: 700;
  text-decoration: none;
  white-space: nowrap;
}

.ing-name:focus-visible {
  outline: 3px solid var(--mustard);
  outline-offset: 2px;
  border-radius: 6px;
}

.ing-icon {
  width: 22px;
  height: 22px;
  object-fit: contain;
}

.ing-pages {
  display: flex;
  justify-content: center;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding-top: 4px;
}

.ing-page {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 40px;
  height: 40px;
  padding: 0 8px;
  border: var(--line) solid var(--ink);
  border-radius: var(--radius-sm);
  background: var(--white);
  color: var(--ink);
  font-family: var(--font);
  font-weight: 700;
  cursor: pointer;
}

.ing-page:hover:not(:disabled):not(.is-active) {
  background: var(--cream);
}

.ing-page:focus-visible {
  outline: 3px solid var(--mustard);
  outline-offset: 2px;
}

.ing-page:disabled {
  opacity: .4;
  cursor: default;
}

.ing-page.is-active {
  background: var(--ink);
  color: var(--white);
}

.ing-gap {
  color: var(--muted);
  font-weight: 700;
}
</style>
