<template>
  <div class="page">
    <!--
      The version gate for each mobile app. Unlike the mails and documents tabs, what is
      saved here is not copy: it is a rule the apps obey at launch, so the screen is built
      around making its consequences visible before the save rather than after.
    -->
    <div v-if="loading" class="progress"><i></i></div>

    <p class="small muted" style="max-width:70ch">{{ $t('admin_releases_hint') }}</p>

    <div v-for="gate in drafts" :key="gate.platform" class="panel">
      <div class="panel-head">
        <span class="panel-title">{{ $t(`admin_releases_platform_${gate.platform.toLowerCase()}`) }}</span>
        <span v-if="gate.configured" class="badge accent">{{ $t('admin_releases_gated') }}</span>
        <span v-else class="badge">{{ $t('admin_releases_ungated') }}</span>
        <span v-if="dirty(gate)" class="badge warn">{{ $t('admin_releases_unsaved') }}</span>
        <span class="spacer"></span>
        <span v-if="gate.updatedAt" class="small subtle">
          {{ $t('admin_releases_updated', {when: fmtAgo(gate.updatedAt)}) }}
        </span>
      </div>

      <div class="panel-body col" style="gap:14px">
        <div class="versions">
          <label class="field">
            <span>{{ $t('admin_releases_minimum') }}</span>
            <input v-model="gate.minimumVersion" class="input mono" placeholder="1.4.0"
                   :maxlength="MAX_VERSION_LENGTH" />
            <span class="small subtle">{{ $t('admin_releases_minimum_hint') }}</span>
          </label>

          <label class="field">
            <span>{{ $t('admin_releases_latest') }}</span>
            <input v-model="gate.latestVersion" class="input mono" placeholder="1.6.0"
                   :maxlength="MAX_VERSION_LENGTH" />
            <span class="small subtle">{{ $t('admin_releases_latest_hint') }}</span>
          </label>
        </div>

        <label class="field">
          <span>{{ $t('admin_releases_store_url') }}</span>
          <input v-model="gate.storeUrl" class="input mono"
                 :placeholder="STORE_PLACEHOLDER[gate.platform]" :maxlength="511" />
          <span class="small subtle">{{ $t('admin_releases_store_url_hint') }}</span>
        </label>

        <!--
          What the three numbers add up to, in the terms an operator is deciding in: which
          builds stop, which are nudged, which are left alone. Rendered from the draft, so
          it moves as the fields do and the consequence is on screen before the save.
        -->
        <div class="field">
          <span>{{ $t('admin_releases_effect') }}</span>
          <div class="bands">
            <div class="band danger">
              <ui-icon name="ban" :size="14" />
              <span>{{ $t('admin_releases_band_blocked', {version: shown(gate.minimumVersion)}) }}</span>
            </div>
            <div class="band warn">
              <ui-icon name="arrowDown" :size="14" />
              <span>{{ $t('admin_releases_band_nudged', {
                from: shown(gate.minimumVersion), to: shown(gate.latestVersion)}) }}</span>
            </div>
            <div class="band ok">
              <ui-icon name="check" :size="14" />
              <span>{{ $t('admin_releases_band_fine', {version: shown(gate.latestVersion)}) }}</span>
            </div>
          </div>
        </div>

        <div v-if="raisesFloor(gate)" class="alert warn">
          <ui-icon name="alert" />
          <span>{{ $t('admin_releases_raising_floor', {
            from: saved(gate.platform)?.minimumVersion || '—', to: gate.minimumVersion}) }}</span>
        </div>

        <!--
          Who the draft floor above would actually stop. Measured server-side against the
          field as it is being typed, so the number moves with the decision rather than
          confirming it afterwards — which for a version gate is too late by construction.
        -->
        <div class="field">
          <span>{{ $t('admin_releases_reach') }}</span>

          <div v-if="!reach[gate.platform]" class="small subtle">{{ $t('admin_releases_reach_loading') }}</div>

          <template v-else>
            <div class="reach">
              <div class="stat">
                <span class="small muted">{{ $t('admin_releases_reach_devices') }}</span>
                <b class="tnum">{{ reach[gate.platform].devices }}</b>
              </div>
              <div class="stat">
                <span class="small muted">{{ $t('admin_releases_reach_users') }}</span>
                <b class="tnum">{{ reach[gate.platform].users }}</b>
              </div>
              <div class="stat" :class="{alarm: reach[gate.platform].blockedDevices > 0}">
                <span class="small muted">{{ $t('admin_releases_reach_blocked') }}</span>
                <b class="tnum">{{ reach[gate.platform].blockedDevices }}</b>
                <span class="small subtle">
                  {{ $t('admin_releases_reach_blocked_users', {
                    users: reach[gate.platform].blockedUsers,
                    share: fmtShare(reach[gate.platform].blockedUsers, reach[gate.platform].users),
                  }) }}
                </span>
              </div>
            </div>

            <span class="small subtle">
              {{ $t('admin_releases_reach_window', {days: reach[gate.platform].windowDays}) }}
            </span>

            <!--
              Where the install base actually sits, which is what a floor is chosen from.
              A build nobody is on is free to block; the one under half the users is not.
              Counted in people first and devices second: what an operator hesitates over
              is how many users a save stops, and one person's two handsets are one
              hesitation, not two.
            -->
            <div v-if="reach[gate.platform].distribution.length" class="dist">
              <div class="dist-row head">
                <span class="small subtle">{{ $t('admin_releases_reach_col_version') }}</span>
                <span></span>
                <span class="small subtle dist-num">{{ $t('admin_releases_reach_col_share') }}</span>
                <span class="small subtle dist-num">{{ $t('admin_releases_reach_col_users') }}</span>
                <span class="small subtle dist-num">{{ $t('admin_releases_reach_col_devices') }}</span>
              </div>
              <div v-for="row in reach[gate.platform].distribution" :key="row.version || 'unknown'"
                   class="dist-row" :class="{blocked: row.blocked}">
                <span class="mono dist-name">
                  {{ row.version || $t('admin_releases_reach_unknown') }}
                </span>
                <span class="bar"><i :style="{width: barWidth(gate.platform, row.users)}"></i></span>
                <span class="small tnum dist-num dist-share">
                  {{ fmtShare(row.users, reach[gate.platform].users) }}
                </span>
                <span class="small tnum dist-num">{{ row.users }}</span>
                <span class="small tnum dist-num subtle">{{ row.devices }}</span>
              </div>
            </div>
            <p v-else class="small subtle" style="margin:0">{{ $t('admin_releases_reach_empty') }}</p>

            <div v-if="overlapping(gate.platform) > 0" class="small subtle">
              {{ $t('admin_releases_reach_overlap', {users: overlapping(gate.platform)}) }}
            </div>

            <div v-if="reach[gate.platform].unknownDevices > 0" class="small subtle">
              {{ $t('admin_releases_reach_unknown_hint', {devices: reach[gate.platform].unknownDevices}) }}
            </div>
          </template>
        </div>
      </div>

      <div class="panel-foot">
        <button class="btn primary" :disabled="!fillable(gate) || !dirty(gate) || busy === gate.platform"
                @click="confirm(gate)">
          <ui-icon name="check" :size="14" />
          {{ busy === gate.platform ? $t('admin_releases_saving') : $t('admin_releases_save') }}
        </button>
        <button v-if="gate.configured" class="btn danger" :disabled="busy === gate.platform"
                @click="askClear(gate.platform)">
          <ui-icon name="undo" :size="14" />
          {{ $t('admin_releases_clear') }}
        </button>
        <button v-if="dirty(gate)" class="btn ghost" @click="revert(gate.platform)">
          {{ $t('admin_releases_revert') }}
        </button>
        <span class="spacer"></span>
        <span class="small subtle">{{ $t('admin_releases_enforced_by_client') }}</span>
      </div>
    </div>

    <!-- ------------------------------------------------------------- checker -->
    <!--
      The verdict a real device would get, from the endpoint a real device calls. Reading
      the saved gate rather than the form above, which is why it says so and why it refuses
      to answer while something is unsaved: a simulation of a rule that is not in force yet
      would be the one number here nobody could act on.
    -->
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">{{ $t('admin_releases_check') }}</span>
      </div>

      <div class="panel-body col" style="gap:12px">
        <p class="small muted" style="margin:0">{{ $t('admin_releases_check_hint') }}</p>

        <div class="checker">
          <label class="field">
            <span>{{ $t('admin_releases_check_platform') }}</span>
            <select v-model="probe.platform" class="input">
              <option v-for="p in platforms" :key="p" :value="p">
                {{ $t(`admin_releases_platform_${p.toLowerCase()}`) }}
              </option>
            </select>
          </label>

          <label class="field">
            <span>{{ $t('admin_releases_check_version') }}</span>
            <input v-model="probe.version" class="input mono" placeholder="1.3.9"
                   :maxlength="MAX_VERSION_LENGTH" @keyup.enter="check" />
          </label>

          <button class="btn" :disabled="!probe.version.trim() || checking || probeUnsaved" @click="check">
            <ui-icon name="play" :size="14" />
            {{ $t('admin_releases_check_run') }}
          </button>
        </div>

        <div v-if="probeUnsaved" class="alert info">
          <ui-icon name="info" />
          <span>{{ $t('admin_releases_check_blocked_by_unsaved') }}</span>
        </div>

        <div v-else-if="verdict" class="verdict">
          <span class="badge" :class="VERDICT_TONE[verdict.status]">
            {{ $t(`admin_releases_verdict_${verdict.status.toLowerCase()}`) }}
          </span>
          <span class="small muted">
            {{ $t('admin_releases_verdict_detail', {
              version: verdict.currentVersion || '—',
              minimum: verdict.minimumVersion ?? '—',
              latest: verdict.latestVersion ?? '—',
            }) }}
          </span>
        </div>
      </div>
    </div>

    <!-- ------------------------------------------------------------ confirms -->
    <ui-modal v-model="saveDialog" :title="$t('admin_releases_save')">
      <div class="col" style="gap:12px">
        <p class="small muted">
          {{ $t('admin_releases_confirm_save', {
            platform: pending ? $t(`admin_releases_platform_${pending.platform.toLowerCase()}`) : '',
            minimum: pending?.minimumVersion,
            latest: pending?.latestVersion,
          }) }}
        </p>
        <div v-if="pendingReach && pendingReach.blockedDevices > 0" class="alert danger">
          <ui-icon name="alert" />
          <span>{{ $t('admin_releases_confirm_blocked', {
            devices: pendingReach.blockedDevices,
            users: pendingReach.blockedUsers,
            total: pendingReach.devices,
            share: fmtShare(pendingReach.blockedUsers, pendingReach.users),
          }) }}</span>
        </div>
        <div v-else-if="pendingReach" class="alert info">
          <ui-icon name="info" />
          <span>{{ $t('admin_releases_confirm_blocks_nobody') }}</span>
        </div>

        <div v-if="pending && raisesFloor(pending)" class="alert warn">
          <ui-icon name="alert" />
          <span>{{ $t('admin_releases_confirm_warning') }}</span>
        </div>
      </div>
      <template #actions>
        <button class="btn" @click="saveDialog = false">{{ $t('cancel') }}</button>
        <button class="btn primary" @click="save">{{ $t('admin_releases_save') }}</button>
      </template>
    </ui-modal>

    <ui-modal v-model="clearDialog" :title="$t('admin_releases_clear')">
      <p class="small muted">{{ $t('admin_releases_confirm_clear') }}</p>
      <template #actions>
        <button class="btn" @click="clearDialog = false">{{ $t('cancel') }}</button>
        <button class="btn danger" @click="clear">{{ $t('admin_releases_clear') }}</button>
      </template>
    </ui-modal>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {
  checkAppVersion, clearAppVersion, errorKey, getAppVersionReach, listAppVersions, saveAppVersion,
} from '../lib/api'
import {debounce} from 'lodash'
import {fmtAgo, fmtShare} from '../lib/format'
import {notifyError, notifyOk} from '../lib/toast'
import UiIcon from '../components/UiIcon.vue'
import UiModal from '../components/UiModal.vue'

