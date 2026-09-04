<template>
  <div class="page col" style="gap:16px">
    <div class="panel">
      <div class="panel-head filters">
        <div class="search">
          <ui-icon name="search" :size="15" />
          <input v-model="filters.query" class="input" style="width:250px"
                 :placeholder="$t('admin_search_ingredient')" @input="debouncedReload" />
        </div>
        <select v-model="filters.type" class="select" style="width:180px" @change="reset">
          <option :value="null">{{ $t('admin_all_types') }}</option>
          <option v-for="ty in INGREDIENT_TYPES" :key="ty" :value="ty">{{ ty }}</option>
        </select>
        <span class="spacer"></span>
        <button class="btn primary" @click="openCreate">
          <ui-icon name="plus" :size="15" />{{ $t('admin_new_ingredient') }}
        </button>
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
              <th>{{ $t('admin_measurable_as') }}</th>
              <th>{{ $t('defaultUnit') }}</th>
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
              <td>
                <!-- NONE is always allowed and says nothing, so only the real families show -->
                <span class="row-wrap" style="gap:4px">
                  <span v-for="ty in measurable(i)" :key="ty" class="badge info">{{ $t(`admin_measure_${ty.toLowerCase()}`) }}</span>
                  <span v-if="!measurable(i).length" class="badge warn" :title="$t('admin_no_measurement_hint')">
                    <ui-icon name="alert" :size="12" />{{ $t('admin_no_measurement') }}
                  </span>
                </span>
              </td>
              <td class="mono small">{{ i.defaultUnit ? unitLabel(i.defaultUnit) : '—' }}</td>
              <td class="right tnum">
                <span v-if="i.recipesCount" class="badge accent">{{ i.recipesCount }}</span>
                <span v-else class="subtle">0</span>
              </td>
              <td class="actions">
                <span class="row-actions">
                  <button class="btn icon" :title="$t('edit')" @click="openEdit(i)"><ui-icon name="edit" /></button>
                  <button class="btn icon danger-hover" :title="$t('delete')"
                          @click="openDelete(i)"><ui-icon name="trash" /></button>
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

    <!-- What users typed by hand, i.e. what the catalogue is still missing -->
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">{{ $t('custom_ingredients_in_use') }}</span>
        <span class="spacer"></span>
        <button class="btn icon" :title="$t('admin_refresh')" @click="reloadUsage"><ui-icon name="refresh" /></button>
      </div>

      <div class="progress" v-if="usageLoading"><i></i></div>

      <div class="table-wrap">
        <table class="grid">
          <thead>
            <tr>
              <th>{{ $t('ingredient') }}</th>
              <th class="right">{{ $t('custom_ingredient_uses') }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in usage" :key="c.name">
              <td>{{ c.name }}</td>
              <td class="right tnum">{{ c.uses }}</td>
              <td class="actions">
                <button class="btn sm" @click="openConvert(c)">
                  <ui-icon name="arrowRight" :size="14" />{{ $t('convert') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <ui-empty v-if="!usageLoading && !usage.length" icon="leaf"
                  :title="$t('no_custom_ingredient_to_display')" :hint="$t('admin_custom_usage_hint')" />
      </div>

      <div class="panel-foot">
        <ui-pager :page="usagePage" :size="usageSize" :total="usageTotal"
                  @update:page="p => { usagePage = p; reloadUsage() }" />
      </div>
    </div>

    <!-- create / edit / convert all use the same form; convert adds the absorb shortcut -->
    <ui-modal v-model="editor" :title="editorTitle" :width="700">
      <div class="col" style="gap:14px">
        <div v-if="absorbName" class="col" style="gap:8px">
          <div class="alert info">
            <ui-icon name="info" />
            <span>{{ $t('admin_convert_hint', {name: absorbName}) }}</span>
          </div>
          <label class="field">
            <span>{{ $t('absorb_into_existing') }}</span>
            <div class="search">
              <ui-icon name="search" :size="15" />
              <input v-model="absorbQuery" class="input" :placeholder="$t('admin_search_ingredient')"
                     @input="debouncedCandidates" />
            </div>
          </label>
          <ul v-if="candidates.length" class="candidates">
            <li v-for="c in candidates" :key="c.id">
              <button class="btn sm" @click="absorbInto(c)">
                <ui-icon name="arrowRight" :size="14" />{{ c.name?.EN || c.name?.FR || `#${c.id}` }}
              </button>
              <span class="subtle small">{{ c.type }}</span>
            </li>
          </ul>
        </div>

        <div class="grid2">
          <label class="field">
            <span>{{ $t('admin_name_en') }}</span>
            <input v-model="form.name.EN" class="input" />
          </label>
          <label class="field">
            <span>{{ $t('admin_name_fr') }}</span>
            <input v-model="form.name.FR" class="input" />
          </label>
          <label class="field">
            <span>{{ $t('type') }}</span>
            <select v-model="form.type" class="select" @change="onTypeChange">
              <option v-for="ty in INGREDIENT_TYPES" :key="ty" :value="ty">{{ ty }}</option>
            </select>
          </label>
        </div>

        <div class="col" style="gap:8px">
          <span class="panel-title">{{ $t('admin_measurements') }}</span>
          <p class="small muted">{{ $t('admin_measurements_hint') }}</p>
          <div class="grid2">
            <label class="field">
              <span>{{ $t('gramsPerUnit') }}</span>
              <input v-model="form.gramsPerUnit" class="input" type="number" min="0" step="1"
                     :placeholder="$t('admin_conversion_unknown')" />
            </label>
            <label class="field">
              <span>{{ $t('gramsPerMilliliter') }}</span>
              <input v-model="form.gramsPerMilliliter" class="input" type="number" min="0" step="0.01"
                     :placeholder="$t('admin_conversion_unknown')" />
            </label>
            <label class="check" style="align-self:end;height:32px">
              <input v-model="form.measurableByWeight" type="checkbox" />
              <span>{{ $t('measurableByWeight') }}</span>
            </label>
            <label class="field">
              <span>{{ $t('defaultUnit') }}</span>
              <select v-model="form.defaultUnit" class="select">
                <option :value="null">—</option>
                <option v-for="u in allowedUnits" :key="u.name" :value="u.name">{{ unitLabel(u.name) }}</option>
              </select>
            </label>
          </div>
        </div>

        <div class="col" style="gap:8px">
          <span class="panel-title">{{ $t('admin_nutrition_per_100g') }}</span>
          <div class="grid3">
            <label v-for="f in NUTRITION_FIELDS" :key="f.key" class="field">
              <span>{{ $t(f.key) }}</span>
              <input v-model="form[f.key]" class="input" type="number" min="0" :step="f.step" />
            </label>
          </div>
        </div>
      </div>
      <template #actions>
        <button class="btn" @click="editor = false">{{ $t('cancel') }}</button>
        <button class="btn primary" :disabled="saving" @click="save">{{ $t('save') }}</button>
      </template>
    </ui-modal>

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
import {
  absorbCustomIngredient, createIngredient, deleteIngredient, errorKey, getCustomIngredientUsage,
  getIngredient, listIngredients, listUnits, updateIngredient,
} from '../lib/api'
import {
  allowedTypesOf, emptyForm, FALLBACK_UNITS, formOf, INGREDIENT_TYPES, NUTRITION_FIELDS, payloadOf,
  typeDefaults, unitLabel, type IngredientForm,
} from '../lib/ingredients'
import {notifyError, notifyInfo, notifyOk} from '../lib/toast'
import UiIcon from '../components/UiIcon.vue'
import UiModal from '../components/UiModal.vue'
import UiPager from '../components/UiPager.vue'
import UiEmpty from '../components/UiEmpty.vue'

const {t, te} = useI18n()

const rows = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(25)
const total = ref(0)
const filters = reactive<any>({query: '', type: null})

const usage = ref<any[]>([])
const usageLoading = ref(false)
const usagePage = ref(1)
const usageSize = ref(20)
const usageTotal = ref(0)

const units = ref<{name: string; type: string}[]>(FALLBACK_UNITS)

const editor = ref(false)
const saving = ref(false)
const form = ref<IngredientForm>(emptyForm())
const editorTitle = ref('')
/** Set only in convert mode: the free-text name whose rows the saved ingredient takes over. */
const absorbName = ref<string | null>(null)
const absorbQuery = ref('')
const candidates = ref<any[]>([])

const selected = ref<any>(null)
const dialog = ref(false)
const selectedName = computed(() => selected.value?.name?.EN || selected.value?.name?.FR || '')

const allowedUnits = computed(() => {
  const allowed = allowedTypesOf(form.value)
  // NONE is dropped: the empty option above the list already means "no default unit"
  return units.value.filter(u => u.type !== 'NONE' && allowed.includes(u.type))
})

/** The families that say something, i.e. everything but the always-allowed NONE. */
const measurable = (row: any) => (row.allowedTypes ?? []).filter((ty: string) => ty !== 'NONE')

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

async function reloadUsage() {
  usageLoading.value = true
  try {
    const {data} = await getCustomIngredientUsage(usagePage.value - 1, usageSize.value)
    usage.value = data.items
    usageTotal.value = data.count
  } catch (e) { fail(e) } finally { usageLoading.value = false }
}

let timer: any
const debouncedReload = () => { clearTimeout(timer); timer = setTimeout(reset, 350) }
const reset = () => { page.value = 1; reload() }

// ------------------------------------------------------------------- editor

function openForm(title: string, next: IngredientForm) {
  form.value = next
  editorTitle.value = title
  absorbName.value = null
  absorbQuery.value = ''
  candidates.value = []
  editor.value = true
}

const openCreate = () => openForm(t('admin_new_ingredient'), emptyForm())

async function openEdit(row: any) {
  try {
    // The catalogue row carries only what the table shows, so the form loads the whole thing
    const {data} = await getIngredient(row.id)
    openForm(`${t('edit')} — ${row.name?.EN || row.name?.FR || `#${row.id}`}`, formOf(data))
  } catch (e) { fail(e) }
}

function openConvert(custom: any) {
  const next = emptyForm()
  next.name = {EN: custom.name, FR: custom.name}
  openForm(`${t('convert')} — ${custom.name}`, next)
  absorbName.value = custom.name
  absorbQuery.value = custom.name
  loadCandidates()
}

/** A new ingredient inherits the conversions that hold for its whole type; an edit never does. */
function onTypeChange() {
  if (form.value.id !== null) return
  Object.assign(form.value, typeDefaults(form.value.type))
}

async function loadCandidates() {
  const query = absorbQuery.value.trim()
  if (!query) { candidates.value = []; return }
  try {
    const {data} = await listIngredients({query}, 0, 6)
    candidates.value = data.items
  } catch (e) { fail(e) }
}

let candidateTimer: any
const debouncedCandidates = () => { clearTimeout(candidateTimer); candidateTimer = setTimeout(loadCandidates, 350) }

/**
 * Rows the ingredient cannot be measured in stay free text, so say so rather than letting the
 * name look absorbed when part of it was left behind.
 */
function reportAbsorbed(result: {convertedRows: number; skippedRows: number}) {
  notifyOk(t('admin_absorbed', {count: result.convertedRows}))
  if (result.skippedRows) notifyInfo(t('admin_absorb_skipped', {count: result.skippedRows}))
}

/** Absorbing into an existing ingredient replaces saving: nothing new is created. */
async function absorbInto(candidate: any) {
  try {
    const {data} = await absorbCustomIngredient(candidate.id, absorbName.value!)
    reportAbsorbed(data)
    editor.value = false
    await Promise.all([reload(), reloadUsage()])
  } catch (e) { fail(e) }
}

async function save() {
  saving.value = true
  try {
    const body = payloadOf(form.value)
    let id = form.value.id
    if (id === null) {
      id = (await createIngredient(body)).data.id
    } else {
      await updateIngredient(id, body)
    }
    const absorbing = absorbName.value
    if (absorbing) {
      reportAbsorbed((await absorbCustomIngredient(id!, absorbing)).data)
    } else {
      notifyOk(t('admin_action_done'))
    }
    editor.value = false
    await reload()
    if (absorbing) await reloadUsage()
  } catch (e) { fail(e) } finally { saving.value = false }
}

// ------------------------------------------------------------------- delete

const openDelete = (i: any) => { selected.value = i; dialog.value = true }

const confirmDelete = async () => {
  const i = selected.value; dialog.value = false
  try {
    await deleteIngredient(i.id)
    notifyOk(t('admin_action_done'))
    await reload()
  } catch (e) { fail(e) }
}

onMounted(async () => {
  await Promise.all([reload(), reloadUsage()])
  // Falls back to the bundled list, so the form works even if this never answers
  try { units.value = (await listUnits()).data } catch { /* keep the fallback */ }
})
</script>

<style scoped>
.page { max-width: 1200px; }
.filters { gap: 8px; flex-wrap: wrap; }
.grid2 { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px 12px; }
.grid3 { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px 12px; }
.candidates { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
.candidates li { display: flex; align-items: center; gap: 8px; }
@media (max-width: 640px) {
  .grid2, .grid3 { grid-template-columns: minmax(0, 1fr); }
}
</style>
