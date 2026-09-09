<template>
  <div class="page">
    <!--
      The documents the app prints. Every kind ships with a layout in the app itself, so
      this reads as an override list rather than a form to fill: what each locale is
      printed from, and whether that came from here or from the jar.
    -->
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">{{ $t('admin_documents_templates') }}</span>
        <span class="spacer"></span>
        <button class="btn icon" :title="$t('admin_refresh')" @click="load"><ui-icon name="refresh" /></button>
      </div>

      <div class="progress" v-if="loading"><i></i></div>

      <div class="panel-body">
        <p class="small muted" style="margin:0 0 12px">{{ $t('admin_documents_hint') }}</p>

        <div class="kinds">
          <button v-for="tpl in templates" :key="tpl.key" class="kind"
                  :class="{on: tpl.key === selectedKey}" @click="selectedKey = tpl.key">
            <span class="row" style="gap:6px">
              <b>{{ label(tpl) }}</b>
              <span v-if="dirtyKeys.has(tpl.key)" class="badge warn">{{ $t('admin_documents_unsaved') }}</span>
            </span>
            <span class="small subtle mono">{{ tpl.key }}</span>
            <span class="row small subtle" style="gap:6px">
              <span v-for="l in tpl.locales" :key="l.locale" class="badge" :class="l.custom ? 'ok' : 'info'">
                {{ LOCALE_LABELS[l.locale] }} · {{ $t(l.custom ? 'admin_documents_layout_custom' : 'admin_documents_layout_packaged') }}
              </span>
            </span>
          </button>
        </div>

        <ui-empty v-if="!loading && !templates.length" icon="fileText" :title="$t('admin_no_result')" />
      </div>
    </div>

    <template v-if="selected">
      <div class="tabs standalone">
        <button v-for="l in selected.locales" :key="l.locale" class="tab"
                :class="{on: l.locale === locale}" @click="locale = l.locale">
          {{ LOCALE_LABELS[l.locale] }}
          <span class="badge" :class="l.custom ? 'ok' : 'info'">
            {{ $t(l.custom ? 'admin_documents_layout_custom' : 'admin_documents_layout_packaged') }}
          </span>
        </button>
      </div>

      <!-- ------------------------------------------------ editor and preview -->
      <div class="split">
        <!-- ---------------------------------------------------------- editor -->
        <div class="panel pane">
          <div class="panel-head">
            <span class="panel-title">{{ $t('admin_documents_layout') }}</span>
            <span class="spacer"></span>
            <span class="small subtle tnum">{{ draft.length }} / {{ MAX_BODY }}</span>
          </div>

          <div class="panel-body pane-body">
            <textarea ref="bodyInput" v-model="draft" class="input mono editor"
                      :maxlength="MAX_BODY" spellcheck="false"></textarea>

            <div class="row-wrap small" style="margin-top:10px">
              <span class="muted">{{ $t('admin_documents_variables') }}</span>
              <button v-for="name in selected.variables" :key="name" class="chip mono"
                      :title="$t('admin_documents_insert_variable')" @click="insert(name)">
                {{ token(name) }}
              </button>
            </div>
            <p class="small subtle" style="margin:8px 0 0">{{ $t('admin_documents_variables_hint') }}</p>
            <p class="small subtle" style="margin:4px 0 0">{{ $t('admin_documents_offline_hint') }}</p>
          </div>

          <div class="panel-foot">
            <button class="btn primary" :disabled="!dirty || saving" @click="save">
              <ui-icon name="check" :size="14" />
              {{ saving ? $t('admin_documents_saving') : $t('admin_documents_save') }}
            </button>
            <span class="spacer"></span>
            <span v-if="current.updatedAt" class="small subtle">
              {{ $t('admin_documents_edited', {when: fmtAgo(current.updatedAt)}) }}
            </span>
            <button class="btn ghost" :disabled="!current.custom" @click="restoreDialog = true">
              <ui-icon name="undo" :size="14" />
              {{ $t('admin_documents_restore') }}
            </button>
          </div>
        </div>

        <!-- --------------------------------------------------------- preview -->
        <div class="panel pane">
          <div class="panel-head">
            <span class="panel-title">{{ $t('admin_documents_preview') }}</span>
            <span class="dot" :class="statusClass" :title="statusTitle"></span>
            <span class="spacer"></span>

            <label class="row small subtle" style="gap:5px" :title="$t('admin_documents_live_hint')">
              <input v-model="live" type="checkbox" />
              {{ $t('admin_documents_live') }}
            </label>

            <label class="row small subtle" style="gap:5px">
              {{ $t('admin_documents_preview_recipe') }}
              <input v-model="previewRecipeId" class="input tnum" style="width:78px" type="number" min="1"
                     :placeholder="$t('admin_documents_preview_newest')" />
            </label>

            <button class="btn icon" :disabled="!draft.trim()" :title="$t('admin_documents_preview_refresh')"
                    @click="render(true, true)">
              <ui-icon name="refresh" />
            </button>
            <a v-if="shownUrl" class="btn icon" :href="shownUrl" target="_blank" rel="noopener"
               :title="$t('admin_documents_preview_open')">
              <ui-icon name="external" />
            </a>
          </div>

          <!-- The last good print stays on screen underneath, so a typo mid-edit does not
               blank the pane the operator is working against. -->
          <div v-if="renderError" class="alert danger" style="margin:10px 14px 0">
            <ui-icon name="alert" />
            <span>{{ renderError }}</span>
          </div>

          <div class="pane-body preview">
            <!--
              Two frames, one shown. A print is loaded into the hidden one and they swap
              once it is ready, because pointing a single frame at a new PDF tears the
              viewer down and flashes white on every keystroke's worth of edit.
            -->
            <iframe class="pdf" :class="{on: shown === 'a'}" :src="urlA ?? 'about:blank'"
                    @load="onFrameLoad('a')"></iframe>
            <iframe class="pdf" :class="{on: shown === 'b'}" :src="urlB ?? 'about:blank'"
                    @load="onFrameLoad('b')"></iframe>

            <div v-if="!urlA && !urlB && !renderError" class="placeholder small subtle">
              {{ rendering ? $t('admin_documents_printing') : $t('admin_documents_preview_empty') }}
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- ------------------------------------------------------------ restore -->
    <ui-modal v-model="restoreDialog" :title="$t('admin_documents_restore')" :width="440">
      <p>{{ $t('admin_documents_restore_confirm', {locale: LOCALE_LABELS[locale]}) }}</p>
      <template #footer>
        <button class="btn" @click="restoreDialog = false">{{ $t('cancel') }}</button>
        <button class="btn danger" @click="confirmRestore">{{ $t('admin_documents_restore') }}</button>
      </template>
    </ui-modal>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, onUnmounted, reactive, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {errorKey, listPdfTemplates, previewPdfTemplate, restorePdfTemplate, savePdfTemplate} from '../lib/api'