const {t, te} = useI18n()

/** Must match AppVersions.MAX_LENGTH in the shared module, which refuses a longer one. */
const MAX_VERSION_LENGTH = 23

const STORE_PLACEHOLDER: Record<string, string> = {
  ANDROID: 'https://play.google.com/store/apps/details?id=com.xavierclavel.cooknco',
  IOS: 'https://apps.apple.com/app/id0000000000',
}

const VERDICT_TONE: Record<string, string> = {
  OK: 'ok',
  UPDATE_AVAILABLE: 'warn',
  UPDATE_REQUIRED: 'danger',
}

type Reach = {
  platform: string
  windowDays: number
  devices: number
  users: number
  minimumVersion: string
  blockedDevices: number
  blockedUsers: number
  unknownDevices: number
  distribution: {version: string; devices: number; users: number; blocked: boolean}[]
}

type Gate = {
  platform: string
  configured: boolean
  minimumVersion: string
  latestVersion: string
  storeUrl: string
  updatedAt: number | null
}

const loading = ref(false)
const busy = ref<string | null>(null)
const checking = ref(false)

/** What the server holds, untouched, so an edit can be compared against it and reverted. */
const stored = ref<Gate[]>([])
/** What the form is editing. One row per platform, gated or not. */
const drafts = ref<Gate[]>([])

