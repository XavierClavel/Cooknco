<template>
  <div class="page">
    <div class="panel">
      <div class="panel-head filters">
        <select v-model="filters.level" class="select" style="width:120px" @change="restart">
          <option v-for="l in levels" :key="l" :value="l">≥ {{ l }}</option>
        </select>
        <select v-model="filters.logger" class="select" style="width:220px" @change="restart">
          <option :value="null">{{ $t('admin_all_loggers') }}</option>
          <option v-for="l in loggers" :key="l" :value="l">{{ shortLogger(l) }}</option>
        </select>
        <div class="search">
          <ui-icon name="search" :size="15" />
          <input v-model="filters.search" class="input" style="width:230px"
                 :placeholder="$t('admin_search_in_message')" @input="debouncedRestart" />
        </div>

        <span class="spacer"></span>

        <button class="btn sm" :class="{primary: live}" @click="toggleLive">
          <ui-icon :name="live ? 'pause' : 'play'" :size="13" />
          {{ live ? $t('admin_pause') : $t('admin_live_tail') }}
        </button>
        <span class="badge" :class="streamTone"><i class="dot"></i>{{ $t(`admin_stream_${streamState}`) }}</span>
        <button class="btn icon" :title="$t('admin_refresh')" @click="restart"><ui-icon name="refresh" /></button>
        <button class="btn icon danger-hover" :title="$t('admin_clear_buffer')" @click="dialog = true">
          <ui-icon name="trash" />
        </button>
      </div>

      <div ref="pane" class="term" @scroll="onScroll">
        <div v-for="e in entries" :key="e.sequence" class="line" :class="e.level.toLowerCase()">
          <span class="t">{{ fmtLogTime(e.timestamp) }}</span>
          <span class="lv">{{ e.level }}</span>
          <span class="lg" :title="e.logger">{{ shortLogger(e.logger) }}</span>
          <span class="msg">{{ e.message }}</span>
          <pre v-if="e.stackTrace" class="stack">{{ e.stackTrace }}</pre>
        </div>
        <div v-if="!entries.length" class="term-empty">{{ $t('admin_no_logs') }}</div>

        <button v-if="live && !autoScroll" class="jump btn sm" @click="scrollToBottom(true)">
          <ui-icon name="arrowDown" :size="13" />{{ $t('admin_jump_to_latest') }}
        </button>
      </div>

      <div class="panel-foot small subtle">
        {{ $t('admin_log_buffer_state', {size: bufferSize, capacity: bufferCapacity, dropped: droppedCount}) }}
        <span class="spacer"></span>
        <span class="tnum">{{ entries.length }} {{ $t('admin_lines_shown') }}</span>
      </div>
    </div>

    <ui-modal v-model="dialog" :title="$t('admin_clear_buffer')">
      <p>{{ $t('admin_clear_buffer_confirm') }}</p>
      <template #actions>
        <button class="btn" @click="dialog = false">{{ $t('cancel') }}</button>
        <button class="btn danger" @click="confirmClear">{{ $t('admin_clear_buffer') }}</button>
      </template>
    </ui-modal>
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, onMounted, onUnmounted, reactive, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {clearLogs, errorKey, getLogs, logStreamUrl} from '../lib/api'
import {fmtLogTime, shortLogger} from '../lib/format'
import {notifyError, notifyOk} from '../lib/toast'
import UiIcon from '../components/UiIcon.vue'
import UiModal from '../components/UiModal.vue'

const {t, te} = useI18n()

/** Cap on lines held client-side; the server buffer is the real history. */
const MAX_LINES = 2000
const levels = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR']

const entries = ref<any[]>([])
const loggers = ref<string[]>([])
const bufferSize = ref(0)
const bufferCapacity = ref(0)
const droppedCount = ref(0)

const filters = reactive<any>({level: 'INFO', logger: null, search: ''})
const live = ref(true)
const streamState = ref<'closed' | 'connecting' | 'open' | 'error'>('closed')
const autoScroll = ref(true)
const dialog = ref(false)
const pane = ref<HTMLElement | null>(null)

let es: EventSource | null = null

