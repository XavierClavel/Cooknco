<template>
  <div class="page">
    <!--
      The mails the app sends. A built-in kind always has a wording, because one ships in
      the app itself, so this reads as an override list rather than a form to fill: what
      each locale is sending, and whether that came from here or from the jar.
    -->
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">{{ $t('admin_mails_templates') }}</span>
        <span class="spacer"></span>
        <button class="btn sm" @click="openAdd">
          <ui-icon name="plus" :size="14" />
          {{ $t('admin_mails_add') }}
        </button>
        <button class="btn icon" :title="$t('admin_refresh')" @click="load"><ui-icon name="refresh" /></button>
      </div>

      <div class="progress" v-if="loading"><i></i></div>

      <div class="panel-body">
        <p class="small muted" style="margin:0 0 12px">{{ $t('admin_mails_hint') }}</p>

        <div class="kinds">
          <button v-for="tpl in templates" :key="tpl.key" class="kind"
                  :class="{on: tpl.key === selectedKey}" @click="select(tpl.key)">
            <span class="row" style="gap:6px">
              <b>{{ label(tpl) }}</b>
              <span v-if="!tpl.builtIn" class="badge">{{ $t('admin_mails_custom_kind') }}</span>
              <span v-if="dirtyKeys.has(tpl.key)" class="badge warn">{{ $t('admin_mails_unsaved') }}</span>
            </span>
            <span class="small subtle mono">{{ tpl.key }}</span>
            <span class="row small subtle" style="gap:6px">
              <span v-for="l in tpl.locales" :key="l.locale" class="badge" :class="l.custom ? 'ok' : 'info'">
                {{ LOCALE_LABELS[l.locale] }} · {{ $t(l.custom ? 'admin_mails_wording_custom' : 'admin_mails_wording_packaged') }}
              </span>
            </span>
          </button>
        </div>

        <ui-empty v-if="!loading && !templates.length" icon="mail" :title="$t('admin_no_result')" />
      </div>
    </div>

    <!-- ------------------------------------------------------------- editor -->
    <div v-if="selected" class="panel">
      <div class="panel-head">
        <span class="panel-title">{{ label(selected) }}</span>
        <span class="spacer"></span>
        <!-- A custom kind is authored here but nothing emits it yet; say so where it is edited -->
        <span v-if="!selected.builtIn" class="badge info">
          <ui-icon name="info" :size="12" />
          {{ $t('admin_mails_custom_inert') }}
        </span>
        <button v-if="!selected.builtIn" class="btn icon danger-hover" :title="$t('admin_mails_delete')"
                @click="deleteDialog = true">
          <ui-icon name="trash" />
        </button>
      </div>

      <div class="tabs">
        <button v-for="l in selected.locales" :key="l.locale" class="tab"
                :class="{on: l.locale === locale}" @click="locale = l.locale">
          {{ LOCALE_LABELS[l.locale] }}
          <span class="badge" :class="l.custom ? 'ok' : 'info'">
            {{ $t(l.custom ? 'admin_mails_wording_custom' : 'admin_mails_wording_packaged') }}
          </span>
        </button>
      </div>

      <div class="panel-body col" style="gap:14px">
        <div v-if="!current.subject && !current.body" class="alert warn">
          <ui-icon name="alert" />
          <span>{{ $t('admin_mails_empty_wording') }}</span>
        </div>

        <label class="field">
          <span>{{ $t('admin_mails_subject') }}</span>
          <input v-model="draft.subject" class="input" :maxlength="MAX_SUBJECT" />
        </label>

        <div class="field">
          <div class="row">
            <label for="mail-body">{{ $t('admin_mails_body') }}</label>
            <span class="spacer"></span>
            <span class="small subtle tnum">{{ draft.body.length }} / {{ MAX_BODY }}</span>
          </div>
          <textarea id="mail-body" ref="bodyInput" v-model="draft.body" class="input mono body"
                    :maxlength="MAX_BODY" spellcheck="false"></textarea>
        </div>

        <div v-if="selected.placeholders.length" class="row-wrap small">
          <span class="muted">{{ $t('admin_mails_placeholders') }}</span>
          <button v-for="name in selected.placeholders" :key="name" class="chip mono"
                  :title="$t('admin_mails_insert_placeholder')" @click="insert(name)">
            {{ token(name) }}
          </button>
          <span class="subtle">{{ $t('admin_mails_placeholders_hint') }}</span>
        </div>

        <!-- Refused by the server too: a reset mail with no link is one nobody can act on -->
        <div v-if="missingPlaceholders.length" class="alert danger">
          <ui-icon name="alert" />
          <span>{{ $t('admin_mails_missing_placeholder', {names: missingPlaceholders.join(', ')}) }}</span>
        </div>
      </div>

      <div class="panel-foot">
        <button class="btn primary" :disabled="!dirty || !!missingPlaceholders.length || saving" @click="save">
          <ui-icon name="check" :size="14" />
          {{ saving ? $t('admin_mails_saving') : $t('admin_mails_save') }}
        </button>
        <button class="btn" :disabled="!draft.body.trim()" @click="openPreview">
          <ui-icon name="eye" :size="14" />
          {{ $t('admin_mails_preview') }}
        </button>
        <button class="btn" :disabled="dirty" :title="dirty ? $t('admin_mails_test_needs_save') : ''"
                @click="openTest">
          <ui-icon name="send" :size="14" />
          {{ $t('admin_mails_test') }}
        </button>
        <span class="spacer"></span>
        <span v-if="current.updatedAt" class="small subtle">
          {{ $t('admin_mails_edited', {when: fmtAgo(current.updatedAt)}) }}
        </span>
        <button class="btn ghost" :disabled="!current.custom || !selected.builtIn" @click="restoreDialog = true">
          <ui-icon name="undo" :size="14" />
          {{ $t('admin_mails_restore') }}
        </button>
      </div>
    </div>

    <!-- ------------------------------------------------------------ preview -->
    <ui-modal v-model="previewDialog" :title="$t('admin_mails_preview')" :width="720">
      <div class="col" style="gap:12px">
        <div class="field">
          <span>{{ $t('admin_mails_preview_subject') }}</span>
          <p><b>{{ preview?.subject }}</b></p>
        </div>
        <div v-if="preview?.unfilled?.length" class="alert warn">
          <ui-icon name="alert" />
          <span>{{ $t('admin_mails_preview_unfilled', {names: preview.unfilled.join(', ')}) }}</span>
        </div>
        <!-- Operators paste raw HTML in here, so the preview runs none of it -->
        <iframe class="rendered" sandbox="" :srcdoc="preview?.body ?? ''"></iframe>
      </div>
      <template #actions>
        <button class="btn" @click="previewDialog = false">{{ $t('close') }}</button>
      </template>
    </ui-modal>

    <!-- --------------------------------------------------------- test send -->
    <ui-modal v-model="testDialog" :title="$t('admin_mails_test')">
      <div class="col" style="gap:12px">
        <p class="small muted">{{ $t('admin_mails_test_hint') }}</p>
        <label class="field">
          <span>{{ $t('admin_mails_test_recipient') }}</span>
          <input v-model="testRecipient" class="input" type="email" placeholder="you@example.com" />
        </label>
      </div>
      <template #actions>
        <button class="btn" @click="testDialog = false">{{ $t('cancel') }}</button>
        <button class="btn primary" :disabled="!testRecipient.trim()" @click="confirmTest">
          {{ $t('admin_mails_test') }}
        </button>
      </template>
    </ui-modal>

    <!-- ----------------------------------------------------- restore wording -->
    <ui-modal v-model="restoreDialog" :title="$t('admin_mails_restore')">
      <p class="small muted">{{ $t('admin_mails_restore_hint') }}</p>
      <template #actions>
        <button class="btn" @click="restoreDialog = false">{{ $t('cancel') }}</button>
        <button class="btn danger" @click="confirmRestore">{{ $t('admin_mails_restore') }}</button>
      </template>
    </ui-modal>

    <!-- ------------------------------------------------------------ add kind -->
    <ui-modal v-model="addDialog" :title="$t('admin_mails_add')">
      <div class="col" style="gap:12px">
        <p class="small muted">{{ $t('admin_mails_add_hint') }}</p>
        <label class="field">
          <span>{{ $t('admin_mails_add_key') }}</span>
          <input v-model="newKey" class="input mono" placeholder="welcome_back" />
          <span class="small subtle">{{ $t('admin_mails_add_key_hint') }}</span>
        </label>
      </div>
      <template #actions>
        <button class="btn" @click="addDialog = false">{{ $t('cancel') }}</button>
        <button class="btn primary" :disabled="!KEY_FORMAT.test(newKey.trim().toLowerCase())" @click="confirmAdd">
          {{ $t('admin_mails_add') }}
        </button>
      </template>
    </ui-modal>

    <!-- --------------------------------------------------------- delete kind -->
    <ui-modal v-model="deleteDialog" :title="$t('admin_mails_delete')">
      <p class="small muted">{{ $t('admin_mails_delete_hint') }}</p>
      <template #actions>
        <button class="btn" @click="deleteDialog = false">{{ $t('cancel') }}</button>
        <button class="btn danger" @click="confirmDelete">{{ $t('delete') }}</button>
      </template>
    </ui-modal>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {
  addMailTemplate, deleteMailTemplate, errorKey, listMailTemplates, previewMailTemplate,
  restoreMailTemplate, saveMailTemplate, sendTestMail,
} from '../lib/api'
import {fmtAgo} from '../lib/format'
import {notifyError, notifyOk} from '../lib/toast'
import UiIcon from '../components/UiIcon.vue'
import UiModal from '../components/UiModal.vue'
import UiEmpty from '../components/UiEmpty.vue'