import {fmtAgo} from '../lib/format'
import {notifyError, notifyOk} from '../lib/toast'
import UiIcon from '../components/UiIcon.vue'
import UiModal from '../components/UiModal.vue'
import UiEmpty from '../components/UiEmpty.vue'

const {t, te} = useI18n()

/** Must match PdfTemplates in the backend, which is what refuses an over-long one. */
const MAX_BODY = 65535

/**
 * How long the editor sits still before the preview is reprinted.
 *
 * A preview is a real Chromium print, not a re-layout in the browser: it costs a request
 * and a few hundred milliseconds of somebody's CPU. Long enough to skip the middle of a
 * word, short enough to feel like it is keeping up.
 */
const LIVE_DEBOUNCE = 700

/** A language's own name is the same in every locale, so these are not translated. */
const LOCALE_LABELS: Record<string, string> = {FR: 'Français', EN: 'English'}

const KIND_LABELS: Record<string, string> = {
  recipe: 'admin_documents_kind_recipe',
}

/**
 * Names that stand for a list or a condition rather than a value.
 *
 * They are inserted as a section — `{{#steps}}…{{/steps}}` — because writing one as a plain
 * `{{steps}}` prints nothing, which is a confusing first thing to happen to an operator.
 */
const SECTIONS = new Set(['ingredients', 'steps', 'hasIngredients', 'hasSteps'])

