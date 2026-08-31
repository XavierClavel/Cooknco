<template>
  <div class="card panel" :class="{stale: loading}">
    <header>
      <div class="col" style="gap:1px">
        <span class="label">{{ label }}</span>
        <div class="row" style="gap:7px">
          <span class="value">{{ total.toLocaleString() }}</span>
          <span v-if="delta !== null" class="delta" :class="deltaTone">
            {{ delta > 0 ? '+' : '' }}{{ delta }}%
          </span>
        </div>
        <span class="sub small subtle">{{ periodLabel }}</span>
      </div>
      <span class="spacer"></span>
      <button class="btn icon" :title="$t(showTable ? 'admin_show_chart' : 'admin_show_table')"
              :aria-pressed="showTable" @click="showTable = !showTable">
        <ui-icon :name="showTable ? 'gauge' : 'table'" />
      </button>
    </header>

    <!-- Table view: every plotted value stays reachable without hovering -->
    <div v-if="showTable" class="table-wrap tablebox">
      <table class="grid">
        <thead><tr><th>{{ $t('admin_period') }}</th><th class="right">{{ label }}</th></tr></thead>
        <tbody>
          <tr v-for="(p, i) in points" :key="p.bucket">
            <td class="nowrap">{{ bucketLabel(p.bucket, true) }}</td>
            <td class="right tnum">{{ values[i].toLocaleString() }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else ref="host" class="plot"
         tabindex="0"
         role="img"
         :aria-label="ariaLabel"
         @pointermove="onMove" @pointerleave="active = null"
         @keydown="onKey" @focus="active = active ?? values.length - 1" @blur="active = null">
      <svg :width="w" :height="h" aria-hidden="true">
        <!-- recessive hairline grid -->
        <g>
          <line v-for="t in ticks" :key="`g${t.v}`"
                :x1="padL" :x2="w - padR" :y1="t.y" :y2="t.y" class="grid" />
        </g>

        <path v-if="values.length > 1" :d="areaPath" class="area" />
        <polyline v-if="values.length > 1" :points="linePoints" class="line" />

        <!-- endpoint marker, ringed in the surface colour so it stays legible -->
        <circle v-if="values.length" :cx="xAt(values.length - 1)" :cy="yAt(values[values.length - 1])"
                r="4" class="enddot" />
        <!-- the one direct label: latest value, so the current level reads without hovering -->
        <text v-if="values.length" class="endlabel"
              :x="xAt(values.length - 1) + 8"
              :y="Math.min(h - padB - 2, Math.max(padT + 8, yAt(values[values.length - 1]) + 3))">
          {{ values[values.length - 1].toLocaleString() }}
        </text>

        <!-- crosshair -->
        <g v-if="active !== null">
          <line :x1="xAt(active)" :x2="xAt(active)" :y1="padT" :y2="h - padB" class="cross" />
          <circle :cx="xAt(active)" :cy="yAt(values[active])" r="4" class="hotdot" />
        </g>

        <!-- axes: value ticks and the two ends of the window -->
        <g class="axis">
          <text v-for="t in ticks" :key="`t${t.v}`" :x="padL - 6" :y="t.y + 3" text-anchor="end">
            {{ t.v.toLocaleString() }}
          </text>
          <text :x="padL" :y="h - 5">{{ bucketLabel(points[0]?.bucket) }}</text>
          <text :x="w - padR" :y="h - 5" text-anchor="end">
            {{ bucketLabel(points[points.length - 1]?.bucket) }}
          </text>
        </g>
      </svg>

      <div v-if="active !== null" class="tip" :style="tipStyle">
        <span class="tip-v tnum">{{ values[active].toLocaleString() }}</span>
        <span class="tip-k"><i class="key"></i>{{ bucketLabel(points[active]?.bucket, true) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import UiIcon from './UiIcon.vue'

const props = withDefaults(defineProps<{
  label: string
  points: {bucket: string}[]
  /** Which numeric field of each point this chart plots. */
  field: string
  granularity: string
  /** Total for the previous window of the same length, for the delta. */
  previousTotal?: number | null
  /** False for metrics where a rise is bad (reports filed). */
  higherIsBetter?: boolean
  /**
   * 'flow' counts events per bucket, so the headline is their sum.
   * 'stock' is a running total already, so the headline is its latest value —
   * summing a cumulative series would report a number that means nothing.
   */
  kind?: 'flow' | 'stock'
  loading?: boolean
}>(), {previousTotal: null, higherIsBetter: true, kind: 'flow', loading: false})

const {t, locale} = useI18n()

const host = ref<HTMLElement | null>(null)
const w = ref(320)
const h = 132
const padL = 34, padR = 34, padT = 10, padB = 20
const active = ref<number | null>(null)
const showTable = ref(false)

const values = computed<number[]>(() => props.points.map(p => Number((p as any)[props.field]) || 0))

const total = computed(() =>
  props.kind === 'stock'
    ? (values.value[values.value.length - 1] ?? 0)
    : values.value.reduce((a, b) => a + b, 0))

const delta = computed(() => {
  if (props.kind === 'stock') return null
  const prev = props.previousTotal
  if (prev === null || prev === undefined) return null
  if (prev === 0) return total.value === 0 ? 0 : null   // growth from zero has no meaningful %
  return Math.round(((total.value - prev) / prev) * 100)
})

const deltaTone = computed(() => {
  if (!delta.value) return 'flat'
  const good = delta.value > 0 ? props.higherIsBetter : !props.higherIsBetter
  return good ? 'good' : 'bad'
})

const periodLabel = computed(() =>
  props.kind === 'stock'
    ? t('admin_trend_to_date')
    : t(`admin_trend_period_${props.granularity.toLowerCase()}`, {count: props.points.length}))

/** Round the axis top to a clean number so ticks read 0 / 5 / 10, never 0 / 3.7 / 7.4. */
function niceMax(v: number): number {
  if (v <= 0) return 1
  const pow = Math.pow(10, Math.floor(Math.log10(v)))
  const n = v / pow
  const step = n <= 1 ? 1 : n <= 2 ? 2 : n <= 2.5 ? 2.5 : n <= 5 ? 5 : 10
  return step * pow
}

const maxV = computed(() => niceMax(Math.max(...values.value, 0)))
const ticks = computed(() =>
  [0, maxV.value / 2, maxV.value]
    .filter((v, i, a) => a.indexOf(v) === i && Number.isInteger(v))
    .map(v => ({v, y: yAt(v)})))

const xAt = (i: number) => {
  const n = values.value.length
  if (n <= 1) return padL
  return padL + (i * (w.value - padL - padR)) / (n - 1)
}
const yAt = (v: number) => h - padB - (v / maxV.value) * (h - padT - padB)

const linePoints = computed(() => values.value.map((v, i) => `${xAt(i)},${yAt(v)}`).join(' '))
const areaPath = computed(() => {
  const base = h - padB
  const pts = values.value.map((v, i) => `${i === 0 ? 'M' : 'L'}${xAt(i)},${yAt(v)}`).join(' ')
  return `${pts} L${xAt(values.value.length - 1)},${base} L${padL},${base} Z`
})

const tipStyle = computed(() => {
  const x = xAt(active.value ?? 0)
  // Flip the tooltip before it can overflow the card
  const flip = x > w.value - 110
  return {left: `${flip ? x - 8 : x + 8}px`, top: '6px', transform: flip ? 'translateX(-100%)' : ''}
})

function bucketLabel(iso?: string, long = false): string {
  if (!iso) return ''
  const d = new Date(`${iso}T00:00:00`)
  if (props.granularity === 'MONTH') {
    return d.toLocaleDateString(locale.value, {month: 'short', year: long ? 'numeric' : '2-digit'})
  }
  return d.toLocaleDateString(locale.value, {month: 'short', day: 'numeric'})
}

const ariaLabel = computed(() =>
  `${props.label}: ${total.value} ${periodLabel.value}`)

/** The pointer only has to be nearest, never dead on the 2px line. */
function onMove(e: PointerEvent) {
  const el = host.value
  if (!el || values.value.length < 2) return
  const x = e.clientX - el.getBoundingClientRect().left
  const span = (w.value - padL - padR) / (values.value.length - 1)
  const i = Math.round((x - padL) / span)
  active.value = Math.min(values.value.length - 1, Math.max(0, i))
}

function onKey(e: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(e.key)) return
  e.preventDefault()
  const last = values.value.length - 1
  const cur = active.value ?? last
  active.value =
    e.key === 'Home' ? 0
    : e.key === 'End' ? last
    : Math.min(last, Math.max(0, cur + (e.key === 'ArrowRight' ? 1 : -1)))
}

let ro: ResizeObserver | null = null
onMounted(() => {
  if (!host.value) return
  // Measured rather than scaled with a viewBox, so strokes and dots keep their spec size
  ro = new ResizeObserver(([entry]) => { w.value = Math.max(180, entry.contentRect.width) })
  ro.observe(host.value)
})
onBeforeUnmount(() => ro?.disconnect())
</script>

<style scoped>
.card { padding: 13px 14px 8px; display: flex; flex-direction: column; gap: 8px; }
.card.stale { opacity: .55; }          /* refetch keeps the frame: no skeleton, no jump */
header { display: flex; align-items: flex-start; gap: 8px; }
.label { font-size: 12px; color: var(--c-text-muted); }
/* Proportional figures: this is a standalone value, not a column */
.value { font-size: 23px; font-weight: 600; letter-spacing: -0.02em; line-height: 1.15; }
.sub { line-height: 1.3; }

.delta {
  font-size: 11.5px; font-weight: 600;
  padding: 1px 6px; border-radius: 999px;
  border: 1px solid var(--c-border);
  color: var(--c-text-muted);
}
.delta.good { color: var(--c-ok);     background: var(--c-ok-soft);     border-color: var(--c-ok-border); }
.delta.bad  { color: var(--c-danger); background: var(--c-danger-soft); border-color: var(--c-danger-border); }

.plot { position: relative; outline: none; }
.plot:focus-visible { box-shadow: 0 0 0 2px var(--c-accent-soft); border-radius: var(--radius-sm); }

.grid   { stroke: var(--c-border); stroke-width: 1; }
.area   { fill: var(--c-accent); fill-opacity: .1; }
.line   { fill: none; stroke: var(--c-accent); stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; }
.enddot { fill: var(--c-accent); stroke: var(--c-surface); stroke-width: 2; }
.cross  { stroke: var(--c-border-strong); stroke-width: 1; }
.hotdot { fill: var(--c-accent); stroke: var(--c-surface); stroke-width: 2; }
.endlabel { font-size: 11px; font-weight: 600; fill: var(--c-text-muted); font-variant-numeric: tabular-nums; }
.axis text { font-size: 10px; fill: var(--c-text-subtle); font-variant-numeric: tabular-nums; }

.tip {
  position: absolute;
  pointer-events: none;
  background: var(--c-surface);
  border: 1px solid var(--c-border-strong);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 5px 8px;
  display: flex;
  flex-direction: column;
  gap: 1px;
  white-space: nowrap;
}
/* Value leads, label follows */
.tip-v { font-size: 14px; font-weight: 600; }
.tip-k { font-size: 11px; color: var(--c-text-muted); display: flex; align-items: center; gap: 5px; }
.key { width: 10px; height: 2px; border-radius: 1px; background: var(--c-accent); }

.tablebox { max-height: 172px; }
</style>
