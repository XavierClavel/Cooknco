<template>
  <div class="page">
    <div class="panel">
      <div class="panel-head filters">
        <div class="segmented">
          <button v-for="s in statuses" :key="String(s.value)"
                  :class="{on: filters.status === s.value}"
                  @click="filters.status = s.value; reset()">
            {{ $t(s.label) }}
            <em v-if="s.value === 'PENDING' && pending" class="tnum">{{ pending }}</em>
          </button>
        </div>
        <select v-model="filters.targetType" class="select" style="width:160px" @change="reset">
          <option :value="null">{{ $t('admin_all_targets') }}</option>
          <option value="RECIPE">{{ $t('admin_target_recipe') }}</option>
          <option value="USER">{{ $t('admin_target_user') }}</option>
          <option value="COOKBOOK">{{ $t('admin_target_cookbook') }}</option>
        </select>
        <span class="spacer"></span>
        <button class="btn icon" :title="$t('admin_refresh')" @click="reload"><ui-icon name="refresh" /></button>
      </div>

      <div class="progress" v-if="loading"><i></i></div>

      <div class="table-wrap">
        <table class="grid">
          <thead>
            <tr>
              <th>{{ $t('admin_reported_item') }}</th>
              <th>{{ $t('admin_author') }}</th>
              <th>{{ $t('admin_reason') }}</th>
              <th>{{ $t('admin_reported_by') }}</th>
              <th>{{ $t('admin_filed') }}</th>
              <th>{{ $t('status') }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in rows" :key="r.id">
              <td>
                <div class="target">
                  <span class="badge">{{ $t(`admin_target_${r.targetType.toLowerCase()}`) }}</span>
                  <span v-if="r.targetLabel" class="truncate">{{ r.targetLabel }}</span>
                  <span v-else class="subtle">{{ $t('admin_target_deleted') }}</span>
                </div>
                <div class="row" style="gap:4px;margin-top:3px">
                  <span v-if="r.targetHidden" class="badge danger">{{ $t('admin_hidden') }}</span>
                  <span v-if="r.reportsOnTargetCount > 1" class="badge warn">
                    {{ $t('admin_reports_on_target', {count: r.reportsOnTargetCount}) }}
                  </span>
                </div>
              </td>
              <td class="muted">{{ r.targetAuthor?.username ?? '—' }}</td>
              <td>
                <span class="badge info">{{ $t(`report_reason_${r.reason.toLowerCase()}`) }}</span>
                <div v-if="r.comment" class="small muted comment" :title="r.comment">“{{ r.comment }}”</div>
              </td>
              <td class="muted">{{ r.reporter?.username ?? $t('admin_deleted_account') }}</td>
              <td class="small muted nowrap">{{ fmtAgo(r.creationDate) }}</td>
              <td>
                <span class="badge" :class="r.status === 'PENDING' ? 'warn' : r.status === 'RESOLVED' ? 'ok' : ''">
                  <i class="dot"></i>{{ $t(`admin_report_status_${r.status.toLowerCase()}`) }}
                </span>
                <div v-if="r.resolution" class="small subtle nowrap">
                  {{ $t(`admin_action_${r.resolution.toLowerCase()}`) }}
                  <template v-if="r.resolvedBy"> · {{ r.resolvedBy.username }}</template>
                </div>
              </td>
              <td class="actions">
                <button v-if="r.status === 'PENDING'" class="btn sm primary" @click="openResolve(r)">
                  {{ $t('admin_review') }}
                </button>
                <ui-icon v-else name="check" class="subtle" :title="r.moderatorNote || ''" />
              </td>
            </tr>
          </tbody>
        </table>
        <ui-empty v-if="!loading && !rows.length" icon="gavel"
                  :title="$t('admin_queue_empty')" :hint="$t('admin_queue_empty_hint')" />
      </div>

      <div class="panel-foot">
        <ui-pager :page="page" :size="size" :total="total" @update:page="p => { page = p; reload() }" />
      </div>
    </div>

    <ui-modal v-model="dialog" :title="$t('admin_resolve_report')" :width="580">
      <div v-if="selected" class="col" style="gap:14px">
        <dl class="summary">
          <dt>{{ $t('admin_reported_item') }}</dt>
          <dd>{{ selected.targetLabel ?? $t('admin_target_deleted') }}</dd>
          <dt>{{ $t('admin_reason') }}</dt>
          <dd>{{ $t(`report_reason_${selected.reason.toLowerCase()}`) }}</dd>
          <template v-if="selected.comment">
            <dt>{{ $t('admin_reporter_comment') }}</dt>
            <dd>“{{ selected.comment }}”</dd>
          </template>
          <template v-if="selected.targetAuthor">
            <dt>{{ $t('admin_author') }}</dt>
            <dd>{{ selected.targetAuthor.username }}</dd>
          </template>
        </dl>

        <div class="field">
          <label>{{ $t('admin_decision') }}</label>
          <div class="choices">
            <label v-for="a in actions" :key="a.value" class="choice" :class="{on: action === a.value}">
              <input type="radio" name="action" :value="a.value" v-model="action" />
              <span>{{ $t(a.label) }}</span>
            </label>
          </div>
        </div>

        <label v-if="action === 'SUSPEND_AUTHOR'" class="field">
          <span>{{ $t('admin_suspension_days') }}</span>
          <input v-model.number="days" class="input" type="number" min="1" max="3650" style="width:130px" />
        </label>

        <label class="field">
          <span>{{ $t('admin_moderator_note') }}</span>
          <textarea v-model="note" class="input" rows="3"></textarea>
        </label>

        <div v-if="hint" class="alert info"><ui-icon name="info" /><span>{{ hint }}</span></div>
      </div>
      <template #actions>
        <button class="btn" @click="dialog = false">{{ $t('cancel') }}</button>
        <button class="btn primary" @click="confirm">{{ $t('admin_apply') }}</button>
      </template>
    </ui-modal>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {errorKey, listReports, resolveReport} from '../lib/api'
import {fmtAgo} from '../lib/format'
import {notifyError, notifyOk} from '../lib/toast'
import UiIcon from '../components/UiIcon.vue'
import UiModal from '../components/UiModal.vue'
import UiPager from '../components/UiPager.vue'
import UiEmpty from '../components/UiEmpty.vue'

const {t, te} = useI18n()
const emit = defineEmits<{(e: 'pending-reports', n: number): void}>()

const statuses = [
  {value: 'PENDING', label: 'admin_report_status_pending'},
  {value: 'RESOLVED', label: 'admin_report_status_resolved'},
  {value: 'DISMISSED', label: 'admin_report_status_dismissed'},
  {value: null, label: 'admin_all'},
]

const rows = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(25)
const total = ref(0)
const pending = ref(0)
const filters = reactive<any>({status: 'PENDING', targetType: null})

const selected = ref<any>(null)
const dialog = ref(false)
const action = ref('DISMISS')
const note = ref('')
const days = ref(7)

/** Only offer decisions the backend will accept for this kind of target. */
const actions = computed(() => {
  const r = selected.value
  if (!r) return []
  const out = [{value: 'DISMISS', label: 'admin_action_dismiss'}]
  if (r.targetType === 'RECIPE') out.push({value: 'HIDE_CONTENT', label: 'admin_action_hide_content'})
  if (r.targetType !== 'USER') out.push({value: 'DELETE_CONTENT', label: 'admin_action_delete_content'})
  if (r.targetAuthor) {
    out.push({value: 'SUSPEND_AUTHOR', label: 'admin_action_suspend_author'})
    out.push({value: 'BAN_AUTHOR', label: 'admin_action_ban_author'})
  }
  return out
})

const hint = computed(() => ({
  HIDE_CONTENT: t('admin_hide_hint'),
  DELETE_CONTENT: t('admin_delete_content_hint'),
  SUSPEND_AUTHOR: t('admin_suspend_hint'),
  BAN_AUTHOR: t('admin_ban_hint'),
} as Record<string, string>)[action.value] ?? '')

function fail(e: any) {
  const key = errorKey(e)
  notifyError(key && te(key) ? t(key) : t('admin_action_failed'))
}

async function reload() {
  loading.value = true
  try {
    const {data} = await listReports(filters, page.value - 1, size.value)
    rows.value = data.items
    total.value = data.count
  } catch (e) { fail(e) } finally { loading.value = false }
  await refreshPending()
}

/** The badge must reflect the whole queue, not whatever filter is applied. */
async function refreshPending() {
  try {
    pending.value = (await listReports({status: 'PENDING'}, 0, 1)).data.count
    emit('pending-reports', pending.value)
  } catch { /* badge only */ }
}

const reset = () => { page.value = 1; reload() }

function openResolve(r: any) {
  selected.value = r
  action.value = 'DISMISS'
  note.value = ''
  days.value = 7
  dialog.value = true
}

async function confirm() {
  const r = selected.value
  dialog.value = false
  try {
    await resolveReport(r.id, action.value, note.value, days.value)
    notifyOk(t('admin_action_done'))
    await reload()
  } catch (e) { fail(e) }
}

onMounted(reload)
</script>

<style scoped>
.page { max-width: 1400px; }
.filters { gap: 8px; flex-wrap: wrap; }

.segmented { display: inline-flex; border: 1px solid var(--c-border-strong); border-radius: var(--radius); overflow: hidden; }
.segmented button {
  border: 0; background: var(--c-surface); color: var(--c-text-muted);
  font: inherit; font-size: 12.5px; font-weight: 500;
  padding: 0 11px; height: 32px; cursor: pointer;
  border-right: 1px solid var(--c-border);
  display: inline-flex; align-items: center; gap: 6px;
}
.segmented button:last-child { border-right: 0; }
.segmented button:hover { background: var(--c-surface-hover); }
.segmented button.on { background: var(--c-accent-soft); color: var(--c-accent); }
.segmented em { font-style: normal; font-size: 11px; font-weight: 600; padding: 0 5px; border-radius: 999px; background: var(--c-accent); color: #fff; }

.target { display: flex; align-items: center; gap: 6px; max-width: 380px; }
.comment { max-width: 260px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.summary { display: grid; grid-template-columns: auto 1fr; gap: 5px 14px; margin: 0; font-size: 13px; }
.summary dt { color: var(--c-text-subtle); font-size: 12px; }
.summary dd { margin: 0; }

.choices { display: flex; flex-direction: column; gap: 5px; }
.choice {
  display: flex; align-items: center; gap: 8px;
  padding: 7px 10px;
  border: 1px solid var(--c-border); border-radius: var(--radius);
  cursor: pointer; font-size: 13px;
}
.choice:hover { background: var(--c-surface-hover); }
.choice.on { border-color: var(--c-accent); background: var(--c-accent-soft); color: var(--c-accent); }
.choice input { accent-color: var(--c-accent); margin: 0; }
</style>
