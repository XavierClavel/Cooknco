<template>
  <div class="page">
    <!--
      Push notifications an operator sends by hand. Unlike the mails tab, there is nothing
      to save here: an announcement is typed once and sent, so this is a composer with an
      audience attached rather than a template list.
    -->
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">{{ $t('admin_notifications_compose') }}</span>
        <span class="spacer"></span>
        <button class="btn icon" :title="$t('admin_refresh')" @click="loadAudience">
          <ui-icon name="refresh" />
        </button>
      </div>

      <div class="panel-body col" style="gap:14px">
        <p class="small muted" style="margin:0">{{ $t('admin_notifications_hint') }}</p>

        <label class="field">
          <span>{{ $t('admin_notifications_title') }}</span>
          <input v-model="draft.title" class="input" :maxlength="MAX_TITLE"
                 :placeholder="$t('admin_notifications_title_placeholder')" />
        </label>

        <div class="field">
          <div class="row">
            <label for="notif-body">{{ $t('admin_notifications_body') }}</label>
            <span class="spacer"></span>
            <span class="small subtle tnum">{{ draft.body.length }} / {{ MAX_BODY }}</span>
          </div>
          <textarea id="notif-body" v-model="draft.body" class="input body" :maxlength="MAX_BODY"></textarea>
        </div>

        <label class="field">
          <span>{{ $t('admin_notifications_link') }}</span>
          <input v-model="draft.link" class="input mono" placeholder="/recipe/view?id=12" />
          <span class="small subtle">{{ $t('admin_notifications_link_hint') }}</span>
        </label>

        <!--
          A preview rather than a rendering: what the device draws is the OS's own layout,
          so this only promises the two strings and roughly how much of them fits.
        -->
        <div class="field">
          <span>{{ $t('admin_notifications_preview') }}</span>
          <div class="preview">
            <span class="preview-icon"><ui-icon name="bell" :size="14" /></span>
            <div class="preview-text">
              <b>{{ draft.title || $t('admin_notifications_title_placeholder') }}</b>
              <span class="small">{{ draft.body || '—' }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ----------------------------------------------------------- audience -->
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">{{ $t('admin_notifications_audience') }}</span>
      </div>

      <div class="panel-body col" style="gap:14px">
        <div class="segmented">
          <button :class="{on: mode === 'broadcast'}" @click="mode = 'broadcast'">
            {{ $t('admin_notifications_everyone') }}
          </button>
          <button :class="{on: mode === 'users'}" @click="mode = 'users'">
            {{ $t('admin_notifications_specific') }}
          </button>
        </div>

        <label v-if="mode === 'broadcast'" class="field">
          <span>{{ $t('admin_notifications_locale_filter') }}</span>
          <select v-model="localeFilter" class="input">
            <option value="">{{ $t('admin_notifications_locale_any') }}</option>
            <option v-for="l in LOCALES" :key="l" :value="l">{{ LOCALE_LABELS[l] }}</option>
          </select>
          <span class="small subtle">{{ $t('admin_notifications_locale_hint') }}</span>
        </label>

        <div v-else class="col" style="gap:10px">
          <label class="field">
            <span>{{ $t('admin_notifications_find_user') }}</span>
            <input v-model="userQuery" class="input" :placeholder="$t('admin_notifications_find_user_hint')" />
          </label>

          <div v-if="searching" class="progress"><i></i></div>

          <div v-if="matches.length" class="matches">
            <button v-for="u in matches" :key="u.id" class="match" @click="pick(u)">
              <b>{{ u.username }}</b>
              <span class="small subtle mono">#{{ u.id }}</span>
              <ui-icon name="plus" :size="13" />
            </button>
          </div>

          <div v-if="picked.length" class="row-wrap">
            <button v-for="u in picked" :key="u.id" class="picked"
                    :title="$t('admin_notifications_remove_user')" @click="unpick(u.id)">
              {{ u.username }}
              <ui-icon name="x" :size="12" />
            </button>
          </div>
          <p v-else class="small subtle" style="margin:0">{{ $t('admin_notifications_none_picked') }}</p>
        </div>

        <!-- What the send will actually do, in the two units that answer different questions -->
        <div class="reach">
          <div class="stat">
            <span class="small muted">{{ $t('admin_notifications_reach_users') }}</span>
            <b class="tnum">{{ audience?.users ?? '—' }}</b>
          </div>
          <div class="stat">
            <span class="small muted">{{ $t('admin_notifications_reach_devices') }}</span>
            <b class="tnum">{{ audience?.devices ?? '—' }}</b>
          </div>
          <div v-for="(count, platform) in audience?.byPlatform ?? {}" :key="platform" class="stat">
            <span class="small muted">{{ platform }}</span>
            <b class="tnum">{{ count }}</b>
          </div>
        </div>

        <div v-if="audience && audience.users > audience.devices" class="alert info">
          <ui-icon name="info" />
          <span>{{ $t('admin_notifications_stored_anyway') }}</span>
        </div>
      </div>

      <div class="panel-foot">
        <button class="btn primary" :disabled="!sendable || sending" @click="confirmDialog = true">
          <ui-icon name="send" :size="14" />
          {{ sending ? $t('admin_notifications_sending') : $t('admin_notifications_send') }}
        </button>
        <button class="btn" :disabled="testing" @click="sendTest">
          <ui-icon name="phone" :size="14" />
          {{ testing ? $t('admin_notifications_sending') : $t('admin_notifications_test') }}
        </button>
        <span class="spacer"></span>
        <span class="small subtle">{{ $t('admin_notifications_test_hint') }}</span>
      </div>
    </div>

    <!-- ------------------------------------------------------------- confirm -->
    <ui-modal v-model="confirmDialog" :title="$t('admin_notifications_send')">
      <div class="col" style="gap:12px">
        <p class="small muted">
          {{ mode === 'broadcast'
            ? $t('admin_notifications_confirm_broadcast', {users: audience?.users ?? 0, devices: audience?.devices ?? 0})
            : $t('admin_notifications_confirm_users', {users: picked.length, devices: audience?.devices ?? 0}) }}
        </p>
        <!-- A broadcast cannot be recalled, so it says so before it goes -->
        <div v-if="mode === 'broadcast'" class="alert warn">
          <ui-icon name="alert" />
          <span>{{ $t('admin_notifications_confirm_warning') }}</span>
        </div>
      </div>
      <template #actions>
        <button class="btn" @click="confirmDialog = false">{{ $t('cancel') }}</button>
        <button class="btn primary" @click="send">{{ $t('admin_notifications_send') }}</button>
      </template>
    </ui-modal>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {debounce} from 'lodash'
import {errorKey, getPushAudience, listUsers, sendAnnouncement, sendTestNotification} from '../lib/api'
import {notifyError, notifyOk} from '../lib/toast'
import UiIcon from '../components/UiIcon.vue'
import UiModal from '../components/UiModal.vue'

const {t, te} = useI18n()

/** Must match NotificationWordings in the shared module, which refuses an over-long one. */
const MAX_TITLE = 127
const MAX_BODY = 511

const LOCALES = ['FR', 'EN']
/** A language's own name is the same in every locale, so these are not translated. */
const LOCALE_LABELS: Record<string, string> = {FR: 'Français', EN: 'English'}

type Audience = {users: number; devices: number; byPlatform: Record<string, number>}
type PickedUser = {id: number; username: string}

const draft = reactive({title: '', body: '', link: ''})
const mode = ref<'broadcast' | 'users'>('broadcast')
const localeFilter = ref('')
const userQuery = ref('')
const matches = ref<PickedUser[]>([])
const picked = ref<PickedUser[]>([])
const searching = ref(false)
const audience = ref<Audience | null>(null)
const sending = ref(false)
const testing = ref(false)
const confirmDialog = ref(false)

const pickedIds = computed(() => picked.value.map(u => u.id))

const sendable = computed(() =>
  draft.title.trim().length > 0 &&
  draft.body.trim().length > 0 &&
  (mode.value === 'broadcast' || picked.value.length > 0))

/** Server errors come back as a bare cause key; fall back to its text when untranslated. */
function reportError(error: any) {
  const key = errorKey(error)
  notifyError(key && te(key) ? t(key) : t('admin_notifications_failed'))
}

async function loadAudience() {
  // A targeted send with nobody picked has no audience to describe yet
  if (mode.value === 'users' && !picked.value.length) {
    audience.value = null
    return
  }
  try {
    const locale = mode.value === 'broadcast' ? (localeFilter.value || null) : null
    audience.value = (await getPushAudience(pickedIds.value, locale)).data
  } catch (error) {
    audience.value = null
    reportError(error)
  }
}

/** Debounced: this runs on every keystroke in the search box. */
const search = debounce(async (query: string) => {
  if (!query.trim()) {
    matches.value = []
    return
  }
  searching.value = true
  try {
    const result = await listUsers({query: query.trim()}, 0, 8)
    matches.value = result.data.items
      .map((u: any) => ({id: u.id, username: u.username}))
      .filter((u: PickedUser) => !pickedIds.value.includes(u.id))
  } catch (error) {
    reportError(error)
  } finally {
    searching.value = false
  }
}, 250)

watch(userQuery, query => search(query))
watch([mode, localeFilter, picked], loadAudience, {deep: true})

function pick(user: PickedUser) {
  if (!pickedIds.value.includes(user.id)) picked.value = [...picked.value, user]
  matches.value = matches.value.filter(u => u.id !== user.id)
}

function unpick(id: number) {
  picked.value = picked.value.filter(u => u.id !== id)
}

async function send() {
  confirmDialog.value = false
  sending.value = true
  try {
    const locale = mode.value === 'broadcast' ? (localeFilter.value || null) : null
    const result = await sendAnnouncement(
      draft.title.trim(), draft.body.trim(), draft.link.trim(), pickedIds.value, locale,
    )
    // 202: stored for this many, pushing to that many. Delivery is not this screen's to report.
    notifyOk(t('admin_notifications_sent', {
      users: result.data.recipients,
      devices: result.data.devices,
    }))
    draft.title = ''
    draft.body = ''
    draft.link = ''
  } catch (error) {
    reportError(error)
  } finally {
    sending.value = false
  }
}

async function sendTest() {
  testing.value = true
  try {
    const result = await sendTestNotification(draft.title.trim(), draft.body.trim(), draft.link.trim())
    const {devices, pushed, failed} = result.data
    if (pushed > 0) notifyOk(t('admin_notifications_test_sent', {pushed, devices}))
    else notifyError(t('admin_notifications_test_failed', {failed}))
  } catch (error) {
    reportError(error)
  } finally {
    testing.value = false
  }
}

onMounted(loadAudience)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 22px; max-width: 1100px; }

