<template>
  <div class="page">
    <div class="panel">
      <div class="panel-head filters">
        <div class="search">
          <ui-icon name="search" :size="15" />
          <input v-model="filters.query" class="input" style="width:250px"
                 :placeholder="$t('admin_search_ingredient')" @input="debouncedReload" />
        </div>
        <select v-model="filters.type" class="select" style="width:180px" @change="reset">
          <option :value="null">{{ $t('admin_all_types') }}</option>
          <option v-for="ty in types" :key="ty" :value="ty">{{ ty }}</option>
        </select>
        <span class="spacer"></span>
        <button class="btn icon" :title="$t('admin_refresh')" @click="reload"><ui-icon name="refresh" /></button>
      </div>

      <div class="progress" v-if="loading"><i></i></div>

      <div class="table-wrap">
        <table class="grid">
          <thead>
            <tr>
              <th>{{ $t('admin_name_en') }}</th>
              <th>{{ $t('admin_name_fr') }}</th>
              <th>{{ $t('type') }}</th>
              <th class="right">{{ $t('calories') }}</th>
              <th class="right">{{ $t('admin_used_in_recipes') }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="i in rows" :key="i.id">
              <td>{{ i.name?.EN || '—' }}</td>
              <td class="muted">{{ i.name?.FR || '—' }}</td>
              <td><span class="badge">{{ i.type }}</span></td>
              <td class="right tnum">{{ i.calories }}</td>
              <td class="right tnum">
                <span v-if="i.recipesCount" class="badge accent">{{ i.recipesCount }}</span>
                <span v-else class="subtle">0</span>
              </td>
              <td class="actions">
                <span class="row-actions">
                  <button class="btn icon danger-hover" :title="$t('delete')"
                          @click="open(i)"><ui-icon name="trash" /></button>
                </span>
              </td>
            </tr>
          </tbody>
        </table>
        <ui-empty v-if="!loading && !rows.length" icon="leaf"
                  :title="$t('admin_no_result')" :hint="$t('admin_no_result_hint')" />
      </div>

      <div class="panel-foot">
        <ui-pager :page="page" :size="size" :total="total" @update:page="p => { page = p; reload() }" />
      </div>
    </div>

    <ui-modal v-model="dialog" :title="$t('admin_delete_ingredient')">
      <div class="col" style="gap:12px">
        <p>{{ $t('admin_delete_ingredient_confirm', {name: selectedName}) }}</p>
        <div v-if="selected?.recipesCount" class="alert warn">
          <ui-icon name="alert" />
          <span>{{ $t('admin_delete_ingredient_in_use', {count: selected.recipesCount}) }}</span>
        </div>
      </div>
      <template #actions>
        <button class="btn" @click="dialog = false">{{ $t('cancel') }}</button>
        <button class="btn danger" @click="confirmDelete">{{ $t('delete') }}</button>
      </template>
    </ui-modal>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {deleteIngredient, errorKey, listIngredients} from '../lib/api'
import {notifyError, notifyOk} from '../lib/toast'
import UiIcon from '../components/UiIcon.vue'
import UiModal from '../components/UiModal.vue'
import UiPager from '../components/UiPager.vue'
import UiEmpty from '../components/UiEmpty.vue'

const {t, te} = useI18n()

const types = [
  'VEGETABLE', 'FRUIT', 'GRAIN', 'NUT', 'DAIRY', 'FISH', 'MEAT',
  'CONDIMENT', 'OIL', 'BAKERY', 'BEVERAGE_INGREDIENT', 'ALCOHOL', 'MISCELLANEOUS',
]

const rows = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(25)
const total = ref(0)
const filters = reactive<any>({query: '', type: null})

const selected = ref<any>(null)
const dialog = ref(false)
const selectedName = computed(() => selected.value?.name?.EN || selected.value?.name?.FR || '')

function fail(e: any) {
  const key = errorKey(e)
  notifyError(key && te(key) ? t(key) : t('admin_action_failed'))
}

async function reload() {
  loading.value = true
  try {
    const {data} = await listIngredients(filters, page.value - 1, size.value)
    rows.value = data.items
    total.value = data.count
  } catch (e) { fail(e) } finally { loading.value = false }
}

let timer: any
const debouncedReload = () => { clearTimeout(timer); timer = setTimeout(reset, 350) }
const reset = () => { page.value = 1; reload() }

const open = (i: any) => { selected.value = i; dialog.value = true }

const confirmDelete = async () => {
  const i = selected.value; dialog.value = false
  try {
    await deleteIngredient(i.id)
    notifyOk(t('admin_action_done'))
    await reload()
  } catch (e) { fail(e) }
}

onMounted(reload)
</script>

<style scoped>
.page { max-width: 1200px; }
.filters { gap: 8px; flex-wrap: wrap; }
</style>
