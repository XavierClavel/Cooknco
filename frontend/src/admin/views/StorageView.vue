<template>
  <div class="page">
    <!--
      Images are the only user data kept outside the database, so nothing reconciles the
      two on its own. The three panels read top-down: how much space is at stake, which
      bucket holds it, and which files exactly.
    -->
    <section class="tiles">
      <div class="tile panel">
        <span class="label">{{ $t('admin_storage_files') }}</span>
        <span class="value tnum">{{ loading ? '—' : fmtNumber(overview.totalFiles) }}</span>
        <span class="sub small subtle">{{ fmtBytes(overview.totalBytes) }}</span>
      </div>

      <div class="tile panel" :class="{flag: overview.reclaimableFiles}">
        <span class="label">{{ $t('admin_storage_reclaimable') }}</span>
        <span class="value tnum">{{ loading ? '—' : fmtBytes(overview.reclaimableBytes) }}</span>
        <span class="sub small subtle">
          {{ $t('admin_storage_files_count', {count: overview.reclaimableFiles ?? 0}) }}
        </span>
      </div>

      <div class="tile panel" :class="{flag: overview.missingImages}">
        <span class="label">{{ $t('admin_storage_missing') }}</span>
        <span class="value tnum">{{ loading ? '—' : fmtNumber(overview.missingImages) }}</span>
        <span class="sub small subtle">{{ $t('admin_storage_missing_hint') }}</span>
      </div>

      <div class="tile panel">
        <span class="label">{{ $t('admin_storage_volume') }}</span>
        <template v-if="volume">
          <span class="value tnum">{{ fmtBytes(volume.used) }}</span>
          <span class="sub small subtle">
            {{ $t('admin_storage_volume_of', {total: fmtBytes(volume.total), percent: volume.percent}) }}
          </span>
          <!-- The volume is the hard limit the reclaimable figure is measured against -->
          <div class="meter" :class="{tight: volume.percent >= 85}">
            <i :style="{width: `${volume.percent}%`}"></i>
          </div>
        </template>
        <template v-else>
          <span class="value tnum">—</span>
          <span class="sub small subtle">{{ $t('admin_storage_not_mounted') }}</span>
        </template>
      </div>
    </section>

    <!-- ------------------------------------------------------------ buckets -->
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">{{ $t('admin_storage_buckets') }}</span>
        <span class="spacer"></span>
        <button class="btn sm" :disabled="!overview.reclaimableFiles" @click="openCleanup">
          <ui-icon name="trash" :size="14" />
          {{ $t('admin_storage_sweep') }}
        </button>
        <button class="btn icon" :title="$t('admin_refresh')" @click="reloadAll">
          <ui-icon name="refresh" />
        </button>
      </div>

      <div class="progress" v-if="loading"><i></i></div>

      <div class="table-wrap">
        <table class="grid">
          <thead>
            <tr>
              <th>{{ $t('admin_storage_bucket') }}</th>
              <th class="right">{{ $t('admin_storage_files') }}</th>
              <th class="right">{{ $t('admin_storage_size') }}</th>
              <th v-for="s in statusColumns" :key="s" class="right">{{ $t(STATUS_LABELS[s]) }}</th>
              <th class="right">{{ $t('admin_storage_largest') }}</th>
              <th>{{ $t('admin_storage_last_write') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="b in overview.buckets ?? []" :key="b.bucket">
              <td>
                <button class="linkish" @click="focusBucket(b.bucket)">{{ $t(BUCKET_LABELS[b.bucket]) }}</button>
                <div class="small subtle mono">{{ b.path }}</div>
              </td>
              <td class="right tnum">{{ fmtNumber(b.files) }}</td>
              <td class="right tnum">{{ fmtBytes(b.bytes) }}</td>
              <td v-for="s in statusColumns" :key="s" class="right tnum">
                <button v-if="b.countByStatus?.[s]" class="cell-badge badge" :class="STATUS_TONES[s]"
                        @click="focusBucket(b.bucket, s)">
                  {{ b.countByStatus[s] }}
                </button>
                <span v-else class="subtle">0</span>
              </td>
              <td class="right tnum small muted">{{ fmtBytes(b.largestFileBytes) }}</td>
              <td class="small muted nowrap">{{ fmtAgo(b.lastModified) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ------------------------------------------------------------- images -->
    <div class="panel">
      <div class="panel-head filters">
        <div class="search">
          <ui-icon name="search" :size="15" />
          <input v-model="filters.query" class="input" style="width:230px"
                 :placeholder="$t('admin_storage_search')" @input="debouncedReload" />
        </div>
        <select v-model="filters.bucket" class="select" style="width:180px" @change="reset">
          <option :value="null">{{ $t('admin_storage_all_buckets') }}</option>
          <option v-for="b in BUCKETS" :key="b" :value="b">{{ $t(BUCKET_LABELS[b]) }}</option>
        </select>
        <select v-model="filters.status" class="select" style="width:170px" @change="reset">
          <option :value="null">{{ $t('admin_all_statuses') }}</option>
          <option v-for="s in STATUSES" :key="s" :value="s">{{ $t(STATUS_LABELS[s]) }}</option>
        </select>
        <select v-model="filters.sort" class="select" style="width:170px" @change="reset">
          <option value="SIZE_DESCENDING">{{ $t('admin_storage_largest_first') }}</option>
          <option value="SIZE_ASCENDING">{{ $t('admin_storage_smallest_first') }}</option>
          <option value="DATE_DESCENDING">{{ $t('admin_newest_first') }}</option>
          <option value="DATE_ASCENDING">{{ $t('admin_oldest_first') }}</option>
          <option value="NAME_ASCENDING">{{ $t('admin_storage_by_owner') }}</option>
        </select>
        <span class="spacer"></span>
        <button class="btn icon" :title="$t('admin_refresh')" @click="reload"><ui-icon name="refresh" /></button>
      </div>

      <div class="progress" v-if="imagesLoading"><i></i></div>

      <div class="table-wrap">
        <table class="grid">
          <thead>
            <tr>
              <th style="width:1%"></th>
              <th>{{ $t('admin_storage_file') }}</th>
              <th>{{ $t('admin_storage_bucket') }}</th>
              <th>{{ $t('admin_storage_owner') }}</th>
              <th>{{ $t('admin_state') }}</th>
              <th class="right">{{ $t('admin_storage_size') }}</th>
              <th>{{ $t('admin_storage_modified') }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="`${row.bucket}/${row.filename}`">
              <td>
                <!-- A missing row has no file behind it, and an unrecognised one may not
                     be an image at all, so neither gets an <img> that would 404 -->
                <a v-if="row.status !== 'MISSING' && row.status !== 'UNKNOWN'"
                   :href="imageUrl(BUCKET_DIRS[row.bucket], row.filename)" target="_blank" rel="noopener">
                  <img class="thumb" :src="imageUrl(BUCKET_DIRS[row.bucket], row.filename)"
                       :alt="row.filename" loading="lazy" @error="onThumbError" />
                </a>
                <span v-else class="thumb placeholder"><ui-icon name="image" :size="14" /></span>
              </td>
              <td class="mono small">{{ row.filename }}</td>
              <td class="small muted nowrap">{{ $t(BUCKET_LABELS[row.bucket]) }}</td>
              <td>
                <span v-if="row.ownerLabel" class="truncate">{{ row.ownerLabel }}</span>
                <span v-else-if="row.ownerId" class="subtle mono small">#{{ row.ownerId }}</span>
                <span v-else class="subtle">—</span>
                <div v-if="row.status === 'STALE'" class="small subtle tnum">
                  {{ $t('admin_storage_owner_now_at', {version: row.ownerVersion}) }}
                </div>
              </td>
              <td>
                <span class="badge" :class="STATUS_TONES[row.status]">
                  <i class="dot"></i>{{ $t(STATUS_LABELS[row.status]) }}
                </span>
              </td>
              <td class="right tnum">{{ row.status === 'MISSING' ? '—' : fmtBytes(row.bytes) }}</td>
              <td class="small muted nowrap">{{ fmtAgo(row.lastModified) }}</td>
              <td class="actions">
                <span class="row-actions">
                  <button class="btn icon danger-hover" :disabled="!isDeletable(row)"
                          :title="$t('delete')" @click="askDelete(row)">
                    <ui-icon name="trash" />
                  </button>
                </span>
              </td>
            </tr>
          </tbody>
        </table>
        <ui-empty v-if="!imagesLoading && !rows.length" icon="disk"
                  :title="$t('admin_no_result')" :hint="$t('admin_no_result_hint')" />
      </div>

      <div class="panel-foot">
        <ui-pager :page="page" :size="size" :total="total" @update:page="p => { page = p; reload() }" />
      </div>
    </div>

    <!-- ------------------------------------------------------- delete a file -->
    <ui-modal v-model="deleteDialog" :title="$t('admin_storage_delete_file')">
      <div class="col" style="gap:12px">
        <p class="mono small">{{ selected?.filename }}</p>
        <div v-if="selected?.status === 'CURRENT'" class="alert warn">
          <ui-icon name="alert" />
          <span>{{ $t('admin_storage_delete_current_hint') }}</span>
        </div>
        <p v-else class="small muted">{{ $t('admin_storage_delete_hint') }}</p>
      </div>
      <template #actions>
        <button class="btn" @click="deleteDialog = false">{{ $t('cancel') }}</button>
        <button class="btn danger" @click="confirmDelete">{{ $t('delete') }}</button>
      </template>
    </ui-modal>

    <!-- ------------------------------------------------------------- cleanup -->
    <ui-modal v-model="cleanupDialog" :title="$t('admin_storage_sweep')" :width="560">
      <div class="col" style="gap:14px">
        <p class="small muted">{{ $t('admin_storage_sweep_hint') }}</p>

        <div class="field">
          <span>{{ $t('admin_storage_sweep_what') }}</span>
          <label v-for="s in SWEEPABLE" :key="s" class="check">
            <input type="checkbox" :value="s" v-model="cleanup.statuses" @change="cleanup.preview = null" />
            <span class="badge" :class="STATUS_TONES[s]">{{ $t(STATUS_LABELS[s]) }}</span>
            <span class="small muted">{{ $t(STATUS_HINTS[s]) }}</span>
          </label>
        </div>

        <label class="field">
          <span>{{ $t('admin_storage_sweep_scope') }}</span>
          <select v-model="cleanup.bucket" class="select" @change="cleanup.preview = null">
            <option :value="null">{{ $t('admin_storage_all_buckets') }}</option>
            <option v-for="b in BUCKETS" :key="b" :value="b">{{ $t(BUCKET_LABELS[b]) }}</option>
          </select>
        </label>

        <div v-if="cleanup.preview" class="alert info">
          <ui-icon name="info" />
          <span>{{ $t('admin_storage_sweep_preview', {
            count: cleanup.preview.files, size: fmtBytes(cleanup.preview.bytes),
          }) }}</span>
        </div>
        <div v-if="cleanup.statuses.includes('UNKNOWN')" class="alert warn">
          <ui-icon name="alert" />
          <span>{{ $t('admin_storage_sweep_unknown_warning') }}</span>
        </div>
      </div>
      <template #actions>
        <button class="btn" @click="cleanupDialog = false">{{ $t('cancel') }}</button>
        <button class="btn" :disabled="!cleanup.statuses.length || cleanup.running" @click="runCleanup(true)">
          {{ $t('admin_storage_sweep_dry_run') }}
        </button>
        <!-- Nothing is deleted until a dry run has said what would go -->
        <button class="btn danger" :disabled="!cleanup.preview?.files || cleanup.running"
                @click="runCleanup(false)">
          {{ $t('admin_storage_sweep_confirm') }}
        </button>
      </template>
    </ui-modal>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {cleanupStorage, deleteImage, errorKey, getStorageOverview, imageUrl, listImages} from '../lib/api'
import {fmtAgo, fmtBytes, fmtNumber} from '../lib/format'
import {notifyError, notifyOk} from '../lib/toast'
import UiIcon from '../components/UiIcon.vue'
import UiModal from '../components/UiModal.vue'
import UiPager from '../components/UiPager.vue'
import UiEmpty from '../components/UiEmpty.vue'

const {t, te} = useI18n()

const BUCKETS = ['RECIPE', 'RECIPE_THUMBNAIL', 'USER', 'COOKBOOK'] as const
const STATUSES = ['CURRENT', 'STALE', 'ORPHAN', 'MISSING', 'UNKNOWN'] as const
/** Bulk deletion is limited to what the backend accepts; CURRENT and MISSING are refused. */
const SWEEPABLE = ['STALE', 'ORPHAN', 'UNKNOWN'] as const

const BUCKET_LABELS: Record<string, string> = {
  RECIPE: 'admin_storage_bucket_recipe',
  RECIPE_THUMBNAIL: 'admin_storage_bucket_recipe_thumbnail',
  USER: 'admin_storage_bucket_user',
  COOKBOOK: 'admin_storage_bucket_cookbook',
}

/** Where each bucket is served from, so a row can preview its own file. */
const BUCKET_DIRS: Record<string, string> = {
  RECIPE: 'recipes',
  RECIPE_THUMBNAIL: 'recipes-thumbnails',
  USER: 'users',
  COOKBOOK: 'cookbooks',
}

const STATUS_LABELS: Record<string, string> = {
  CURRENT: 'admin_storage_status_current',
  STALE: 'admin_storage_status_stale',
  ORPHAN: 'admin_storage_status_orphan',
  MISSING: 'admin_storage_status_missing',
  UNKNOWN: 'admin_storage_status_unknown',
}

const STATUS_HINTS: Record<string, string> = {
  STALE: 'admin_storage_status_stale_hint',
  ORPHAN: 'admin_storage_status_orphan_hint',
  UNKNOWN: 'admin_storage_status_unknown_hint',
}

const STATUS_TONES: Record<string, string> = {
  CURRENT: 'ok',
  STALE: 'warn',
  ORPHAN: 'danger',
  MISSING: 'danger',
  UNKNOWN: 'info',
}

/** MISSING has no per-bucket byte figure, but its count belongs beside the others. */
const statusColumns = STATUSES

const overview = ref<any>({})
const loading = ref(true)

const rows = ref<any[]>([])
const imagesLoading = ref(false)
const page = ref(1)
const size = ref(25)
const total = ref(0)
const filters = reactive<any>({query: '', bucket: null, status: null, sort: 'SIZE_DESCENDING'})

const selected = ref<any>(null)
const deleteDialog = ref(false)
const cleanupDialog = ref(false)
const cleanup = reactive<any>({statuses: ['STALE', 'ORPHAN'], bucket: null, preview: null, running: false})

const volume = computed(() => {
  const total = overview.value.volumeTotalBytes
  if (!total) return null
  const used = total - (overview.value.volumeFreeBytes ?? 0)
  return {total, used, percent: Math.round((used / total) * 100)}
})

function fail(e: any) {
  const key = errorKey(e)
  notifyError(key && te(key) ? t(key) : t('admin_action_failed'))
}

/** A file the app never wrote may not be an image; drop a preview that cannot load. */
const onThumbError = (e: Event) => { (e.target as HTMLElement).style.visibility = 'hidden' }

/** Only rows backed by a file can be deleted, and the shipped fallbacks are refused. */
const isDeletable = (row: any) => row.status !== 'MISSING' && row.filename !== 'default.webp'

async function loadOverview() {
  loading.value = true
  try {
    overview.value = (await getStorageOverview()).data
  } catch (e) { fail(e) } finally { loading.value = false }
}

async function reload() {
  imagesLoading.value = true
  try {
    const {data} = await listImages(filters, page.value - 1, size.value)
    rows.value = data.items
    total.value = data.count
  } catch (e) { fail(e) } finally { imagesLoading.value = false }
}

const reloadAll = () => Promise.all([loadOverview(), reload()])

let timer: any
const debouncedReload = () => { clearTimeout(timer); timer = setTimeout(reset, 350) }
const reset = () => { page.value = 1; reload() }

/** Clicking a bucket, or one of its status counts, scopes the table below it. */
function focusBucket(bucket: string, status: string | null = null) {
  filters.bucket = bucket
  filters.status = status
  reset()
}

const askDelete = (row: any) => { selected.value = row; deleteDialog.value = true }

async function confirmDelete() {
  const row = selected.value
  deleteDialog.value = false
  try {
    await deleteImage(row.bucket, row.filename)
    notifyOk(t('admin_action_done'))
    await reloadAll()
  } catch (e) { fail(e) }
}

function openCleanup() {
  cleanup.statuses = ['STALE', 'ORPHAN']
  cleanup.bucket = null
  cleanup.preview = null
  cleanupDialog.value = true
}

async function runCleanup(dryRun: boolean) {
  cleanup.running = true
  try {
    const buckets = cleanup.bucket ? [cleanup.bucket] : []
    const {data} = await cleanupStorage(buckets, cleanup.statuses, dryRun)
    if (dryRun) {
      cleanup.preview = data
      if (!data.files) notifyOk(t('admin_storage_sweep_nothing'))
    } else {
      cleanupDialog.value = false
      notifyOk(t('admin_storage_sweep_done', {count: data.files, size: fmtBytes(data.bytes)}))
      if (data.failed) notifyError(t('admin_storage_sweep_failed', {count: data.failed}))
      await reloadAll()
    }
  } catch (e) { fail(e) } finally { cleanup.running = false }
}

onMounted(reloadAll)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; max-width: 1280px; }
.filters { gap: 8px; flex-wrap: wrap; }

.tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 10px; }
.tile { padding: 13px 14px; display: flex; flex-direction: column; gap: 2px; }
.tile .label { font-size: 12px; color: var(--c-text-muted); }
.tile .value { font-size: 23px; font-weight: 600; letter-spacing: -0.02em; line-height: 1.25; }
/* Space worth reclaiming, and broken images, are what an operator came here for */
.tile.flag { border-color: var(--c-warn-border); background: var(--c-warn-soft); }
.tile.flag .value, .tile.flag .label { color: var(--c-warn); }

.meter { height: 4px; margin-top: 8px; border-radius: 2px; background: var(--c-border); overflow: hidden; }
.meter > i { display: block; height: 100%; background: var(--c-accent); }
.meter.tight > i { background: var(--c-danger); }

.thumb {
  display: block;
  width: 34px; height: 26px;
  object-fit: cover;
  border-radius: var(--radius-sm);
  background: var(--c-surface-alt);
  border: 1px solid var(--c-border);
}
.thumb.placeholder { display: grid; place-items: center; color: var(--c-text-subtle); }

.linkish {
  border: 0; background: none; padding: 0;
  font: inherit; color: var(--c-accent); cursor: pointer; text-align: left;
}
.linkish:hover { text-decoration: underline; }

/* A count that filters the table below reads as a control, not just a figure */
.cell-badge { border-style: solid; cursor: pointer; font-variant-numeric: tabular-nums; }
.cell-badge:hover { filter: brightness(.96); }

.check .badge { pointer-events: none; }
</style>