const streamTone = computed(() =>
  streamState.value === 'open' ? 'ok' : streamState.value === 'error' ? 'danger' : '')

function scrollToBottom(force = false) {
  if (!force && !autoScroll.value) return
  autoScroll.value = true
  nextTick(() => { if (pane.value) pane.value.scrollTop = pane.value.scrollHeight })
}

/** Reading back through history should not be yanked away by incoming lines. */
function onScroll() {
  const el = pane.value
  if (!el) return
  autoScroll.value = el.scrollHeight - el.scrollTop - el.clientHeight < 40
}

async function loadSnapshot() {
  try {
    const {data} = await getLogs({...filters, limit: 500})
    entries.value = data.entries
    loggers.value = data.loggers
    bufferSize.value = data.bufferSize
    bufferCapacity.value = data.bufferCapacity
    droppedCount.value = data.droppedCount
    scrollToBottom(true)
  } catch (e: any) {
    const key = errorKey(e)
    notifyError(key && te(key) ? t(key) : t('admin_action_failed'))
  }
}

function closeStream() {
  es?.close()
  es = null
  streamState.value = 'closed'
}

function openStream() {
  closeStream()
  streamState.value = 'connecting'
  // EventSource cannot set headers, so this rides on the admin session cookie
  es = new EventSource(logStreamUrl(filters), {withCredentials: true})
  es.onopen = () => { streamState.value = 'open' }
  es.onerror = () => { streamState.value = 'error' }
  es.addEventListener('log', (ev: MessageEvent) => {
    entries.value.push(JSON.parse(ev.data))
    if (entries.value.length > MAX_LINES) entries.value.splice(0, entries.value.length - MAX_LINES)
    bufferSize.value = Math.min(bufferSize.value + 1, bufferCapacity.value || bufferSize.value + 1)
    scrollToBottom()
  })
}

async function restart() {
  await loadSnapshot()
  live.value ? openStream() : closeStream()
}

let timer: any
const debouncedRestart = () => { clearTimeout(timer); timer = setTimeout(restart, 350) }

const toggleLive = () => {
  live.value = !live.value
  live.value ? openStream() : closeStream()
}

async function confirmClear() {
  dialog.value = false
  try {
    await clearLogs()
    entries.value = []
    bufferSize.value = 0
    droppedCount.value = 0
    notifyOk(t('admin_action_done'))
  } catch (e: any) {
    const key = errorKey(e)
    notifyError(key && te(key) ? t(key) : t('admin_action_failed'))
  }
}

onMounted(restart)
onUnmounted(closeStream)
</script>

<style scoped>
.page { max-width: 1400px; }
.filters { gap: 8px; flex-wrap: wrap; }

.term {
  position: relative;
  height: calc(100vh - var(--topbar-h) - 190px);
  min-height: 300px;
  overflow-y: auto;
  background: var(--c-term-bg);
  color: var(--c-term-text);
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.65;
  padding: 9px 12px;
}
.line {
  display: grid;
  grid-template-columns: 88px 46px 150px 1fr;
  gap: 10px;
  align-items: baseline;
  white-space: pre-wrap;
  word-break: break-word;
}
.t  { color: var(--c-term-dim); }
.lv { font-weight: 700; letter-spacing: .02em; }
.lg { color: #7fb3d8; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.stack {
  grid-column: 1 / -1;
  margin: 4px 0 9px;
  padding: 7px 9px;
  border-left: 2px solid #c0392f;
  background: #171d26;
  color: #e79c9c;
  font-size: 11px;
  overflow-x: auto;
}
.line.error .lv, .line.error .msg { color: #ff9a92; }
.line.warn  .lv, .line.warn  .msg { color: #f0c274; }
.line.info  .lv { color: #79b8ff; }
.line.debug .lv, .line.debug .msg { color: #97a2b0; }
.line.trace .lv, .line.trace .msg { color: #6b7684; }

.term-empty { color: var(--c-term-dim); padding: 30px 0; text-align: center; }

.jump {
  position: sticky;
  bottom: 6px;
  left: 50%;
  transform: translateX(-50%);
  box-shadow: var(--shadow-lg);
}
</style>