/** Last answer per platform, so a card keeps its figures while the next one is in flight. */
const reach = ref<Record<string, Reach | undefined>>({})

const probe = reactive({platform: 'ANDROID', version: ''})
const verdict = ref<any>(null)

const saveDialog = ref(false)
const clearDialog = ref(false)
const pending = ref<Gate | null>(null)
const pendingClear = ref<string | null>(null)

/** What the confirm dialog quotes: the reach already measured for the platform being saved. */
const pendingReach = computed(() => (pending.value ? reach.value[pending.value.platform] ?? null : null))

const platforms = computed(() => drafts.value.map(g => g.platform))

/**
 * Whether the platform being probed has edits that are not in force yet.
 *
 * Scoped to that platform rather than to the tab: an unsaved iOS gate says nothing about
 * what an Android phone would be told, and blocking the check on it would only teach an
 * operator to ignore the warning.
 */
const probeUnsaved = computed(() => {
  const draft = drafts.value.find(g => g.platform === probe.platform)
  return !!draft && dirty(draft)
})

const saved = (platform: string) => stored.value.find(g => g.platform === platform)

/** An empty field reads as a blank in the summary rather than as a missing sentence. */
const shown = (version: string) => version.trim() || '…'

const fillable = (gate: Gate) =>
  !!gate.minimumVersion.trim() && !!gate.latestVersion.trim() && !!gate.storeUrl.trim()

