<template>
  <div class="page">
    <div class="panel">
      <div class="panel-head filters">
        <div class="search">
          <ui-icon name="search" :size="15" />
          <input v-model="filters.query" class="input" style="width:250px"
                 :placeholder="$t('admin_search_user_or_mail')" @input="debouncedReload" />
        </div>
        <select v-model="filters.role" class="select" style="width:130px" @change="reset">
          <option :value="null">{{ $t('admin_all_roles') }}</option>
          <option value="USER">USER</option>
          <option value="ADMIN">ADMIN</option>
        </select>
        <select v-model="filters.status" class="select" style="width:150px" @change="reset">
          <option :value="null">{{ $t('admin_all_statuses') }}</option>
          <option value="ACTIVE">{{ $t('admin_status_active') }}</option>
          <option value="UNVERIFIED">{{ $t('admin_status_unverified') }}</option>
          <option value="SUSPENDED">{{ $t('admin_status_suspended') }}</option>
          <option value="BANNED">{{ $t('admin_status_banned') }}</option>
        </select>
        <span class="spacer"></span>
        <button class="btn icon" :title="$t('admin_refresh')" @click="reload"><ui-icon name="refresh" /></button>
      </div>

      <div class="progress" v-if="loading"><i></i></div>

      <div class="table-wrap">
        <table class="grid">
          <thead>
            <tr>
              <th>{{ $t('user') }}</th>
              <th>{{ $t('mail') }}</th>
              <th>{{ $t('status') }}</th>
              <th>{{ $t('role') }}</th>
              <th class="right">{{ $t('recipes') }}</th>
              <th class="right">{{ $t('admin_reports') }}</th>
              <th>{{ $t('admin_joined') }}</th>
              <th>{{ $t('admin_last_seen') }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in rows" :key="u.id">
              <td>
                <div class="who">
                  <span class="avatar" :style="{background: tint(u.username)}">{{ initials(u.username) }}</span>
                  <span class="truncate">{{ u.username }}</span>
                </div>
              </td>
              <td class="muted small">{{ u.mail }}</td>
              <td>
                <span class="badge" :class="statusTone(u.status)" :title="u.moderationNote || ''">
                  <i class="dot"></i>{{ $t(`admin_status_${u.status.toLowerCase()}`) }}
                </span>
                <div v-if="u.suspendedUntil" class="small subtle nowrap">
                  {{ $t('admin_until') }} {{ fmtDate(u.suspendedUntil) }}
                </div>
              </td>
              <td>
                <select class="select sm-select" :value="u.role"
                        :disabled="busyId === u.id"
                        @change="changeRole(u, ($event.target as HTMLSelectElement).value)">
                  <option value="USER">USER</option>
                  <option value="ADMIN">ADMIN</option>
                </select>
              </td>
              <td class="right tnum">{{ u.recipesCount }}</td>
              <td class="right tnum">
                <span v-if="u.reportsAgainstCount" class="badge warn">{{ u.reportsAgainstCount }}</span>
                <span v-else class="subtle">0</span>
              </td>
              <td class="small muted nowrap">{{ fmtDate(u.joinDate) }}</td>
              <td class="small muted nowrap">{{ fmtAgo(u.lastActivityDate) }}</td>
              <td class="actions">
                <span class="row-actions">
                  <button v-if="!u.isVerified" class="btn icon" :title="$t('admin_force_verify')"
                          @click="run(u, () => verifyUser(u.id))"><ui-icon name="mailCheck" /></button>
                  <button v-if="u.status === 'SUSPENDED' || u.status === 'BANNED'" class="btn icon"
                          :title="$t('admin_reinstate')"
                          @click="run(u, () => reinstateUser(u.id))"><ui-icon name="userCheck" /></button>
                  <template v-else>
                    <button class="btn icon" :title="$t('admin_suspend')" :disabled="u.role === 'ADMIN'"
                            @click="open(u, 'suspend')"><ui-icon name="clock" /></button>
                    <button class="btn icon" :title="$t('admin_ban')" :disabled="u.role === 'ADMIN'"
                            @click="open(u, 'ban')"><ui-icon name="ban" /></button>
                  </template>
                  <button class="btn icon danger-hover" :title="$t('delete')" :disabled="u.role === 'ADMIN'"
                          @click="open(u, 'delete')"><ui-icon name="trash" /></button>
                </span>
              </td>
            </tr>
          </tbody>
        </table>
        <ui-empty v-if="!loading && !rows.length" icon="users"
                  :title="$t('admin_no_result')" :hint="$t('admin_no_result_hint')" />
      </div>

      <div class="panel-foot">
        <ui-pager :page="page" :size="size" :total="total" @update:page="p => { page = p; reload() }" />
      </div>
    </div>

    <!-- suspend -->
    <ui-modal v-model="dialog.suspend" :title="`${$t('admin_suspend')} — ${selected?.username ?? ''}`">
      <div class="col" style="gap:12px">
        <label class="field">
          <span>{{ $t('admin_suspension_days') }}</span>
          <input v-model.number="days" class="input" type="number" min="1" max="3650" />
        </label>
        <label class="field">
          <span>{{ $t('admin_reason') }}</span>
          <textarea v-model="reason" class="input" rows="3"></textarea>
        </label>
        <p class="small muted">{{ $t('admin_suspend_hint') }}</p>
      </div>
      <template #actions>
        <button class="btn" @click="dialog.suspend = false">{{ $t('cancel') }}</button>
        <button class="btn primary" @click="confirmSuspend">{{ $t('admin_suspend') }}</button>
      </template>
    </ui-modal>

    <!-- ban -->
    <ui-modal v-model="dialog.ban" :title="`${$t('admin_ban')} — ${selected?.username ?? ''}`">
      <div class="col" style="gap:12px">
        <label class="field">
          <span>{{ $t('admin_reason') }}</span>
          <textarea v-model="reason" class="input" rows="3"></textarea>
        </label>
        <div class="alert warn"><ui-icon name="alert" /><span>{{ $t('admin_ban_hint') }}</span></div>
      </div>
      <template #actions>
        <button class="btn" @click="dialog.ban = false">{{ $t('cancel') }}</button>
        <button class="btn danger" @click="confirmBan">{{ $t('admin_ban') }}</button>
      </template>
    </ui-modal>

    <!-- delete -->
    <ui-modal v-model="dialog.delete" :title="$t('admin_delete_account')">
      <div class="col" style="gap:12px">
        <p>{{ $t('admin_delete_account_confirm', {username: selected?.username}) }}</p>
        <div class="alert danger"><ui-icon name="alert" /><span>{{ $t('admin_irreversible') }}</span></div>
      </div>
      <template #actions>
        <button class="btn" @click="dialog.delete = false">{{ $t('cancel') }}</button>
        <button class="btn danger" @click="confirmDelete">{{ $t('delete') }}</button>
      </template>
    </ui-modal>
  </div>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {
  banUser, deleteUser, errorKey, listUsers, reinstateUser, setRole, suspendUser, verifyUser,
} from '../lib/api'
import {fmtAgo, fmtDate} from '../lib/format'
import {notifyError, notifyOk} from '../lib/toast'
import UiIcon from '../components/UiIcon.vue'
import UiModal from '../components/UiModal.vue'
import UiPager from '../components/UiPager.vue'
import UiEmpty from '../components/UiEmpty.vue'

const {t, te} = useI18n()

const rows = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(25)
const total = ref(0)
const busyId = ref<number | null>(null)
const filters = reactive<{query: string; role: string | null; status: string | null}>({
  query: '', role: null, status: null,
})

const selected = ref<any>(null)
const dialog = reactive({suspend: false, ban: false, delete: false})
const days = ref(7)
const reason = ref('')

const statusTone = (s: string) =>
  s === 'BANNED' ? 'danger' : s === 'SUSPENDED' ? 'warn' : s === 'UNVERIFIED' ? 'info' : 'ok'

const initials = (name: string) => (name || '?').slice(0, 2).toUpperCase()
/** Stable per-name avatar tint, so rows stay visually distinguishable while scanning. */
const tint = (name: string) => {
  let h = 0
  for (const ch of name || '') h = (h * 31 + ch.charCodeAt(0)) % 360
  return `hsl(${h} 42% 88%)`
}

function fail(e: any) {
  const key = errorKey(e)
  notifyError(key && te(key) ? t(key) : t('admin_action_failed'))
}

async function reload() {
  loading.value = true
  try {
    const {data} = await listUsers(filters, page.value - 1, size.value)
    rows.value = data.items
    total.value = data.count
  } catch (e) { fail(e) } finally { loading.value = false }
}

let timer: any
const debouncedReload = () => { clearTimeout(timer); timer = setTimeout(reset, 350) }
const reset = () => { page.value = 1; reload() }

async function run(user: any, action: () => Promise<any>) {
  busyId.value = user.id
  try {
    Object.assign(user, (await action()).data)
    notifyOk(t('admin_action_done'))
  } catch (e) { fail(e) } finally { busyId.value = null }
}

async function changeRole(user: any, role: string) {
  busyId.value = user.id
  try {
    Object.assign(user, (await setRole(user.id, role)).data)
    notifyOk(t('admin_action_done'))
  } catch (e) {
    fail(e)
    await reload()   // put the select back to what the server actually has
  } finally { busyId.value = null }
}

function open(user: any, which: 'suspend' | 'ban' | 'delete') {
  selected.value = user
  reason.value = ''
  days.value = 7
  dialog[which] = true
}

const confirmSuspend = () => {
  const u = selected.value; dialog.suspend = false
  run(u, () => suspendUser(u.id, days.value, reason.value))
}
const confirmBan = () => {
  const u = selected.value; dialog.ban = false
  run(u, () => banUser(u.id, reason.value))
}
const confirmDelete = async () => {
  const u = selected.value; dialog.delete = false
  try {
    await deleteUser(u.id)
    notifyOk(t('admin_action_done'))
    await reload()
  } catch (e) { fail(e) }
}

onMounted(reload)
</script>

<style scoped>
.page { max-width: 1400px; }
.filters { gap: 8px; flex-wrap: wrap; }
.who { display: flex; align-items: center; gap: 8px; min-width: 0; }
.avatar {
  width: 24px; height: 24px; flex: none;
  border-radius: 50%;
  display: grid; place-items: center;
  font-size: 10px; font-weight: 600;
  color: var(--c-text-muted);
}
.sm-select { height: 27px; font-size: 12.5px; width: 92px; }
</style>