const {t, te} = useI18n()

/** Must match EmailTemplates in the shared module, which is what refuses an over-long one. */
const MAX_SUBJECT = 255
const MAX_BODY = 16383
const KEY_FORMAT = /^[a-z][a-z0-9_]{2,62}$/

/** A language's own name is the same in every locale, so these are not translated. */
const LOCALE_LABELS: Record<string, string> = {FR: 'Français', EN: 'English'}

/** Built-in kinds get a name; a kind an operator added is known by its key. */
const KIND_LABELS: Record<string, string> = {
  account_verification: 'admin_mails_kind_account_verification',
  password_reset: 'admin_mails_kind_password_reset',
}

const templates = ref<any[]>([])
const loading = ref(true)
const selectedKey = ref<string | null>(null)
const locale = ref('FR')
const saving = ref(false)

/**
 * Edits in progress, per kind and locale.
 *
 * Kept across tab and kind switches on purpose: reworking the same mail in two languages
 * is the normal way to use this screen, and losing one half on a click would be its own bug.
 */
const drafts = reactive<Record<string, {subject: string; body: string}>>({})

const preview = ref<any>(null)
const previewDialog = ref(false)
const testDialog = ref(false)
const testRecipient = ref('')
const restoreDialog = ref(false)
const addDialog = ref(false)
const newKey = ref('')
const deleteDialog = ref(false)
const bodyInput = ref<HTMLTextAreaElement | null>(null)