function dirty(gate: Gate): boolean {
  const was = saved(gate.platform)
  if (!was) return false
  return was.minimumVersion !== gate.minimumVersion
    || was.latestVersion !== gate.latestVersion
    || was.storeUrl !== gate.storeUrl
}

/**
 * True while the edit would lock out builds that are running today.
 *
 * Only a *raise* is called out: lowering a floor lets people back in, and a first gate on
 * an ungated platform is announced by the confirm dialog instead — there is no previous
 * floor to compare it against.
 */
function raisesFloor(gate: Gate): boolean {
  const was = saved(gate.platform)
  if (!was?.configured || !gate.minimumVersion.trim()) return false
  return was.minimumVersion !== gate.minimumVersion
}

function fail(error: any) {
  const key = errorKey(error)
  notifyError(key && te(key) ? t(key) : t('admin_action_failed'))
}

async function load() {
  loading.value = true
  try {
    const {data} = await listAppVersions()
    stored.value = data
    drafts.value = data.map((g: Gate) => ({...g}))
    if (!platforms.value.includes(probe.platform) && platforms.value.length) {
      probe.platform = platforms.value[0]
    }
    // Primed for every card, so opening the tab already says what today's gates are
    // blocking rather than waiting for someone to touch a field
    await Promise.all(platforms.value.map(loadReach))
  } catch (e) { fail(e) } finally { loading.value = false }
}