.body { min-height: 90px; resize: vertical; }

/* ------------------------------------------------------------------ preview */
/* A preview, not a rendering: the layout on a device is the OS's own, so this
   only promises the two strings and roughly how much of each one fits. */
.preview {
  display: flex; gap: 10px; align-items: flex-start;
  padding: 12px; border: 1px solid var(--c-border); border-radius: var(--radius-lg);
  background: var(--c-surface-alt);
}
.preview-icon {
  display: grid; place-items: center;
  width: 28px; height: 28px; flex: none;
  border-radius: var(--radius); background: var(--c-accent); color: var(--c-text-inverse);
}
.preview-text { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.preview-text span { color: var(--c-text-muted); }

/* ------------------------------------------------------- audience selector */
/* Same segmented control as the overview's period switch, which is scoped there. */
.segmented {
  display: inline-flex; align-self: flex-start;
  border: 1px solid var(--c-border-strong); border-radius: var(--radius); overflow: hidden;
}
.segmented button {
  border: 0; border-right: 1px solid var(--c-border);
  background: var(--c-surface); color: var(--c-text-muted);
  font: inherit; font-size: 12.5px; font-weight: 500;
  padding: 0 12px; height: 28px; cursor: pointer;
}
.segmented button:last-child { border-right: 0; }
.segmented button:hover { background: var(--c-surface-hover); }
.segmented button.on { background: var(--c-accent-soft); color: var(--c-accent); }

/* ----------------------------------------------------------- user matching */
.matches { display: flex; flex-direction: column; gap: 4px; }
.match {
  display: flex; align-items: center; gap: 8px;
  padding: 7px 10px; border: 1px solid var(--c-border); border-radius: var(--radius);
  background: var(--c-surface); color: inherit; font: inherit; text-align: left; cursor: pointer;
}
.match:hover { background: var(--c-surface-hover); border-color: var(--c-border-strong); }
.match b { flex: 1; min-width: 0; }

/* A picked recipient, removable: solid rather than dashed, because unlike the
   mails tab's placeholder chips this stands for something already chosen. */
.picked {
  display: inline-flex; align-items: center; gap: 5px;
  height: 22px; padding: 0 8px;
  border: 1px solid var(--c-accent-border); border-radius: 999px;
  background: var(--c-accent-soft); color: var(--c-accent);
  font: inherit; font-size: 11.5px; cursor: pointer;
}
.picked:hover { border-color: var(--c-danger); background: var(--c-danger-soft); color: var(--c-danger); }

/* -------------------------------------------------------------------- reach */
.reach { display: grid; grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); gap: 10px; }
.stat {
  display: flex; flex-direction: column; gap: 2px;
  padding: 13px 14px;
  border: 1px solid var(--c-border); border-radius: var(--radius-lg);
  background: var(--c-surface-alt);
}
.stat b { font-size: 25px; font-weight: 600; letter-spacing: -0.02em; line-height: 1.2; }
</style>