const templates = ref<any[]>([])
const loading = ref(true)
const selectedKey = ref<string | null>(null)
const locale = ref('FR')
const saving = ref(false)

/**
 * Edits in progress, per kind and locale.
 *
 * Kept across tab and kind switches on purpose: reworking the same sheet in two languages
 * is the normal way to use this screen, and losing one half on a click would be its own bug.
 */
const drafts = reactive<Record<string, string>>({})

const live = ref(true)
const rendering = ref(false)
const renderError = ref('')
const previewRecipeId = ref<string>('')
const restoreDialog = ref(false)
const bodyInput = ref<HTMLTextAreaElement | null>(null)

// The two preview frames, and which of them is currently in front.
const urlA = ref<string | null>(null)
const urlB = ref<string | null>(null)
const shown = ref<'a' | 'b'>('a')
/** The frame a print is loading into, so an unrelated `load` does not trigger a swap. */
let awaiting: 'a' | 'b' | null = null

const shownUrl = computed(() => (shown.value === 'a' ? urlA.value : urlB.value))

const selected = computed(() => templates.value.find(tpl => tpl.key === selectedKey.value) ?? null)

/** The layout in service for the locale on screen: an override, or the packaged one. */
const current = computed(
  () => selected.value?.locales.find((l: any) => l.locale === locale.value) ?? {body: ''},
)

const draftId = computed(() => `${selectedKey.value}:${locale.value}`)

const draft = computed({
  get: () => drafts[draftId.value] ?? current.value.body,
  set: value => { drafts[draftId.value] = value },
})

/** Puts a draft back to what is saved, which is also what makes it stop reading as dirty. */
const syncDraft = () => { drafts[draftId.value] = current.value.body }

const dirty = computed(() => draft.value !== current.value.body)

/** Which kinds carry an unsaved edit, so the list says so before a kind is opened. */
const dirtyKeys = computed(() => {
  const keys = new Set<string>()
  templates.value.forEach(tpl => tpl.locales.forEach((l: any) => {
    const d = drafts[`${tpl.key}:${l.locale}`]
    if (d !== undefined && d !== l.body) keys.add(tpl.key)
  }))
  return keys
})

const statusClass = computed(() => {
  if (renderError.value) return 'bad'
  if (rendering.value) return 'busy'
  return live.value ? 'ok' : 'off'
})

const statusTitle = computed(() => t(
  renderError.value ? 'admin_documents_status_error'
    : rendering.value ? 'admin_documents_printing'
      : live.value ? 'admin_documents_status_live' : 'admin_documents_status_paused',
))

const label = (tpl: any) => (KIND_LABELS[tpl.key] ? t(KIND_LABELS[tpl.key]) : tpl.key)

/** Built here rather than in the template: a literal `{{` there is an interpolation. */
const token = (name: string) => (SECTIONS.has(name) ? `{{#${name}}}{{/${name}}}` : `{{${name}}}`)

function fail(e: any) {
  const key = errorKey(e)
  notifyError(key && te(key) ? t(key) : t('admin_action_failed'))
}

async function load() {
  loading.value = true
  try {
    templates.value = (await listPdfTemplates()).data
    if (!selected.value) selectedKey.value = templates.value[0]?.key ?? null
    const locales = selected.value?.locales ?? []
    if (locales.length && !locales.some((l: any) => l.locale === locale.value)) locale.value = locales[0].locale
    syncDraft()
    render(true)
  } catch (e) { fail(e) } finally { loading.value = false }
}