function replace(info: Gate) {
  stored.value = stored.value.map(g => (g.platform === info.platform ? info : g))
  drafts.value = drafts.value.map(g => (g.platform === info.platform ? {...info} : g))
  verdict.value = null
}

// --------------------------------------------------------------------- reach

/**
 * Asks what the draft floor would cost, per platform.
 *
 * Server-side, against the field as it is being typed, rather than bucketing a histogram
 * here: comparing versions is where this feature can be subtly wrong (1.10 against 1.9),
 * and one implementation of it, exercised by the phones themselves, is worth a round trip.
 * A failed measurement leaves the last figures on screen rather than blanking the panel —
 * the operator is mid-keystroke, and a card that empties on every partial version reads as
 * "nobody is affected", which is the wrong thing to flash at someone raising a floor.
 */
async function loadReach(platform: string) {
  const draft = drafts.value.find(g => g.platform === platform)
  try {
    reach.value = {
      ...reach.value,
      [platform]: (await getAppVersionReach(platform, draft?.minimumVersion.trim() || '')).data,
    }
  } catch { /* keep whatever is on screen; the next keystroke asks again */ }
}

/**
 * Debounced per platform: this runs on every keystroke in a minimum-version field.
 *
 * One debounce per platform rather than one shared: lodash keeps only the last call's
 * arguments, so editing Android and then iOS inside the window would silently drop the
 * Android refresh and leave that card quoting a figure for a floor nobody typed.
 */
const debounced: Record<string, () => void> = {}
function scheduleReach(platform: string) {
  debounced[platform] ??= debounce(() => loadReach(platform), 300)
  debounced[platform]()
}

watch(
  () => drafts.value.map(g => ({platform: g.platform, minimum: g.minimumVersion})),
  (now, before) => {
    now.forEach((entry, i) => {
      if (entry.minimum !== before?.[i]?.minimum) scheduleReach(entry.platform)
    })
  },
  {deep: true},
)

/**
 * One bar's width: the share of users on that build, so the bar and the figure printed
 * beside it say the same thing rather than two different ones.
 *
 * Floored at a sliver instead of at zero. The long tail is precisely what this list exists
 * to show — a build with four people on it is the one worth noticing before blocking it —
 * and a bucket that renders as an empty track reads as a build nobody is on.
 */
function barWidth(platform: string, users: number): string {
  const total = reach.value[platform]?.users ?? 0
  if (!total || users <= 0) return '0%'
  return `${Math.max(2, Math.round((users / total) * 100))}%`
}

/**
 * How many people the list above counts more than once.
 *
 * A build's user count is "people with at least one device on it" — the same reading as
 * the blocked figure, deliberately, so the two can be compared — which means somebody
 * whose phone and tablet are on different builds appears in two rows and the shares add
 * up to more than everyone. Said only when it actually happens: on a base where each
 * person carries one handset the shares do total 100% and the caveat would be noise.
 */
function overlapping(platform: string): number {
  const measured = reach.value[platform]
  if (!measured) return 0
  return Math.max(0, measured.distribution.reduce((n, row) => n + row.users, 0) - measured.users)
}

function revert(platform: string) {
  const was = saved(platform)
  if (was) drafts.value = drafts.value.map(g => (g.platform === platform ? {...was} : g))
}

// -------------------------------------------------------------------- saving

function confirm(gate: Gate) {
  pending.value = gate
  saveDialog.value = true
}