const selected = computed(() => templates.value.find(tpl => tpl.key === selectedKey.value) ?? null)

/** The wording in service for the locale on screen: an override, or the packaged one. */
const current = computed(
  () => selected.value?.locales.find((l: any) => l.locale === locale.value) ?? {subject: '', body: ''},
)

const draftId = computed(() => `${selectedKey.value}:${locale.value}`)

const draft = computed({
  get: () => drafts[draftId.value] ?? {subject: current.value.subject, body: current.value.body},
  set: value => { drafts[draftId.value] = value },
})

/** Puts a draft back to what is saved, which is also what makes it stop reading as dirty. */
const syncDraft = () => {
  drafts[draftId.value] = {subject: current.value.subject, body: current.value.body}
}

/**
 * The pair on screen always has a draft entry.
 *
 * The subject and body are bound straight into it, so an absent entry would have `draft`
 * handing out a fresh object on every read and every keystroke writing to a temporary.
 */
watch([selectedKey, locale], () => {
  if (selectedKey.value && !drafts[draftId.value]) syncDraft()
}, {immediate: true})

const dirty = computed(() =>
  draft.value.subject !== current.value.subject || draft.value.body !== current.value.body)

/** Which kinds carry an unsaved edit, so the list says so before a kind is opened. */
const dirtyKeys = computed(() => {
  const keys = new Set<string>()
  templates.value.forEach(tpl => tpl.locales.forEach((l: any) => {
    const d = drafts[`${tpl.key}:${l.locale}`]
    if (d && (d.subject !== l.subject || d.body !== l.body)) keys.add(tpl.key)
  }))
  return keys
})