/** Inserts a variable where the cursor is, which is the only place it can be meant. */
function insert(name: string) {
  const input = bodyInput.value
  const inserted = token(name)
  if (!input) {
    draft.value = draft.value + inserted
    return
  }
  const {selectionStart: from, selectionEnd: to} = input
  const body = draft.value
  draft.value = body.slice(0, from) + inserted + body.slice(to)
  // Vue rewrites the value, so the caret has to be put back after that lands. A section is
  // left with the caret between its two halves, which is where the markup goes.
  const caret = from + (SECTIONS.has(name) ? `{{#${name}}}`.length : inserted.length)
  requestAnimationFrame(() => {
    input.focus()
    input.setSelectionRange(caret, caret)
  })
}

// -------------------------------------------------------------------- preview

let timer: ReturnType<typeof setTimeout> | undefined
/** One print at a time, and at most one more waiting: see [render]. */
let inFlight = false
let queued = false

function schedule() {
  if (!live.value) return
  clearTimeout(timer)
  timer = setTimeout(() => render(), LIVE_DEBOUNCE)
}

/** What the sheet on screen was printed from, so the same thing is not printed twice. */
let printed = ''

/**
 * Prints the draft and shows it.
 *
 * Single-flight, and deliberately not cancellable: aborting the request would not stop the
 * renderer, so a fast typist would leave a queue of Chromium tabs behind them. Edits that
 * land mid-print set [queued] instead, and are picked up by one more print when this one
 * returns — so the pane always ends on the current text, at one print in flight.
 *
 * @param now skips the debounce, for a click or a change of kind
 * @param force prints even if nothing has changed since the sheet on screen. Only the
 *   refresh button passes it: the recipe being printed can change under an unchanged
 *   layout, and that button is how an operator asks to see it.
 */
async function render(now = false, force = false) {
  if (now) clearTimeout(timer)
  if (!selectedKey.value || !draft.value.trim()) return

  const id = Number(previewRecipeId.value)
  const recipeId = Number.isFinite(id) && id > 0 ? id : null
  const signature = `${selectedKey.value}|${locale.value}|${recipeId}|${draft.value}`
  if (!force && signature === printed) return
  if (inFlight) { queued = true; return }

  inFlight = true
  rendering.value = true
  try {
    const {data} = await previewPdfTemplate(selectedKey.value, locale.value, draft.value, recipeId)
    present(new Blob([data], {type: 'application/pdf'}))
    printed = signature
    renderError.value = ''
  } catch (e) {
    renderError.value = await previewErrorMessage(e)
  } finally {
    inFlight = false
    rendering.value = false
    if (queued) { queued = false; render(true) }
  }
}

/** Loads a print into whichever frame is hidden; [onFrameLoad] brings it forward. */
function present(blob: Blob) {
  const target = shown.value === 'a' ? 'b' : 'a'
  setFrame(target, URL.createObjectURL(blob))
  awaiting = target
}

/**
 * Object URLs are revoked as their slot is reused rather than on swap: the frame behind
 * still points at the old one until it is given a new print, and a printed sheet is not a
 * small thing to leave behind on every keystroke.
 */
function setFrame(slot: 'a' | 'b', url: string | null) {
  const holder = slot === 'a' ? urlA : urlB
  if (holder.value) URL.revokeObjectURL(holder.value)
  holder.value = url
}

function onFrameLoad(slot: 'a' | 'b') {
  if (awaiting !== slot) return
  awaiting = null
  shown.value = slot
}

/**
 * A preview asks for a PDF, so axios hands back a failed one as a Blob rather than as the
 * cause key the other endpoints answer with. Read as text it is that key again — without
 * this, every refused layout would report the same generic failure.
 */
async function previewErrorMessage(e: any): Promise<string> {
  const body = e?.response?.data
  if (body instanceof Blob) {
    try {
      e.response.data = await body.text()
    } catch { /* the generic message below is still better than nothing */ }
  }
  const key = errorKey(e)
  return key && te(key) ? t(key) : t('admin_action_failed')
}

// --------------------------------------------------------------------- saving