async function save() {
  const gate = pending.value
  saveDialog.value = false
  if (!gate) return

  busy.value = gate.platform
  try {
    const {data} = await saveAppVersion(
      gate.platform,
      gate.minimumVersion.trim(),
      gate.latestVersion.trim(),
      gate.storeUrl.trim(),
    )
    replace(data)
    await loadReach(gate.platform)
    notifyOk(t('admin_releases_saved'))
  } catch (e) { fail(e) } finally { busy.value = null; pending.value = null }
}

function askClear(platform: string) {
  pendingClear.value = platform
  clearDialog.value = true
}

async function clear() {
  const platform = pendingClear.value
  clearDialog.value = false
  if (!platform) return

  busy.value = platform
  try {
    const {data} = await clearAppVersion(platform)
    replace(data)
    await loadReach(platform)
    notifyOk(t('admin_releases_cleared'))
  } catch (e) { fail(e) } finally { busy.value = null; pendingClear.value = null }
}

// ------------------------------------------------------------------ checking

async function check() {
  checking.value = true
  try {
    verdict.value = (await checkAppVersion(probe.platform, probe.version.trim())).data
  } catch (e) {
    verdict.value = null
    fail(e)
  } finally { checking.value = false }
}

onMounted(load)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 22px; max-width: 900px; }

.versions { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 14px; }

/* ------------------------------------------------------------------- bands */
/* The three outcomes of the numbers above, in the order a build passes through
   them, so the gate reads as one range split in two rather than as two numbers. */
.bands { display: flex; flex-direction: column; gap: 4px; }
.band {
  display: flex; align-items: center; gap: 8px;
  padding: 7px 11px;
  border: 1px solid var(--c-border); border-radius: var(--radius);
  background: var(--c-surface-alt);
  font-size: 12.5px;
}
.band.danger { background: var(--c-danger-soft); border-color: var(--c-danger-border); color: var(--c-danger); }
.band.warn   { background: var(--c-warn-soft);   border-color: var(--c-warn-border);   color: var(--c-warn); }
.band.ok     { background: var(--c-ok-soft);     border-color: var(--c-ok-border);     color: var(--c-ok); }
.band svg { flex: none; }

/* ------------------------------------------------------------------- reach */
.reach { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 10px; }
.stat {
  display: flex; flex-direction: column; gap: 2px;
  padding: 11px 13px;
  border: 1px solid var(--c-border); border-radius: var(--radius-lg);
  background: var(--c-surface-alt);
}
.stat b { font-size: 23px; font-weight: 600; letter-spacing: -0.02em; line-height: 1.2; }
/* The one figure worth a colour: how many people this save stops. */
.stat.alarm { background: var(--c-danger-soft); border-color: var(--c-danger-border); }
.stat.alarm b { color: var(--c-danger); }

/* Where the install base sits, in people. One grid for the whole table rather than one
   per row, so the columns size themselves to the longest version string and to whichever
   header a translation is longest in, and the figures still line up under them. */
.dist {
  display: grid;
  grid-template-columns: max-content 1fr max-content max-content max-content;
  align-items: center;
  gap: 3px 10px;
  margin-top: 4px;
  font-size: 12.5px;
}
.dist-row { display: contents; }
.dist-num { text-align: right; }
.dist-row.head > * { padding-bottom: 3px; }
/* The percentage is the figure the decision is made on; devices are context for it. */
.dist-share { font-weight: 600; }
.bar { height: 8px; border-radius: 999px; background: var(--c-surface-alt); overflow: hidden; }
.bar > i { display: block; height: 100%; background: var(--c-accent); }
.dist-row.blocked .dist-name,
.dist-row.blocked .dist-share { color: var(--c-danger); }
.dist-row.blocked .bar > i { background: var(--c-danger); }

/* ----------------------------------------------------------------- checker */
.checker { display: flex; align-items: flex-end; gap: 12px; flex-wrap: wrap; }
.checker .field { min-width: 170px; }
.verdict { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
</style>
