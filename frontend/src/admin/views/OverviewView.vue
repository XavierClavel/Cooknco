<template>
  <div class="page">
    <div v-if="attention.length" class="attention">
      <div v-for="a in attention" :key="a.label" class="alert" :class="a.tone">
        <ui-icon :name="a.icon" />
        <span>{{ a.text }}</span>
        <span class="spacer"></span>
        <router-link class="btn sm" :to="a.to">{{ $t(a.action) }}</router-link>
      </div>
    </div>

    <section class="group">
      <div class="group-head">
        <h2 class="group-title">{{ $t('admin_activity_over_time') }}</h2>
        <span class="spacer"></span>
        <!-- One range control, above every chart it scopes -->
        <div class="segmented" role="group" :aria-label="$t('admin_range')">
          <button v-for="r in ranges" :key="r.granularity"
                  :class="{on: range.granularity === r.granularity}"
                  @click="selectRange(r)">
            {{ $t(r.label) }}
          </button>
        </div>
      </div>

      <div class="charts">
        <trend-chart
          v-for="c in flowCharts" :key="c.field"
          :label="$t(c.label)"
          :points="trends.points"
          :field="c.field"
          :granularity="range.granularity"
          :previous-total="c.previous"
          :higher-is-better="c.higherIsBetter"
          kind="flow"
          :loading="trendsLoading"
        />
      </div>

      <div class="charts totals">
        <trend-chart
          v-for="c in stockCharts" :key="c.field"
          :label="$t(c.label)"
          :points="trends.points"
          :field="c.field"
          :granularity="range.granularity"
          kind="stock"
          :loading="trendsLoading"
        />
      </div>
    </section>

    <section v-for="group in groups" :key="group.title" class="group">
      <h2 class="group-title">{{ $t(group.title) }}</h2>
      <div class="tiles">
        <div v-for="tile in group.tiles" :key="tile.label" class="tile panel" :class="{flag: tile.flag && tile.value}">
          <span class="label">{{ $t(tile.label) }}</span>
          <span class="value tnum">{{ loading ? '—' : fmtNumber(tile.value) }}</span>
          <span v-if="tile.sub" class="sub small subtle">{{ tile.sub }}</span>
        </div>
      </div>
    </section>

    <footer class="meta small subtle">
      {{ $t('admin_server_version') }} <span class="mono">{{ overview.version || '—' }}</span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {getOverview, getTrends} from '../lib/api'
import {fmtNumber} from '../lib/format'
import {notifyError} from '../lib/toast'
import TrendChart from '../components/TrendChart.vue'
import UiIcon from '../components/UiIcon.vue'

const {t} = useI18n()
const loading = ref(true)
const overview = ref<any>({})

const trends = ref<any>({points: [], previous: {}})
const trendsLoading = ref(true)

const ranges = [
  {granularity: 'DAY', buckets: 30, label: 'admin_range_30_days'},
  {granularity: 'WEEK', buckets: 12, label: 'admin_range_12_weeks'},
  {granularity: 'MONTH', buckets: 12, label: 'admin_range_12_months'},
]
const range = ref(ranges[1])

/**
 * One chart per metric rather than one chart with several series: signups,
 * recipes and reports live on completely different scales, and putting them on
 * a shared axis would invent a relationship that is not in the data.
 */
const flowCharts = computed(() => [
  {field: 'newUsers', label: 'admin_chart_new_users', previous: trends.value.previous?.newUsers ?? null, higherIsBetter: true},
  {field: 'newRecipes', label: 'admin_chart_new_recipes', previous: trends.value.previous?.newRecipes ?? null, higherIsBetter: true},
  {field: 'newReports', label: 'admin_chart_new_reports', previous: trends.value.previous?.newReports ?? null, higherIsBetter: false},
])

// Running totals rather than per-bucket counts, so they get their own row: the
// two measure different things and must not share an axis
const stockCharts = [
  {field: 'totalUsers', label: 'admin_chart_total_users'},
  {field: 'totalRecipes', label: 'admin_chart_total_recipes'},
]

async function loadTrends() {
  trendsLoading.value = true
  try {
    trends.value = (await getTrends(range.value.granularity, range.value.buckets)).data
  } catch {
    notifyError(t('admin_action_failed'))
  } finally {
    trendsLoading.value = false
  }
}

