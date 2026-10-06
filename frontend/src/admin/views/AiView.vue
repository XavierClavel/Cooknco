<template>
  <div class="page">
    <!--
      Every AI feature is a paid call to the same provider, so this page is about that money:
      what has been spent, on which feature, by whom, and the two limits that bound it — both
      shared by every feature. Read top-down: is a provider configured, how far into the
      budget is the month, then where the spend went.
    -->
    <div v-if="loading && !loaded" class="progress"><i></i></div>

    <div v-if="loaded && !overview.provider.configured" class="alert info">
      <ui-icon name="info" />
      <span>{{ $t('admin_ai_no_provider') }}</span>
    </div>

    <template v-if="loaded">
      <section class="tiles">
        <div class="tile panel" :class="budgetTone">
          <span class="label">{{ $t('admin_ai_month_spend') }}</span>
          <span class="value tnum">{{ money(overview.month.cost) }}</span>
          <span class="sub small subtle">
            <template v-if="overview.settings.monthlyBudget">
              {{ $t('admin_ai_of_budget', {budget: money(overview.settings.monthlyBudget), percent: budgetPercent}) }}
            </template>
            <template v-else>{{ $t('admin_ai_no_budget') }}</template>
          </span>
          <div v-if="overview.settings.monthlyBudget" class="meter" :class="budgetTone">
            <i :style="{width: `${Math.min(budgetPercent, 100)}%`}"></i>
          </div>
        </div>

        <div class="tile panel">
          <span class="label">{{ $t('admin_ai_month_requests') }}</span>
          <span class="value tnum">{{ fmtNumber(overview.month.requests) }}</span>
          <span class="sub small subtle">
            {{ $t('admin_ai_tokens', {input: fmtTokens(overview.month.inputTokens), output: fmtTokens(overview.month.outputTokens)}) }}
          </span>
        </div>

        <div class="tile panel">
          <span class="label">{{ $t('admin_ai_today') }}</span>
          <span class="value tnum">{{ fmtNumber(overview.today.requests) }}</span>
          <span class="sub small subtle">{{ money(overview.today.cost) }}</span>
        </div>

        <div class="tile panel">
          <span class="label">{{ $t('admin_ai_per_request') }}</span>
          <span class="value tnum">{{ overview.month.requests ? money(overview.month.cost / overview.month.requests, 4) : '—' }}</span>
          <span class="sub small subtle mono truncate" :title="overview.provider.baseUrl">
            {{ overview.provider.model || $t('admin_ai_no_model') }}
          </span>
        </div>
      </section>

      <div v-if="budgetPercent >= 100" class="alert danger">
        <ui-icon name="alert" />
        <span>{{ $t('admin_ai_budget_spent') }}</span>
      </div>
      <div v-else-if="budgetPercent >= 80" class="alert warn">
        <ui-icon name="alert" />
        <span>{{ $t('admin_ai_budget_close', {percent: budgetPercent}) }}</span>
      </div>

      <!-- --------------------------------------------------------------- limits -->
      <div class="panel">
        <div class="panel-head">
          <span class="panel-title">{{ $t('admin_ai_limits') }}</span>
          <span v-if="!overview.settingsSaved" class="badge">{{ $t('admin_ai_defaults') }}</span>
          <span v-if="dirty" class="badge warn">{{ $t('admin_releases_unsaved') }}</span>
          <span class="spacer"></span>
          <span v-if="overview.settingsUpdatedAt" class="small subtle">
            {{ $t('admin_releases_updated', {when: fmtAgo(overview.settingsUpdatedAt)}) }}
          </span>
        </div>

        <div class="panel-body col" style="gap:14px">
          <div class="fields">
            <label class="field">
              <span>{{ $t('admin_ai_daily_limit') }}</span>
              <input v-model.number="draft.dailyLimitPerUser" type="number" min="0" max="1000" step="1" class="input tnum" />
              <span class="small subtle">{{ $t('admin_ai_daily_limit_hint') }}</span>
            </label>

            <label class="field">
              <span>{{ $t('admin_ai_monthly_budget', {currency: draft.currency}) }}</span>
              <!--
                Text rather than a number input: empty has to mean "no ceiling", and a number
                input reports a half-typed "0." as empty, which wiped the decimal point.
              -->
              <input v-model="budgetText" type="text" inputmode="decimal" class="input tnum"
                     :placeholder="$t('admin_ai_monthly_budget_none')" />
              <span v-if="budgetInvalid" class="small danger-text">{{ $t('ai_budget_invalid') }}</span>
              <span v-else class="small subtle">{{ $t('admin_ai_monthly_budget_hint') }}</span>
            </label>

            <label class="field">
              <span>{{ $t('admin_ai_currency') }}</span>
              <input v-model="draft.currency" class="input mono" maxlength="3" style="text-transform:uppercase" />
            </label>
          </div>

          <div class="fields">
            <label class="field">
              <span>{{ $t('admin_ai_input_price', {currency: draft.currency}) }}</span>
              <input v-model.number="draft.inputPricePerMillion" type="number" min="0" step="0.01" class="input tnum" />
            </label>
            <label class="field">
              <span>{{ $t('admin_ai_output_price', {currency: draft.currency}) }}</span>
              <input v-model.number="draft.outputPricePerMillion" type="number" min="0" step="0.01" class="input tnum" />
            </label>
            <div class="field">
              <span>{{ $t('admin_ai_estimate') }}</span>
              <span class="estimate tnum">{{ money(estimatePerImport, 4) }}</span>
              <span class="small subtle">{{ $t('admin_ai_estimate_hint') }}</span>
            </div>
          </div>

          <div v-if="!draft.inputPricePerMillion && !draft.outputPricePerMillion" class="alert warn">
            <ui-icon name="alert" />
            <span>{{ $t('admin_ai_no_prices') }}</span>
          </div>
          <div v-if="draft.dailyLimitPerUser === 0" class="alert warn">
            <ui-icon name="ban" />
            <span>{{ $t('admin_ai_closed') }}</span>
          </div>
        </div>

        <div class="panel-foot">
          <button class="btn" :disabled="!dirty || saving" @click="reset">{{ $t('cancel') }}</button>
          <button class="btn primary" :disabled="!dirty || saving || budgetInvalid" @click="save">{{ $t('admin_ai_save') }}</button>
        </div>
      </div>

      <!-- ---------------------------------------------------------------- chart -->
      <div class="panel">
        <div class="panel-head">
          <span class="panel-title">{{ $t('admin_ai_last_days', {days: overview.days.length}) }}</span>
          <span class="spacer"></span>
          <div class="seg">
            <button class="btn small" :class="{primary: metric === 'cost'}" @click="metric = 'cost'">{{ $t('admin_ai_metric_cost') }}</button>
            <button class="btn small" :class="{primary: metric === 'requests'}" @click="metric = 'requests'">{{ $t('admin_ai_requests') }}</button>
          </div>
          <button class="btn icon" :title="$t('admin_refresh')" @click="load"><ui-icon name="refresh" /></button>
        </div>
        <div class="panel-body">
          <div class="bars">
            <div v-for="d in overview.days" :key="d.date" class="bar"
                 :title="`${d.date} · ${d.requests} · ${money(d.cost, 4)}`">
              <i :style="{height: `${barHeight(d)}%`}"></i>
            </div>
          </div>
          <div class="axis small subtle">
            <span>{{ overview.days[0]?.date }}</span>
            <span>{{ overview.days[overview.days.length - 1]?.date }}</span>
          </div>
        </div>
      </div>

      <!-- ------------------------------------------------------------- features -->
      <div class="panel">
        <div class="panel-head">
          <span class="panel-title">{{ $t('admin_ai_features') }}</span>
        </div>
        <div class="table-wrap">
          <table class="grid">
            <thead>
              <tr>
                <th>{{ $t('admin_ai_feature') }}</th>
                <th class="right">{{ $t('admin_ai_requests') }}</th>
                <th class="right">{{ $t('admin_ai_tokens_header') }}</th>
                <th class="right">{{ $t('admin_ai_metric_cost') }}</th>
                <th class="right">{{ $t('admin_ai_share') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="f in overview.features" :key="f.feature">
                <td><b>{{ featureLabel(f.feature) }}</b></td>
                <td class="right tnum">{{ fmtNumber(f.requests) }}</td>
                <td class="right tnum small muted">{{ fmtTokens(f.inputTokens) }} · {{ fmtTokens(f.outputTokens) }}</td>
                <td class="right tnum">{{ money(f.cost) }}</td>
                <td class="right tnum small muted">{{ overview.month.cost ? `${Math.round((f.cost / overview.month.cost) * 100)}%` : '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- ------------------------------------------------------------ top users -->
      <div class="panel">
        <div class="panel-head">
          <span class="panel-title">{{ $t('admin_ai_top_users') }}</span>
        </div>
        <div class="table-wrap">
          <table class="grid">
            <thead>
              <tr>
                <th>{{ $t('admin_ai_account') }}</th>
                <th class="right">{{ $t('admin_ai_requests') }}</th>
                <th class="right">{{ $t('admin_ai_today') }}</th>
                <th class="right">{{ $t('admin_ai_tokens_header') }}</th>
                <th class="right">{{ $t('admin_ai_metric_cost') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="u in overview.topUsers" :key="u.userId ?? 'deleted'">
                <td>
                  <b v-if="u.username">{{ u.username }}</b>
                  <span v-else class="subtle">{{ $t('admin_ai_deleted_account') }}</span>
                </td>
                <td class="right tnum">{{ fmtNumber(u.requests) }}</td>
                <td class="right tnum">
                  <span :class="{'warn-text': u.requestsToday >= overview.settings.dailyLimitPerUser}">
                    {{ u.requestsToday }} / {{ overview.settings.dailyLimitPerUser }}
                  </span>
                </td>
                <td class="right tnum small muted">{{ fmtTokens(u.inputTokens) }} · {{ fmtTokens(u.outputTokens) }}</td>
                <td class="right tnum">{{ money(u.cost, 4) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <ui-empty v-if="!overview.topUsers.length" icon="sparkles"
                  :title="$t('admin_ai_none')" :hint="$t('admin_ai_none_hint')" />
      </div>

      <!-- --------------------------------------------------------------- recent -->
      <div v-if="overview.recent.length" class="panel">
        <div class="panel-head">
          <span class="panel-title">{{ $t('admin_ai_recent') }}</span>
        </div>
        <div class="table-wrap">
          <table class="grid">
            <thead>
              <tr>
                <th>{{ $t('admin_ai_when') }}</th>
                <th>{{ $t('admin_ai_account') }}</th>
                <th>{{ $t('admin_ai_feature') }}</th>
                <th>{{ $t('admin_ai_outcome') }}</th>
                <th class="right">{{ $t('admin_ai_pages') }}</th>
                <th class="right">{{ $t('admin_ai_tokens_header') }}</th>
                <th class="right">{{ $t('admin_ai_metric_cost') }}</th>
                <th>{{ $t('admin_ai_model') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="e in overview.recent" :key="e.id">
                <td class="small nowrap">{{ fmtAgo(e.createdAt) }}<div class="subtle tnum">{{ fmtDateTime(e.createdAt) }}</div></td>
                <td class="small">
                  <span v-if="e.username">{{ e.username }}</span>
                  <span v-else class="subtle">{{ $t('admin_ai_deleted_account') }}</span>
                </td>
                <td class="small">{{ featureLabel(e.feature) }}</td>
                <td><span class="badge" :class="OUTCOME_TONES[e.outcome]"><span class="dot"></span>{{ $t(OUTCOME_LABELS[e.outcome]) }}</span></td>
                <td class="right tnum">{{ e.pages }}</td>
                <td class="right tnum small muted">{{ fmtTokens(e.inputTokens) }} · {{ fmtTokens(e.outputTokens) }}</td>
                <td class="right tnum">{{ money(e.cost, 4) }}</td>
                <td class="small subtle mono truncate">{{ e.model }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <footer class="meta small subtle">{{ $t('admin_ai_footer') }}</footer>
    </template>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {errorKey, getAiOverview, saveAiSettings, type AiSettings} from '../lib/api'
import {fmtAgo, fmtDateTime, fmtNumber} from '../lib/format'
import {notifyError, notifyOk} from '../lib/toast'
import UiIcon from '../components/UiIcon.vue'
import UiEmpty from '../components/UiEmpty.vue'

const {t, te} = useI18n()

/** What a typical one-page photo import bills; only used to turn draft prices into an estimate. */
const TYPICAL_INPUT_TOKENS = 3500
const TYPICAL_OUTPUT_TOKENS = 1000

const OUTCOME_LABELS: Record<string, string> = {
  READ: 'admin_ai_outcome_read',
  NOTHING_READ: 'admin_ai_outcome_nothing',
  FAILED: 'admin_ai_outcome_failed',
}
/** A feature the strings do not know yet still shows, under its own name. */
function featureLabel(feature: string): string {
  const key = `admin_ai_feature_${feature.toLowerCase()}`
  return te(key) ? t(key) : feature
}

const OUTCOME_TONES: Record<string, string> = {READ: 'ok', NOTHING_READ: 'warn', FAILED: 'danger'}

const overview = ref<any>({})
const loading = ref(true)
const loaded = ref(false)
const saving = ref(false)
const metric = ref<'cost' | 'requests'>('cost')

const draft = ref<AiSettings>({
  dailyLimitPerUser: 0, monthlyBudget: null, inputPricePerMillion: 0, outputPricePerMillion: 0, currency: 'EUR',
})

/**
 * The budget field as typed. Only ever read *into* the draft, never written back from it
 * while typing: a value re-rendered from a half-typed "0." comes back as "0", and the
 * decimal point is lost. Empty is no ceiling; a comma is accepted as the decimal separator.
 */
const budgetText = ref('')
watch(budgetText, (value) => {
  const text = value.trim().replace(',', '.')
  draft.value.monthlyBudget = text === '' ? null : Number(text)
})

/**
 * Something typed that is not a positive number. Refused here rather than sent, because
 * `Number("abc")` is NaN, which JSON writes as null — and null is "no ceiling".
 */
const budgetInvalid = computed(() => {
  const text = budgetText.value.trim().replace(',', '.')
  return text !== '' && !(Number(text) > 0)
})

const dirty = computed(() => loaded.value && JSON.stringify(normalised(draft.value)) !== JSON.stringify(normalised(overview.value.settings)))

function normalised(s: AiSettings) {
  return {...s, currency: (s.currency ?? '').trim().toUpperCase()}
}

const budgetPercent = computed(() => {
  const budget = overview.value.settings?.monthlyBudget
  return budget ? Math.round((overview.value.month.cost / budget) * 100) : 0
})

const budgetTone = computed(() => {
  if (budgetPercent.value >= 100) return 'flag danger'
  if (budgetPercent.value >= 80) return 'flag warn'
  return ''
})

const estimatePerImport = computed(() =>
  (TYPICAL_INPUT_TOKENS * (draft.value.inputPricePerMillion || 0) +
    TYPICAL_OUTPUT_TOKENS * (draft.value.outputPricePerMillion || 0)) / 1_000_000,
)

const barMax = computed(() =>
  Math.max(...(overview.value.days ?? []).map((d: any) => (metric.value === 'cost' ? d.cost : d.requests)), 0),
)

function barHeight(day: any): number {
  const value = metric.value === 'cost' ? day.cost : day.requests
  return barMax.value ? Math.max((value / barMax.value) * 100, value ? 2 : 0) : 0
}

/** Two decimals, or four below one unit, where two would turn a day's spend into 0.00. */
function money(value: number | null | undefined, digits?: number): string {
  if (value === null || value === undefined) return '—'
  if (digits === undefined) digits = value !== 0 && Math.abs(value) < 1 ? 4 : 2
  const currency = overview.value.settings?.currency || 'EUR'
  try {
    return value.toLocaleString(undefined, {style: 'currency', currency, minimumFractionDigits: digits, maximumFractionDigits: digits})
  } catch {
    return `${value.toFixed(digits)} ${currency}`
  }
}

function fmtTokens(n: number | null | undefined): string {
  if (n === null || n === undefined) return '—'
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}k`
  return String(n)
}

function reset() {
  draft.value = {...overview.value.settings}
  budgetText.value = draft.value.monthlyBudget === null ? '' : String(draft.value.monthlyBudget)
}

function fail(e: any) {
  const key = errorKey(e)
  notifyError(key && te(key) ? t(key) : t('admin_action_failed'))
}

async function load() {
  loading.value = true
  try {
    const wasDirty = dirty.value
    overview.value = (await getAiOverview()).data
    // A refresh must not throw away settings being edited.
    if (!loaded.value || !wasDirty) reset()
    loaded.value = true
  } catch (e) { fail(e) } finally { loading.value = false }
}

async function save() {
  saving.value = true
  try {
    await saveAiSettings({...draft.value, currency: draft.value.currency.trim().toUpperCase()})
    loaded.value = false
    await load()
    notifyOk(t('admin_ai_saved'))
  } catch (e) { fail(e) } finally { saving.value = false }
}

onMounted(load)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; max-width: 1280px; }

.tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 10px; }
.tile { padding: 13px 14px; display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.tile .label { font-size: 12px; color: var(--c-text-muted); }
.tile .value { font-size: 23px; font-weight: 600; letter-spacing: -0.02em; line-height: 1.25; }
.tile.flag.warn { border-color: var(--c-warn-border); background: var(--c-warn-soft); }
.tile.flag.warn .value, .tile.flag.warn .label { color: var(--c-warn); }
.tile.flag.danger { border-color: var(--c-danger-border); background: var(--c-danger-soft); }
.tile.flag.danger .value, .tile.flag.danger .label { color: var(--c-danger); }

.meter { height: 4px; margin-top: 8px; border-radius: 2px; background: var(--c-border); overflow: hidden; }
.meter > i { display: block; height: 100%; background: var(--c-accent); }
.meter.warn > i { background: var(--c-warn); }
.meter.danger > i { background: var(--c-danger); }

.fields { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 14px; }
.estimate { font-size: 18px; font-weight: 600; padding: 6px 0; }

.seg { display: flex; gap: 4px; }

.bars { display: flex; align-items: flex-end; gap: 3px; height: 140px; }
.bar { flex: 1; height: 100%; display: flex; align-items: flex-end; background: var(--c-bg-subtle, transparent); border-radius: 2px; }
.bar > i { display: block; width: 100%; background: var(--c-accent); border-radius: 2px 2px 0 0; }
.axis { display: flex; justify-content: space-between; margin-top: 6px; }

.danger-text { color: var(--c-danger); }
.warn-text { color: var(--c-warn); font-weight: 600; }
.meta { padding: 0 2px; }
</style>
