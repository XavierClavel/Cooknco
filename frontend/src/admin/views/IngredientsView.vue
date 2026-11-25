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
        <label class="check">
          <input v-model="filters.mostUsed" type="checkbox" @change="reset" />
          {{ $t('admin_most_used_first') }}
        </label>
        <label class="check">
          <input v-model="filters.missingCiqual" type="checkbox" @change="reset" />
          {{ $t('admin_missing_ciqual') }}
        </label>
        <label class="check">
          <input v-model="filters.missingPrice" type="checkbox" @change="reset" />
          {{ $t('admin_missing_price') }}
        </label>
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
              <th>{{ $t('admin_sources') }}</th>
              <th class="right">{{ $t('admin_price') }}</th>
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
              <td class="right tnum">{{ i.calories ?? '—' }}</td>
              <td>
                <span class="row-wrap" style="gap:4px">
                  <span :class="['badge', i.ciqualCode != null ? 'ok' : 'warn']" :title="ciqualTitle(i)"
                        :aria-label="ciqualTitle(i)">
                    <ui-icon name="leaf" :size="12" />
                  </span>
                  <span :class="['badge', PRICE_BADGE[i.priceStatus]]" :title="priceTitle(i)" :aria-label="priceTitle(i)">
                    <ui-icon name="euro" :size="12" />
                  </span>
                </span>
              </td>
              <!-- A price the ingredient cannot convert to is the category's, not this ingredient's -->
              <td class="right tnum small" :class="{unusable: i.priceStatus !== 'PRICED'}" :title="i.price?.category">
                {{ i.price ? formatPrice(i.price) : '—' }}
              </td>
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
          <span class="panel-title">{{ $t('admin_ciqual_title') }}</span>
          <p class="small muted">{{ $t('admin_ciqual_hint') }}</p>
          <div v-if="form.ciqualCode !== null" class="alert info">
            <ui-icon name="leaf" />
            <span>
              Ciqual{{ form.ciqualEdition ? ` ${form.ciqualEdition}` : '' }} —
              {{ form.ciqualName?.FR || form.ciqualName?.EN || '' }}
              <span class="mono small">#{{ form.ciqualCode }}</span>
            </span>
            <span class="spacer"></span>
            <button class="btn sm" @click="unlink">
              <ui-icon name="undo" :size="14" />{{ $t('admin_ciqual_unlink') }}
            </button>
          </div>
          <template v-else>
            <div class="search">
              <ui-icon name="search" :size="15" />
              <input v-model="ciqualQuery" class="input" :placeholder="$t('admin_ciqual_search')"
                     @input="debouncedCiqual" />
            </div>
            <ul v-if="ciqualResults.length" class="candidates">
              <li v-for="c in ciqualResults" :key="c.code">
                <button class="btn sm" @click="pickCiqual(c)">
                  <ui-icon name="arrowRight" :size="14" />{{ $t('admin_ciqual_use') }}
                </button>
                <span>{{ c.name?.FR || c.name?.EN }}</span>
                <span class="subtle small">{{ c.group?.FR }} · {{ c.calories ?? '—' }} kcal</span>
              </li>
            </ul>
            <p v-else-if="ciqualSearched" class="small muted">{{ $t('admin_ciqual_no_result') }}</p>
          </template>
        </div>

        <div class="col" style="gap:8px">
          <span class="panel-title">{{ $t('admin_price_title') }}</span>
          <p class="small muted">{{ $t('admin_price_hint') }}</p>
          <div v-if="form.openPricesCategory" :class="['alert', form.price ? 'info' : 'warn']">
            <span v-if="form.price">
              {{ form.price.name?.FR || form.price.name?.EN }} — {{ formatPrice(form.price) }}
              <span class="subtle small">{{ $t('admin_price_count', {count: form.price.prices}) }}</span>
              <span class="mono small">{{ form.price.category }}</span>
            </span>
            <!-- Kept on save: a later refresh of the table may list it again -->
            <span v-else>{{ $t('admin_price_dropped', {category: form.openPricesCategory}) }}</span>
            <span class="spacer"></span>
            <button class="btn sm" @click="unprice">
              <ui-icon name="undo" :size="14" />{{ $t('admin_ciqual_unlink') }}
            </button>
          </div>
          <template v-else>
            <div class="search">
              <ui-icon name="search" :size="15" />
              <input v-model="priceQuery" class="input" :placeholder="$t('admin_price_search')"
                     @input="debouncedPrices" />
            </div>
            <ul v-if="priceResults.length" class="candidates">
              <li v-for="p in priceResults" :key="p.category">
                <button class="btn sm" @click="pickPrice(p)">
                  <ui-icon name="arrowRight" :size="14" />{{ $t('admin_ciqual_use') }}
                </button>
                <span>{{ p.name?.FR || p.name?.EN }}</span>
                <span class="subtle small">{{ formatPrice(p) }} · {{ $t('admin_price_count', {count: p.prices}) }}</span>
              </li>
            </ul>
            <p v-else-if="priceSearched" class="small muted">{{ $t('admin_price_no_result') }}</p>
          </template>
        </div>

        <div class="col" style="gap:8px">
          <span class="panel-title">{{ $t('admin_nutrition_per_100g') }}</span>
          <div class="grid3">
            <label v-for="f in NUTRITION_FIELDS" :key="f.key" class="field">
              <span>{{ $t(f.key) }} ({{ f.unit }})</span>
              <!-- A linked ingredient holds Ciqual's figures and no others: unlink it to type -->
              <input v-model="form[f.key]" class="input" type="number" min="0" :step="f.step"
                     :disabled="form.ciqualCode !== null" :placeholder="$t('admin_conversion_unknown')" />
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
  getIngredient, listIngredients, listUnits, searchCiqual, searchOpenPrices, updateIngredient,
} from '../lib/api'
import {
  allowedTypesOf, emptyForm, FALLBACK_UNITS, formatPrice, formOf, INGREDIENT_TYPES, linkCiqual, NUTRITION_FIELDS,
  payloadOf, typeDefaults, unitLabel, unlinkCiqual, type IngredientForm,
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
const filters = reactive<any>({query: '', type: null, mostUsed: false, missingCiqual: false, missingPrice: false})

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
const ciqualQuery = ref('')
const ciqualResults = ref<any[]>([])
/** Set once an answer for the query typed has arrived, so that an empty list can say so. */
const ciqualSearched = ref(false)
const priceQuery = ref('')
const priceResults = ref<any[]>([])
const priceSearched = ref(false)

const selected = ref<any>(null)
const dialog = ref(false)
const selectedName = computed(() => selected.value?.name?.EN || selected.value?.name?.FR || '')

const allowedUnits = computed(() => {
  const allowed = allowedTypesOf(form.value)
  // NONE is dropped: the empty option above the list already means "no default unit"
  return units.value.filter(u => u.type !== 'NONE' && allowed.includes(u.type))
})

const ciqualTitle = (row: any) =>
  row.ciqualCode != null ? t('admin_ciqual_linked', {code: row.ciqualCode}) : t('admin_ciqual_missing')

/** A link that prices nothing because of a refresh or a conversion is a fault, not a gap. */
const PRICE_BADGE: Record<string, string> = {PRICED: 'ok', NONE: 'warn', NOT_IN_TABLE: 'danger', NOT_CONVERTIBLE: 'danger'}

const priceTitle = (row: any) => row.priceStatus === 'PRICED'
  ? `${row.price.name?.FR || row.price.name?.EN} — ${formatPrice(row.price)}`
  : t(`admin_price_status_${row.priceStatus}`, {category: row.openPricesCategory})

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
  // An ingredient whose figures are typed in is offered the Ciqual foods its name finds
  ciqualResults.value = []
  ciqualQuery.value = next.ciqualCode === null ? (next.name.FR || next.name.EN) : ''
  loadCiqual()
  // Likewise an unpriced one is offered the categories its name finds
  priceResults.value = []
  priceQuery.value = next.openPricesCategory === null ? (next.name.FR || next.name.EN) : ''
  loadPrices()
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

async function loadCiqual() {
  const query = ciqualQuery.value.trim()
  ciqualSearched.value = false
  if (!query) { ciqualResults.value = []; return }
  try {
    const {data} = await searchCiqual(query, 8)
    // An answer to what was typed before the last keystroke is not one to show
    if (query !== ciqualQuery.value.trim()) return
    ciqualResults.value = data
    ciqualSearched.value = true
  } catch (e) { fail(e) }
}

let ciqualTimer: any
const debouncedCiqual = () => { clearTimeout(ciqualTimer); ciqualTimer = setTimeout(loadCiqual, 350) }

function pickCiqual(food: any) {
  linkCiqual(form.value, food)
  ciqualResults.value = []
}

async function loadPrices() {
  const query = priceQuery.value.trim()
  priceSearched.value = false
  if (!query) { priceResults.value = []; return }
  try {
    const {data} = await searchOpenPrices(query, 8)
    if (query !== priceQuery.value.trim()) return
    priceResults.value = data
    priceSearched.value = true
  } catch (e) { fail(e) }
}

let priceTimer: any
const debouncedPrices = () => { clearTimeout(priceTimer); priceTimer = setTimeout(loadPrices, 350) }

function pickPrice(price: any) {
  form.value.openPricesCategory = price.category
  form.value.price = price
  priceResults.value = []
}

function unprice() {
  form.value.openPricesCategory = null
  form.value.price = null
  priceQuery.value = form.value.name.FR || form.value.name.EN
  loadPrices()
}

/** The figures stay as the link left them, to be edited; the foods the name finds come back. */
function unlink() {
  unlinkCiqual(form.value)
  ciqualQuery.value = form.value.name.FR || form.value.name.EN
  loadCiqual()
}

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
.unusable { text-decoration: line-through; opacity: .55; }
.candidates { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
.candidates li { display: flex; align-items: center; gap: 8px; }
@media (max-width: 640px) {
  .grid2, .grid3 { grid-template-columns: minmax(0, 1fr); }
}
</style>