async function save() {
  saving.value = true
  try {
    const {data} = await savePdfTemplate(selectedKey.value!, locale.value, draft.value)
    replace(data)
    syncDraft()
    notifyOk(t('admin_documents_saved'))
  } catch (e) { fail(e) } finally { saving.value = false }
}

/** The write endpoints answer with the whole kind, so the list needs no second round trip. */
function replace(info: any) {
  templates.value = templates.value.map(tpl => (tpl.key === info.key ? info : tpl))
}

async function confirmRestore() {
  restoreDialog.value = false
  try {
    const {data} = await restorePdfTemplate(selectedKey.value!, locale.value)
    replace(data)
    syncDraft()
    render(true)
    notifyOk(t('admin_action_done'))
  } catch (e) { fail(e) }
}

// -------------------------------------------------------------------- wiring

watch([selectedKey, locale], () => {
  if (selectedKey.value && drafts[draftId.value] === undefined) syncDraft()
  render(true)
}, {immediate: true})

watch(draft, schedule)

// Through the debounce, not straight to a print: this is a text field, so typing a
// two-digit recipe id is two keystrokes and would otherwise be two prints.
watch(previewRecipeId, schedule)

// A click, so it is meant now.
watch(live, () => { if (live.value) render(true) })

onMounted(load)
onUnmounted(() => {
  clearTimeout(timer)
  setFrame('a', null)
  setFrame('b', null)
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 14px; max-width: 1600px; }

.kinds { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 8px; }
.kind {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 10px 12px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  background: var(--c-surface-alt);
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.kind:hover { background: var(--c-surface-hover); }
.kind.on { border-color: var(--c-accent); background: var(--c-accent-soft); }

.tabs { display: flex; gap: 2px; padding: 0 16px; border-bottom: 1px solid var(--c-border); }
.tabs.standalone { padding: 0; }
.tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 11px;
  margin-bottom: -1px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: none;
  font: inherit;
  font-size: 13px;
  font-weight: 500;
  color: var(--c-text-muted);
  cursor: pointer;
}
.tab:hover { color: var(--c-text); }
.tab.on { color: var(--c-text); border-bottom-color: var(--c-accent); }

/* Editor and print side by side, each filling the window rather than the page scrolling */
.split {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 14px;
  align-items: stretch;
}
@media (max-width: 1180px) {
  .split { grid-template-columns: minmax(0, 1fr); }
}

.pane { display: flex; flex-direction: column; height: calc(100vh - 300px); min-height: 520px; }
.pane-body { flex: 1; min-height: 0; display: flex; flex-direction: column; }

.editor { flex: 1; min-height: 0; resize: none; font-size: 12.5px; line-height: 1.5; }

.preview { position: relative; padding: 0; background: var(--c-surface-alt); }

/* Both frames are laid over each other; only the one that finished loading is visible */
.pdf {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  border: 0;
  border-radius: 0 0 var(--radius) var(--radius);
  background: #fff;
  opacity: 0;
  pointer-events: none;
}
.pdf.on { opacity: 1; pointer-events: auto; }

.placeholder { position: absolute; inset: 0; display: grid; place-items: center; }

/* Live-print state, next to the pane title */
.dot { width: 7px; height: 7px; border-radius: 50%; background: var(--c-text-muted); }
.dot.ok { background: #2e9e5b; }
.dot.busy { background: var(--c-accent); animation: pulse 1s ease-in-out infinite; }
.dot.bad { background: #d64545; }
.dot.off { background: var(--c-border-strong); }
@keyframes pulse { 50% { opacity: 0.25; } }

/* A variable reads as something to click, not as a label */
.chip {
  height: 20px;
  padding: 0 7px;
  border-radius: 999px;
  border: 1px dashed var(--c-border-strong);
  background: var(--c-surface);
  color: var(--c-text-muted);
  font-size: 11.5px;
  cursor: pointer;
}
.chip:hover { border-style: solid; color: var(--c-accent); border-color: var(--c-accent); }
</style>
