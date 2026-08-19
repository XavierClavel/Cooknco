<template>
  <div class="page">
    <div class="panel">
      <div class="panel-head filters">
        <div class="search">
          <ui-icon name="search" :size="15" />
          <input v-model="filters.query" class="input" style="width:250px"
                 :placeholder="$t('admin_search_recipe')" @input="debouncedReload" />
        </div>
        <select v-model="filters.hidden" class="select" style="width:150px" @change="reset">
          <option :value="null">{{ $t('admin_all_visibility') }}</option>
          <option :value="false">{{ $t('admin_visible') }}</option>
          <option :value="true">{{ $t('admin_hidden') }}</option>
        </select>
        <select v-model="filters.sort" class="select" style="width:160px" @change="reset">
          <option value="DATE_DESCENDING">{{ $t('admin_newest_first') }}</option>
          <option value="DATE_ASCENDING">{{ $t('admin_oldest_first') }}</option>
          <option value="NAME_ASCENDING">{{ $t('admin_title_az') }}</option>
        </select>
        <label class="check">
          <input type="checkbox" v-model="filters.reported" @change="reset" />
          {{ $t('admin_reported_only') }}
        </label>
        <span class="spacer"></span>
        <button class="btn icon" :title="$t('admin_refresh')" @click="reload"><ui-icon name="refresh" /></button>
      </div>

      <div class="progress" v-if="loading"><i></i></div>

      <div class="table-wrap">
        <table class="grid">
          <thead>
            <tr>
              <th>{{ $t('recipe') }}</th>
              <th>{{ $t('admin_author') }}</th>
              <th>{{ $t('admin_state') }}</th>
              <th class="right">{{ $t('likes') }}</th>
              <th class="right">{{ $t('cookbooks') }}</th>
              <th class="right">{{ $t('admin_reports') }}</th>
              <th>{{ $t('admin_created') }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in rows" :key="r.id">
              <td>
                <button class="what" :title="$t('admin_open_recipe')" @click="openDetail(r)">
                  <ui-thumb :src="recipeThumbnailUrl(r.id, r.version)" :width="44" :height="33" />
                  <span class="title truncate">{{ r.title }}</span>
                </button>
              </td>
              <td class="muted">{{ r.owner?.username ?? $t('admin_deleted_author') }}</td>
              <td>
                <span class="badge" :class="r.isHidden ? 'danger' : 'ok'" :title="r.hiddenReason || ''">
                  <i class="dot"></i>{{ r.isHidden ? $t('admin_hidden') : $t('admin_visible') }}
                </span>
                <span v-if="r.taggedForDeletion" class="badge warn" style="margin-left:4px">
                  {{ $t('admin_pending_deletion') }}
                </span>
              </td>
              <td class="right tnum">{{ r.likesCount }}</td>
              <td class="right tnum">{{ r.cookbooksCount }}</td>
              <td class="right tnum">
                <span v-if="r.pendingReportsCount" class="badge warn">{{ r.pendingReportsCount }}</span>
                <span v-else class="subtle">0</span>
              </td>
              <td class="small muted nowrap">{{ fmtDate(r.creationDate) }}</td>
              <td class="actions">
                <span class="row-actions">
                  <button class="btn icon" :title="$t('admin_open_recipe')"
                          @click="openDetail(r)"><ui-icon name="search" /></button>
                  <button v-if="r.isHidden" class="btn icon" :title="$t('admin_unhide')"
                          @click="run(r, () => unhideRecipe(r.id))"><ui-icon name="eye" /></button>
                  <button v-else class="btn icon" :title="$t('admin_hide')"
                          @click="open(r, 'hide')"><ui-icon name="eyeOff" /></button>
                  <button class="btn icon danger-hover" :title="$t('delete')"
                          @click="open(r, 'delete')"><ui-icon name="trash" /></button>
                </span>
              </td>
            </tr>
          </tbody>
        </table>
        <ui-empty v-if="!loading && !rows.length" icon="book"
                  :title="$t('admin_no_result')" :hint="$t('admin_no_result_hint')" />
      </div>

      <div class="panel-foot">
        <ui-pager :page="page" :size="size" :total="total" @update:page="p => { page = p; reload() }" />
      </div>
    </div>

    <!-- hide -->
    <ui-modal v-model="dialog.hide" :title="`${$t('admin_hide')} — ${selected?.title ?? ''}`">
      <div class="col" style="gap:12px">
        <label class="field">
          <span>{{ $t('admin_reason') }}</span>
          <textarea v-model="reason" class="input" rows="3"></textarea>
        </label>
        <p class="small muted">{{ $t('admin_hide_hint') }}</p>
      </div>
      <template #actions>
        <button class="btn" @click="dialog.hide = false">{{ $t('cancel') }}</button>
        <button class="btn primary" @click="confirmHide">{{ $t('admin_hide') }}</button>
      </template>
    </ui-modal>

    <!-- delete -->
    <ui-modal v-model="dialog.delete" :title="$t('admin_delete_recipe')">
      <div class="col" style="gap:12px">
        <p>{{ $t('admin_delete_recipe_confirm', {title: selected?.title}) }}</p>
        <div class="alert danger"><ui-icon name="alert" /><span>{{ $t('admin_irreversible') }}</span></div>
      </div>
      <template #actions>
        <button class="btn" @click="dialog.delete = false">{{ $t('cancel') }}</button>
        <button class="btn danger" @click="confirmDelete">{{ $t('delete') }}</button>
      </template>
    </ui-modal>

    <!-- read the content that was reported, without leaving the table -->
    <ui-modal v-model="dialog.detail" :title="detail?.title ?? $t('recipe')" :width="640">
      <div v-if="!detail" class="progress"><i></i></div>
      <div v-else class="detail">
        <img v-if="detail.version" class="hero" alt=""
             :src="recipeImageUrl(detail.id, detail.version)" />
        <p v-if="detail.description">{{ detail.description }}</p>
        <div v-if="detail.ingredients?.length">
          <h4>{{ $t('ingredients') }}</h4>
          <!-- One list for both kinds; a row with no id is a name the author typed by hand -->
          <ul>
            <!-- Flex, so the parts keep a gap: Vue condenses the whitespace between them away -->
            <li v-for="(i, n) in detail.ingredients" :key="n" class="row-wrap" style="gap:5px">
              <span>{{ i.name }}</span>
              <span v-if="formatAmount(i.amount, i.unit)" class="muted">— {{ formatAmount(i.amount, i.unit) }}</span>
              <span v-if="i.complement" class="subtle">({{ i.complement }})</span>
              <span v-if="!i.id" class="badge">{{ $t('admin_custom') }}</span>
            </li>
          </ul>
        </div>
        <div v-if="detail.steps?.length">
          <h4>{{ $t('steps') }}</h4>
          <ol><li v-for="(s, n) in detail.steps" :key="n">{{ s }}</li></ol>
        </div>
        <div v-if="detail.tips">
          <h4>{{ $t('tips') }}</h4>
          <p>{{ detail.tips }}</p>
        </div>
      </div>
      <template #actions>
        <button class="btn" @click="dialog.detail = false">{{ $t('close') }}</button>
      </template>
    </ui-modal>
  </div>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {
  deleteRecipe, errorKey, getRecipeDetail, hideRecipe, listRecipes, unhideRecipe,
} from '../lib/api'
import {fmtDate} from '../lib/format'
import {formatAmount} from '../lib/ingredients'
import {recipeImageUrl, recipeThumbnailUrl} from '../lib/images'
import {notifyError, notifyOk} from '../lib/toast'
import UiIcon from '../components/UiIcon.vue'
import UiModal from '../components/UiModal.vue'
import UiPager from '../components/UiPager.vue'
import UiEmpty from '../components/UiEmpty.vue'
import UiThumb from '../components/UiThumb.vue'

const {t, te, locale} = useI18n()

const rows = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(25)
const total = ref(0)
const filters = reactive<any>({query: '', hidden: null, reported: false, sort: 'DATE_DESCENDING'})

const selected = ref<any>(null)
const detail = ref<any>(null)
const dialog = reactive({hide: false, delete: false, detail: false})
const reason = ref('')

function fail(e: any) {
  const key = errorKey(e)
  notifyError(key && te(key) ? t(key) : t('admin_action_failed'))
}

async function reload() {
  loading.value = true
  try {
    const {data} = await listRecipes(filters, page.value - 1, size.value)
    rows.value = data.items
    total.value = data.count
  } catch (e) { fail(e) } finally { loading.value = false }
}

let timer: any
const debouncedReload = () => { clearTimeout(timer); timer = setTimeout(reset, 350) }
const reset = () => { page.value = 1; reload() }

async function run(recipe: any, action: () => Promise<any>) {
  try {
    Object.assign(recipe, (await action()).data)
    notifyOk(t('admin_action_done'))
  } catch (e) { fail(e) }
}

function open(recipe: any, which: 'hide' | 'delete') {
  selected.value = recipe
  reason.value = ''
  dialog[which] = true
}

async function openDetail(recipe: any) {
  detail.value = null
  dialog.detail = true
  try {
    detail.value = (await getRecipeDetail(recipe.id, locale.value)).data
  } catch (e) { fail(e); dialog.detail = false }
}

const confirmHide = () => {
  const r = selected.value; dialog.hide = false
  run(r, () => hideRecipe(r.id, reason.value))
}
const confirmDelete = async () => {
  const r = selected.value; dialog.delete = false
  try {
    await deleteRecipe(r.id)
    notifyOk(t('admin_action_done'))
    await reload()
  } catch (e) { fail(e) }
}

onMounted(reload)
</script>

<style scoped>
.page { max-width: 1400px; }
.filters { gap: 8px; flex-wrap: wrap; }
.what {
  display: flex; align-items: center; gap: 10px; min-width: 0; max-width: 360px;
  border: 0; background: none; padding: 0; font: inherit;
  cursor: pointer; text-align: left;
}
.what .title { color: var(--c-accent); }
.what:hover .title { text-decoration: underline; }
.detail { display: flex; flex-direction: column; gap: 14px; }
.detail .hero {
  width: 100%; max-height: 260px; object-fit: cover;
  border-radius: var(--radius); border: 1px solid var(--c-border);
}
.detail h4 { font-size: 11px; text-transform: uppercase; letter-spacing: .05em; color: var(--c-text-subtle); margin-bottom: 5px; }
.detail ul, .detail ol { margin: 0; padding-left: 18px; display: flex; flex-direction: column; gap: 3px; }
</style>