const selectRange = (r: typeof ranges[number]) => {
  range.value = r
  loadTrends()
}

const groups = computed(() => [
  {
    title: 'users',
    tiles: [
      {label: 'admin_total', value: overview.value.usersCount},
      {label: 'admin_active_30d', value: overview.value.activeUsersCount},
      {label: 'admin_new_last_week', value: overview.value.newUsersLastWeek},
      {label: 'admin_unverified', value: overview.value.unverifiedUsersCount},
      {label: 'admin_suspended', value: overview.value.suspendedUsersCount, flag: true},
      {label: 'admin_banned', value: overview.value.bannedUsersCount, flag: true},
    ],
  },
  {
    title: 'admin_content',
    tiles: [
      {label: 'recipes', value: overview.value.recipesCount},
      {label: 'admin_new_last_week', value: overview.value.newRecipesLastWeek},
      {label: 'admin_hidden', value: overview.value.hiddenRecipesCount, flag: true},
      {label: 'ingredients', value: overview.value.ingredientsCount},
      {label: 'cookbooks', value: overview.value.cookbooksCount},
    ],
  },
  {
    title: 'admin_engagement',
    tiles: [
      {label: 'likes', value: overview.value.likesCount},
      {label: 'follows', value: overview.value.followsCount},
    ],
  },
])

const attention = computed(() => {
  const out: any[] = []
  if (overview.value.pendingReportsCount) {
    out.push({
      label: 'reports', tone: 'warn', icon: 'flag', to: '/moderation', action: 'admin_review',
      text: t('admin_pending_reports_alert', {count: overview.value.pendingReportsCount}),
    })
  }
  if (overview.value.errorsInLogBuffer) {
    out.push({
      label: 'errors', tone: 'danger', icon: 'alert', to: '/logs', action: 'admin_logs',
      text: t('admin_errors_in_logs_alert', {count: overview.value.errorsInLogBuffer}),
    })
  }
  return out
})

onMounted(async () => {
  loadTrends()
  try {
    overview.value = (await getOverview()).data
  } catch {
    notifyError(t('admin_action_failed'))
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 22px; max-width: 1100px; }
.attention { display: flex; flex-direction: column; gap: 8px; }
.group-head { display: flex; align-items: center; gap: 10px; margin-bottom: 9px; }
.group-head .group-title { margin-bottom: 0; }
/* Explicit tracks, not auto-fit: auto-fit would size a 4th column the three
   flow charts can't fill, leaving a hole beside them. */
.charts { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
/* the running totals are a different kind of measure, so they sit on their own row */
.charts.totals { grid-template-columns: repeat(2, minmax(0, 1fr)); margin-top: 10px; }
@media (max-width: 980px) { .charts { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 660px) { .charts, .charts.totals { grid-template-columns: minmax(0, 1fr); } }

.segmented { display: inline-flex; border: 1px solid var(--c-border-strong); border-radius: var(--radius); overflow: hidden; }
.segmented button {
  border: 0; background: var(--c-surface); color: var(--c-text-muted);
  font: inherit; font-size: 12.5px; font-weight: 500;
  padding: 0 10px; height: 28px; cursor: pointer;
  border-right: 1px solid var(--c-border);
}
.segmented button:last-child { border-right: 0; }
.segmented button:hover { background: var(--c-surface-hover); }
.segmented button.on { background: var(--c-accent-soft); color: var(--c-accent); }

.group-title {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: .05em;
  color: var(--c-text-subtle);
  margin-bottom: 9px;
}
.tiles { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 10px; }
.tile { padding: 13px 14px; display: flex; flex-direction: column; gap: 2px; }
.tile .label { font-size: 12px; color: var(--c-text-muted); }
.tile .value { font-size: 25px; font-weight: 600; letter-spacing: -0.02em; line-height: 1.2; }
/* A non-zero moderation figure is the thing an operator should notice first */
.tile.flag { border-color: var(--c-warn-border); background: var(--c-warn-soft); }
.tile.flag .value, .tile.flag .label { color: var(--c-warn); }
.meta { padding-top: 2px; }
</style>