/** A declared placeholder the draft dropped: the server refuses the save, so say it here. */
const missingPlaceholders = computed(() =>
  (selected.value?.placeholders ?? []).filter((name: string) => !draft.value.body.includes(`{{${name}}}`)))

const label = (tpl: any) => (KIND_LABELS[tpl.key] ? t(KIND_LABELS[tpl.key]) : tpl.key)

/** Built here rather than in the template: a literal `{{` there is an interpolation. */
const token = (name: string) => `{{${name}}}`

function fail(e: any) {
  const key = errorKey(e)
  notifyError(key && te(key) ? t(key) : t('admin_action_failed'))
}

async function load() {
  loading.value = true
  try {
    templates.value = (await listMailTemplates()).data
    if (!selected.value) selectedKey.value = templates.value[0]?.key ?? null
    // The tab has to name a locale the server actually sends in
    const locales = selected.value?.locales ?? []
    if (locales.length && !locales.some((l: any) => l.locale === locale.value)) locale.value = locales[0].locale
    syncDraft()
  } catch (e) { fail(e) } finally { loading.value = false }
}

const select = (key: string) => { selectedKey.value = key }

/** Inserts a placeholder where the cursor is, which is the only place it can be meant. */
function insert(name: string) {
  const input = bodyInput.value
  const inserted = token(name)
  if (!input) {
    draft.value = {...draft.value, body: draft.value.body + inserted}
    return
  }
  const {selectionStart: from, selectionEnd: to} = input
  const body = draft.value.body
  draft.value = {...draft.value, body: body.slice(0, from) + inserted + body.slice(to)}
  // Vue rewrites the value, so the caret has to be put back after that lands
  requestAnimationFrame(() => {
    input.focus()
    input.setSelectionRange(from + inserted.length, from + inserted.length)
  })
}

async function save() {
  saving.value = true
  try {
    const {data} = await saveMailTemplate(selectedKey.value!, locale.value, draft.value.subject, draft.value.body)
    replace(data)
    syncDraft()
    notifyOk(t('admin_mails_saved'))
  } catch (e) { fail(e) } finally { saving.value = false }
}

/** The write endpoints answer with the whole kind, so the list needs no second round trip. */
function replace(info: any) {
  templates.value = templates.value.map(tpl => (tpl.key === info.key ? info : tpl))
}

async function openPreview() {
  try {
    preview.value = (await previewMailTemplate(selectedKey.value!, draft.value.subject, draft.value.body)).data
    previewDialog.value = true
  } catch (e) { fail(e) }
}

function openTest() {
  testRecipient.value = ''
  testDialog.value = true
}

async function confirmTest() {
  const recipient = testRecipient.value.trim()
  testDialog.value = false
  try {
    await sendTestMail(selectedKey.value!, recipient, locale.value)
    notifyOk(t('admin_mails_test_queued', {recipient}))
  } catch (e) { fail(e) }
}

async function confirmRestore() {
  restoreDialog.value = false
  try {
    const {data} = await restoreMailTemplate(selectedKey.value!, locale.value)
    replace(data)
    syncDraft()
    notifyOk(t('admin_action_done'))
  } catch (e) { fail(e) }
}

async function openAdd() {
  newKey.value = ''
  addDialog.value = true
}

async function confirmAdd() {
  const key = newKey.value.trim().toLowerCase()
  addDialog.value = false
  try {
    const {data} = (await addMailTemplate(key))
    templates.value = [...templates.value, data]
    selectedKey.value = data.key
    notifyOk(t('admin_action_done'))
  } catch (e) { fail(e) }
}

async function confirmDelete() {
  const key = selectedKey.value!
  deleteDialog.value = false
  try {
    await deleteMailTemplate(key)
    templates.value = templates.value.filter(tpl => tpl.key !== key)
    Object.keys(drafts).forEach(id => { if (id.startsWith(`${key}:`)) delete drafts[id] })
    selectedKey.value = templates.value[0]?.key ?? null
    notifyOk(t('admin_action_done'))
  } catch (e) { fail(e) }
}

onMounted(load)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; max-width: 1000px; }

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

textarea.body { min-height: 260px; font-size: 12.5px; }

/* A placeholder reads as something to click, not as a label */
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

/* Framed like a mail client's reading pane, on white: mails are not theme-aware */
.rendered {
  width: 100%;
  height: 340px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  background: #fff;
}
</style>
