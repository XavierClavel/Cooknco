<template>
  <div class="page">
    <!--
      Read top-down: is there a recent backup, how reliably has there been one, and what is
      actually on the volume. Everything on this page is read off the dumps themselves —
      nothing here asks the backup job how it thinks it went.
    -->

    <div v-if="loaded && !overview.mounted" class="alert info">
      <ui-icon name="info" />
      <span>{{ $t('admin_backups_not_mounted') }}</span>
    </div>

    <template v-else>
      <section class="tiles">
        <div class="tile panel" :class="toneClass(overview.health)">
          <span class="label">{{ $t('admin_backups_state') }}</span>
          <span class="value">{{ loading ? '—' : $t(HEALTH_LABELS[overview.health]) }}</span>
          <!-- Nothing is claimed before the scan answers: an unread volume is not a healthy one -->
          <span class="sub small subtle">{{ loading ? '' : $t(HEALTH_HINTS[overview.health]) }}</span>
        </div>

        <div class="tile panel">
          <span class="label">{{ $t('admin_backups_last_run') }}</span>
          <span class="value tnum">{{ loading ? '—' : fmtAgo(lastRun) }}</span>
          <span class="sub small subtle">{{ lastRun ? fmtDateTime(lastRun) : $t('admin_backups_never') }}</span>
        </div>

        <div class="tile panel">
          <span class="label">{{ $t('admin_backups_dumps') }}</span>
          <span class="value tnum">{{ loading ? '—' : fmtNumber(overview.dumps?.length) }}</span>
          <span class="sub small subtle">
            {{ $t('admin_backups_retention', {days: overview.retentionDays ?? 0}) }} · {{ fmtBytes(overview.totalBytes) }}
          </span>
        </div>

        <div class="tile panel">
          <span class="label">{{ $t('admin_backups_volume') }}</span>
          <template v-if="volume">
            <span class="value tnum">{{ fmtBytes(volume.used) }}</span>
            <span class="sub small subtle">
              {{ $t('admin_storage_volume_of', {total: fmtBytes(volume.total), percent: volume.percent}) }}
            </span>
            <div class="meter" :class="{tight: volume.percent >= 85}"><i :style="{width: `${volume.percent}%`}"></i></div>
          </template>
          <template v-else>
            <span class="value tnum">—</span>
            <span class="sub small subtle">{{ $t('admin_backups_not_mounted_short') }}</span>
          </template>
        </div>
      </section>

      <!-- A run that started and never produced a dump: the job died mid-write -->
      <div v-if="overview.unfinished?.length" class="alert warn">
        <ui-icon name="alert" />
        <span>
          {{ $t('admin_backups_unfinished', {count: overview.unfinished.length}) }}
          <span class="mono small">{{ overview.unfinished.map((f: any) => f.filename).join(', ') }}</span>
        </span>
      </div>

      <div v-if="shrunkDumps.length" class="alert danger">
        <ui-icon name="alert" />
        <span>{{ $t('admin_backups_shrunk_warning', {count: shrunkDumps.length}) }}</span>
      </div>

      <!-- ------------------------------------------------------------ databases -->
      <div class="panel">
        <div class="panel-head">
          <span class="panel-title">{{ $t('admin_backups_databases') }}</span>
          <span class="spacer"></span>
          <button class="btn icon" :title="$t('admin_refresh')" @click="load"><ui-icon name="refresh" /></button>
        </div>

        <div class="progress" v-if="loading"><i></i></div>

        <div class="table-wrap">
          <table class="grid">
            <thead>
              <tr>
                <th>{{ $t('admin_backups_database') }}</th>
                <th>{{ $t('admin_state') }}</th>
                <th>{{ $t('admin_backups_last_dump') }}</th>
                <th class="right">{{ $t('admin_backups_last_size') }}</th>
                <th>{{ $t('admin_backups_history') }}</th>
                <th class="right">{{ $t('admin_backups_kept') }}</th>
                <th>{{ $t('admin_backups_oldest') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="d in overview.databases ?? []" :key="d.database">
                <td>
                  <b>{{ $t(DATABASE_LABELS[d.database] ?? 'admin_backups_database') }}</b>
                  <div class="small subtle mono">{{ DATABASE_PREFIXES[d.database] }}</div>
                </td>
                <td>
                  <span class="badge" :class="HEALTH_TONES[d.health]">
                    <span class="dot"></span>{{ $t(HEALTH_LABELS[d.health]) }}
                  </span>
                </td>
                <td class="small nowrap">
                  <template v-if="d.latest">
                    {{ fmtAgo(d.latest.takenAt) }}
                    <div class="subtle tnum">{{ fmtDateTime(d.latest.takenAt) }}</div>
                  </template>
                  <span v-else class="subtle">—</span>
                </td>
                <td class="right tnum">{{ fmtBytes(d.latest?.bytes) }}</td>
                <td class="small nowrap">
                  <!-- Gaps inside what the volume covers; whether tonight ran is the state column -->
                  <span class="badge" :class="d.nightsCovered < d.nightsExpected ? 'warn' : 'ok'">
                    {{ $t('admin_backups_nights', {covered: d.nightsCovered, expected: d.nightsExpected}) }}
                  </span>
                  <div v-if="d.nightsCovered < d.nightsExpected" class="subtle">
                    {{ $t('admin_backups_nights_missed', {count: d.nightsExpected - d.nightsCovered}) }}
                  </div>
                </td>
                <td class="right tnum small muted">{{ fmtNumber(d.dumps) }} · {{ fmtBytes(d.bytes) }}</td>
                <td class="small muted nowrap">{{ d.oldest ? fmtAgo(d.oldest.takenAt) : '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- ---------------------------------------------------------------- dumps -->
      <div class="panel">
        <div class="panel-head filters">
          <span class="panel-title">{{ $t('admin_backups_on_volume') }}</span>
          <select v-model="databaseFilter" class="select" style="width:190px">
            <option :value="null">{{ $t('admin_backups_all_databases') }}</option>
            <option v-for="d in overview.databases ?? []" :key="d.database" :value="d.database">
              {{ $t(DATABASE_LABELS[d.database] ?? 'admin_backups_database') }}
            </option>
          </select>
          <span class="spacer"></span>
          <span class="small subtle">{{ $t('admin_backups_read_only') }}</span>
        </div>

        <div class="table-wrap">
          <table class="grid">
            <thead>
              <tr>
                <th>{{ $t('admin_backups_taken') }}</th>
                <th>{{ $t('admin_backups_database') }}</th>
                <th class="right">{{ $t('admin_storage_size') }}</th>
                <th class="right">{{ $t('admin_backups_change') }}</th>
                <th>{{ $t('admin_backups_written') }}</th>
                <th>{{ $t('admin_storage_file') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="f in visibleDumps" :key="f.filename" :class="{flagged: f.shrunk}">
                <td class="small nowrap">
                  {{ fmtDateTime(f.takenAt) }}
                  <div class="subtle">{{ fmtAgo(f.takenAt) }}</div>
                </td>
                <td class="small">{{ $t(DATABASE_LABELS[f.database] ?? 'admin_backups_database') }}</td>
                <td class="right tnum">{{ fmtBytes(f.bytes) }}</td>
                <td class="right tnum small">
                  <span v-if="f.previousBytes == null" class="subtle">—</span>
                  <span v-else :class="f.shrunk ? 'danger-text' : 'muted'">{{ delta(f) }}</span>
                </td>
                <!--
                  Shown next to the run's own stamp because the two disagreeing is the point:
                  a dump copied or restored onto the volume has a fresh mtime and an old stamp.
                -->
                <td class="small muted nowrap">{{ fmtDateTime(f.lastModified) }}</td>
                <td class="small subtle mono truncate">{{ f.filename }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <ui-empty
          v-if="loaded && !visibleDumps.length"
          icon="database"
          :title="$t('admin_backups_none')"
          :hint="$t('admin_backups_none_hint')"
        />
      </div>

      <!-- --------------------------------------------------------------- strays -->
      <div v-if="overview.strays?.length" class="panel">
        <div class="panel-head">
          <span class="panel-title">{{ $t('admin_backups_strays') }}</span>
        </div>
        <div class="panel-body">
          <p class="small muted" style="margin:0 0 10px">{{ $t('admin_backups_strays_hint') }}</p>
          <div class="table-wrap">
            <table class="grid">
              <thead>
                <tr>
                  <th>{{ $t('admin_storage_file') }}</th>
                  <th class="right">{{ $t('admin_storage_size') }}</th>
                  <th>{{ $t('admin_storage_modified') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="f in overview.strays" :key="f.filename">
                  <td class="small mono truncate">{{ f.filename }}</td>
                  <td class="right tnum">{{ fmtBytes(f.bytes) }}</td>
                  <td class="small muted nowrap">{{ fmtDateTime(f.lastModified) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <footer class="meta small subtle">
        {{ $t('admin_backups_footer', {path: overview.path, ms: overview.scanDurationMs ?? 0}) }}
      </footer>
    </template>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {errorKey, getBackupOverview} from '../lib/api'
import {fmtAgo, fmtBytes, fmtDateTime, fmtNumber} from '../lib/format'
import {notifyError} from '../lib/toast'
import UiIcon from '../components/UiIcon.vue'
import UiEmpty from '../components/UiEmpty.vue'

const {t, te} = useI18n()

const HEALTH_LABELS: Record<string, string> = {
  OK: 'admin_backups_health_ok',
  LATE: 'admin_backups_health_late',
  STALE: 'admin_backups_health_stale',
  MISSING: 'admin_backups_health_missing',
}

/** Late is a job to go and look at; stale is one that has stopped. They read differently. */
const HEALTH_HINTS: Record<string, string> = {
  OK: 'admin_backups_health_ok_hint',
  LATE: 'admin_backups_health_late_hint',
  STALE: 'admin_backups_health_stale_hint',
  MISSING: 'admin_backups_health_missing_hint',
}

const HEALTH_TONES: Record<string, string> = {
  OK: 'ok',
  LATE: 'warn',
  STALE: 'danger',
  MISSING: 'danger',
}

const DATABASE_LABELS: Record<string, string> = {
  COOKNCO: 'admin_backups_database_cooknco',
  MAIL_SERVICE: 'admin_backups_database_mail_service',
}

/** The name the dumps carry, so a row on this page can be found on the volume by hand. */
const DATABASE_PREFIXES: Record<string, string> = {
  COOKNCO: 'cooknco-*.dump',
  MAIL_SERVICE: 'mail-service-*.dump',
}

const overview = ref<any>({})
const loading = ref(true)
const loaded = ref(false)
const databaseFilter = ref<string | null>(null)

/** The most recent run across every database — the figure "are we backed up" is read from. */
const lastRun = computed<number | null>(() => {
  const taken = (overview.value.databases ?? [])
    .map((d: any) => d.latest?.takenAt)
    .filter((t: number | undefined): t is number => !!t)
  return taken.length ? Math.max(...taken) : null
})

const volume = computed(() => {
  const total = overview.value.volumeTotalBytes
  if (!total) return null
  const used = total - overview.value.volumeFreeBytes
  return {used, total, percent: Math.round((used / total) * 100)}
})

const visibleDumps = computed(() =>
  (overview.value.dumps ?? []).filter((f: any) => !databaseFilter.value || f.database === databaseFilter.value),
)

const shrunkDumps = computed(() => (overview.value.dumps ?? []).filter((f: any) => f.shrunk))

const toneClass = (health: string) => (health && health !== 'OK' ? `flag ${HEALTH_TONES[health]}` : '')

/** Signed, against the same database's previous dump. */
function delta(file: any): string {
  const diff = file.bytes - file.previousBytes
  if (diff === 0) return '±0'
  return `${diff > 0 ? '+' : '−'}${fmtBytes(Math.abs(diff))}`
}

async function load() {
  loading.value = true
  try {
    overview.value = (await getBackupOverview()).data
    loaded.value = true
  } catch (e: any) {
    const key = errorKey(e)
    notifyError(key && te(key) ? t(key) : t('admin_action_failed'))
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; max-width: 1280px; }
.filters { gap: 8px; flex-wrap: wrap; }

.tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 10px; }
.tile { padding: 13px 14px; display: flex; flex-direction: column; gap: 2px; }
.tile .label { font-size: 12px; color: var(--c-text-muted); }
.tile .value { font-size: 23px; font-weight: 600; letter-spacing: -0.02em; line-height: 1.25; }

/* The state tile carries the verdict, so it takes the colour of it rather than a badge */
.tile.flag.warn { border-color: var(--c-warn-border); background: var(--c-warn-soft); }
.tile.flag.warn .value, .tile.flag.warn .label { color: var(--c-warn); }
.tile.flag.danger { border-color: var(--c-danger-border); background: var(--c-danger-soft); }
.tile.flag.danger .value, .tile.flag.danger .label { color: var(--c-danger); }

.meter { height: 4px; margin-top: 8px; border-radius: 2px; background: var(--c-border); overflow: hidden; }
.meter > i { display: block; height: 100%; background: var(--c-accent); }
.meter.tight > i { background: var(--c-danger); }

/* A dump that collapsed against the night before is the row to find at a glance */
tr.flagged { background: var(--c-danger-soft); }
.danger-text { color: var(--c-danger); font-weight: 600; }

.meta { padding: 0 2px; }
</style>
